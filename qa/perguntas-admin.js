#!/usr/bin/env node
/**
 * Manutenção do banco de perguntas em `/categorias/{categoria}/perguntas`.
 *
 * Canalização para o agente diário: acrescenta perguntas novas e **arquiva** (nunca apaga) as
 * problemáticas em `/perguntas_arquivadas/{categoria}/{id}`. As rules fecham os dois nós a
 * `.write: false` — a escrita faz-se com credencial de administrador, que as ignora por completo.
 *
 * COMO CORRER
 *   1. Consola do Firebase → Definições do projeto → Contas de serviço → Gerar nova chave
 *      privada. Guarda o JSON FORA do repositório (é uma credencial de ADMIN).
 *   2. export GOOGLE_APPLICATION_CREDENTIALS=/caminho/para/chave.json
 *   3. npm install firebase-admin
 *   4. node qa/perguntas-admin.js listar "Geografia"
 *      node qa/perguntas-admin.js listar "Geografia" --json > geografia.json
 *      node qa/perguntas-admin.js adicionar "Geografia" novas.json --dry-run
 *      node qa/perguntas-admin.js arquivar "Geografia" 42 --motivo "facto errado" --dry-run
 *      node qa/perguntas-admin.js snapshot
 *      node qa/perguntas-admin.js aplicar-pendente qa/pendentes/2026-08-21.json --dry-run
 *
 *   Tira o `--dry-run` para escrever mesmo. `listar` nunca escreve, com ou sem flags.
 *
 * O FLUXO "A NUVEM PROPÕE, O LOCAL APLICA"
 *
 *   A rotina diária corre na nuvem e **não alcança a RTDB** — não tem credencial de admin, e as
 *   rules fecham `/categorias` a quem não esteja autenticado. Por isso:
 *
 *     1. a nuvem lê `qa/snapshot-perguntas.json` (versionado) para saber o que já existe;
 *     2. propõe num ficheiro em `qa/pendentes/`, que faz commit;
 *     3. quem tem a chave corre `aplicar-pendente` aqui, primeiro com `--dry-run`;
 *     4. o `aplicar-pendente` regenera o snapshot no fim, para o dia seguinte partir a par.
 *
 *   O snapshot é a única fonte de verdade que a nuvem tem. Se ficar velho, as propostas passam a
 *   bater ao lado — daí a regeneração automática ser parte do passo que escreve.
 *
 * DUAS COISAS QUE NÃO SÃO ÓBVIAS E QUE ESTE SCRIPT RESPEITA
 *
 *   1. `respostaCorreta` tem de bater **exactamente** com uma das `opcoes`. O jogo compara com
 *      `option == question.respostaCorreta` (GameViewModel.kt, MultiMatchViewModel.kt) — um
 *      espaço a mais ou um acento trocado torna a pergunta impossível de acertar, e nada na
 *      base de dados o denuncia. Por isso a validação aqui é por igualdade exacta, não
 *      normalizada; a normalização serve só para caçar duplicados.
 *
 *   2. As perguntas estão guardadas como **array** (chaves "0", "1", "2"…), não com push-keys.
 *      `adicionar` usa `push()`, o que mistura chaves novas (`-O5x…`) com as numéricas e faz o
 *      nó deixar de ser um array na vista JSON. O cliente aguenta: `QuestionRepository` lê por
 *      `snapshot.children`, que itera as duas formas. Arquivar também deixa buracos na
 *      numeração, pela mesma razão inofensiva.
 */
'use strict';

const fs = require('fs');
const path = require('path');

/**
 * As categorias que o jogo mostra no picker. As duas escondidas (`Património Português`,
 * `Gastronomia Portuguesa`) ficam de fora do snapshot de propósito: ninguém lhes acrescenta
 * perguntas, e metê-las lá só engordava o ficheiro versionado.
 */
const CATEGORIAS_VISIVEIS = ['Cultura Geral', 'Desporto', 'Gentílicos', 'Geografia', 'História'];

/**
 * Cópia do banco versionada no repositório.
 *
 * A rotina na nuvem propõe perguntas mas **não alcança a RTDB** — não tem credencial de admin, e
 * as rules fecham `/categorias` a quem não esteja autenticado. Sem este ficheiro não teria contra
 * o que deduplicar e proporia repetidos todos os dias. Quem aplica localmente regenera-o no fim.
 */
const SNAPSHOT = path.join(__dirname, 'snapshot-perguntas.json');

/** Sem isto o firebase-admin infere `https://<projectId>.firebaseio.com`, que aqui está errado. */
const DB_URL =
  process.env.FIREBASE_DATABASE_URL || 'https://supermercado-tia-lucia-default-rtdb.firebaseio.com';

const DIFICULDADES = ['facil', 'medio', 'dificil'];

/** Campos que uma pergunta pode ter. Ver `Question.kt`. */
const CAMPOS = ['pergunta', 'opcoes', 'respostaCorreta', 'dificuldade'];

/** Caracteres que a RTDB proíbe numa chave — uma categoria com estes partia o caminho. */
const CHAVE_INVALIDA = /[.#$[\]/]/;

// ---------------------------------------------------------------------------
// Puros — sem rede, sem credencial. Exportados no fim para poderem ser testados.
// ---------------------------------------------------------------------------

/**
 * Texto comparável: minúsculas, sem acentos, sem pontuação, espaços colapsados.
 *
 * NFD parte "á" em "a" + acento combinatório, e o intervalo ̀-ͯ apaga o acento.
 * Serve só para **comparar** (duplicados); o texto guardado é sempre o original.
 */
function normalizar(texto) {
  return String(texto == null ? '' : texto)
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9\s]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

/**
 * Valida uma pergunta à entrada. Devolve a lista de erros — vazia quer dizer aprovada.
 *
 * [jaNoBanco] é um Set de enunciados já normalizados (banco existente + as que já passaram
 * neste mesmo ficheiro), para apanhar duplicados nos dois sentidos.
 */
function validarEstrutura(p, jaNoBanco) {
  const erros = [];

  if (p === null || typeof p !== 'object' || Array.isArray(p)) {
    return ['não é um objeto JSON'];
  }

  const desconhecidos = Object.keys(p).filter((k) => !CAMPOS.includes(k));
  if (desconhecidos.length) {
    erros.push(`campos que não pertencem ao schema: ${desconhecidos.join(', ')}`);
  }

  if (typeof p.pergunta !== 'string' || !p.pergunta.trim()) {
    erros.push('`pergunta` tem de ser texto não vazio');
  }

  if (!Array.isArray(p.opcoes) || p.opcoes.length < 2) {
    // `QuestionRepository` rejeita `opcoes.size < 2`; com menos de duas não há escolha nenhuma.
    erros.push('`opcoes` tem de ser uma lista com 2 ou mais entradas');
  } else if (p.opcoes.some((o) => typeof o !== 'string' || !o.trim())) {
    erros.push('todas as `opcoes` têm de ser texto não vazio');
  } else {
    const vistas = new Map();
    for (const o of p.opcoes) {
      const chave = normalizar(o);
      if (vistas.has(chave)) {
        erros.push(`opções repetidas: ${JSON.stringify(vistas.get(chave))} e ${JSON.stringify(o)}`);
      } else {
        vistas.set(chave, o);
      }
    }
  }

  if (typeof p.respostaCorreta !== 'string' || !p.respostaCorreta.trim()) {
    erros.push('`respostaCorreta` tem de ser texto não vazio');
  } else if (Array.isArray(p.opcoes) && !p.opcoes.includes(p.respostaCorreta)) {
    // Igualdade EXACTA de propósito — ver o cabeçalho.
    const quase = Array.isArray(p.opcoes)
      ? p.opcoes.find((o) => normalizar(o) === normalizar(p.respostaCorreta))
      : undefined;
    erros.push(
      quase === undefined
        ? '`respostaCorreta` não está entre as `opcoes`'
        : `\`respostaCorreta\` só bate com ${JSON.stringify(quase)} depois de normalizar — ` +
          'o jogo compara texto exacto, esta pergunta seria impossível de acertar'
    );
  }

  if (!DIFICULDADES.includes(p.dificuldade)) {
    erros.push(`\`dificuldade\` tem de ser ${DIFICULDADES.join(' | ')}`);
  }

  if (typeof p.pergunta === 'string' && jaNoBanco && jaNoBanco.has(normalizar(p.pergunta))) {
    erros.push('já existe uma pergunta com este enunciado (comparado sem acentos nem pontuação)');
  }

  return erros;
}

// ---------------------------------------------------------------------------
// Acesso à base de dados
// ---------------------------------------------------------------------------

/**
 * `firebase-admin` é carregado só aqui, e não no topo, para os `require` deste ficheiro (testes,
 * `--help`) não dependerem de uma credencial nem do pacote estar instalado.
 */
function database() {
  let App;
  let Database;
  try {
    // API modular (`firebase-admin/app`, `firebase-admin/database`). A antiga, com namespace
    // — `admin.apps`, `admin.credential`, `admin.database()` — foi **removida** no
    // firebase-admin v13/v14 e rebenta com `Cannot read properties of undefined`.
    App = require('firebase-admin/app');
    Database = require('firebase-admin/database');
  } catch (e) {
    abortar('`firebase-admin` não está instalado. Corre: npm install firebase-admin');
  }
  if (!process.env.GOOGLE_APPLICATION_CREDENTIALS) {
    abortar(
      'GOOGLE_APPLICATION_CREDENTIALS não está definida — sem chave de conta de serviço não há\n' +
        '  escrita de admin. Ver as instruções no cabeçalho deste ficheiro.'
    );
  }
  const app = App.getApps().length
    ? App.getApps()[0]
    : App.initializeApp({ credential: App.applicationDefault(), databaseURL: DB_URL });
  return Database.getDatabase(app);
}

/** Lê o banco de uma categoria como lista de `{ id, pergunta }`. */
async function lerBanco(db, categoria) {
  const snap = await db.ref(`categorias/${categoria}/perguntas`).once('value');
  const valor = snap.val();
  if (!valor) return [];
  return Object.entries(valor).map(([id, p]) => ({ id, ...p }));
}

/** Só os campos do schema — o `id` é detalhe de armazenamento e não entra no snapshot. */
function semId(p) {
  return {
    pergunta: p.pergunta,
    opcoes: p.opcoes,
    respostaCorreta: p.respostaCorreta,
    dificuldade: p.dificuldade,
  };
}

// ---------------------------------------------------------------------------
// Preparar / escrever — separados de propósito
//
// Preparar lê e valida sem tocar em nada; escrever aplica o plano já validado. É o que permite
// ao `aplicar-pendente` verificar o lote inteiro **antes** de escrever a primeira coisa, em vez
// de ir aplicando e descobrir a meio que uma entrada estava má.
// ---------------------------------------------------------------------------

/** Valida um lote contra o banco da categoria e devolve o plano de escrita. Não escreve nada. */
async function prepararAdicao(db, categoria, perguntas) {
  const banco = await lerBanco(db, categoria);
  // O Set cresce à medida que as perguntas são aceites, por isso apanha duplicados dentro do
  // próprio lote e não só contra o que já está na base de dados.
  const vistos = new Set(banco.map((p) => normalizar(p.pergunta)));

  const aceites = [];
  const recusadas = [];
  perguntas.forEach((p, i) => {
    const erros = validarEstrutura(p, vistos);
    if (erros.length) {
      recusadas.push({ i, p, erros });
      return;
    }
    vistos.add(normalizar(p.pergunta));
    aceites.push(p);
  });

  // `push()` sem valor NÃO escreve — só gera a chave. Com as chaves todas na mão, o lote vai
  // numa única `update()`, para não haver escrita parcial se a ligação cair a meio.
  const raiz = db.ref(`categorias/${categoria}/perguntas`);
  const lote = {};
  for (const p of aceites) lote[raiz.push().key] = semId(p);

  return { categoria, noBanco: banco.length, recebidas: perguntas.length, aceites, recusadas, lote, raiz };
}

async function escreverAdicao(plano) {
  if (!plano.aceites.length) return 0;
  await plano.raiz.update(plano.lote);
  return plano.aceites.length;
}

function relatarAdicao(plano) {
  console.log(`"${plano.categoria}" · no banco ${plano.noBanco} · recebidas ${plano.recebidas}`);
  console.log(`aceites ${plano.aceites.length} · recusadas ${plano.recusadas.length}`);
  for (const r of plano.recusadas) {
    const titulo = typeof r.p?.pergunta === 'string' ? r.p.pergunta : '(sem enunciado)';
    console.log(`  RECUSADA #${r.i}: ${JSON.stringify(titulo)}`);
    r.erros.forEach((e) => console.log(`      - ${e}`));
  }
}

/** Confirma que a pergunta existe e que o arquivo não a sobrescreve. Devolve `{ erro }` se não. */
async function prepararArquivo(db, categoria, id, motivo) {
  const origem = `categorias/${categoria}/perguntas/${id}`;
  const destino = `perguntas_arquivadas/${categoria}/${id}`;

  const snap = await db.ref(origem).once('value');
  const pergunta = snap.val();
  if (!pergunta) return { erro: `${origem} não existe — nada para arquivar.` };

  // Os ids são índices de array e podem repetir-se entre limpezas; sobrescrever o arquivo
  // apagava em silêncio uma pergunta já arquivada, que é exactamente o que este nó evita.
  const jaLa = await db.ref(destino).once('value');
  if (jaLa.exists()) {
    return { erro: `${destino} já existe. Arquiva com outro id ou trata o conflito à mão.` };
  }

  const registo = { ...pergunta, arquivadaEm: new Date().toISOString() };
  if (motivo) registo.motivo = motivo;
  return { origem, destino, pergunta, motivo, registo };
}

async function escreverArquivo(db, plano) {
  // Escrita e apagamento no MESMO `update()` a partir da raiz: a RTDB aplica os dois caminhos
  // de uma vez, por isso não há instante em que a pergunta esteja nos dois sítios nem em nenhum.
  await db.ref().update({ [plano.destino]: plano.registo, [plano.origem]: null });
}

/** Relê as categorias visíveis e reescreve o [SNAPSHOT]. */
async function gerarSnapshot(db) {
  const saida = {};
  for (const categoria of CATEGORIAS_VISIVEIS) {
    saida[categoria] = (await lerBanco(db, categoria)).map(semId);
  }
  fs.writeFileSync(SNAPSHOT, `${JSON.stringify(saida, null, 2)}\n`);
  return saida;
}

// ---------------------------------------------------------------------------
// Subcomandos
// ---------------------------------------------------------------------------

async function listar(categoria, { json }) {
  const db = database();
  const banco = await lerBanco(db, categoria);

  // Com `--json` sai só o JSON, para poder ir directo para um ficheiro por redirecção.
  if (json) {
    console.log(JSON.stringify({ [categoria]: banco.map(semId) }, null, 2));
    return;
  }

  if (!banco.length) {
    console.log(`"${categoria}" não tem perguntas (ou não existe).`);
    return;
  }

  const porNormalizado = new Map();
  for (const p of banco) {
    const chave = normalizar(p.pergunta);
    if (!porNormalizado.has(chave)) porNormalizado.set(chave, []);
    porNormalizado.get(chave).push(p);
  }

  console.log(`"${categoria}" · ${banco.length} perguntas\n`);
  for (const p of banco) {
    console.log(`  ${String(p.id).padStart(5)} [${(p.dificuldade || '?').padEnd(7)}] ${normalizar(p.pergunta)}`);
  }

  const repetidas = [...porNormalizado.entries()].filter(([, lista]) => lista.length > 1);
  console.log(`\nenunciados distintos ${porNormalizado.size} · repetidos ${repetidas.length}`);
  for (const [chave, lista] of repetidas) {
    console.log(`\n  REPETIDO (${lista.length}x): ${chave}`);
    lista.forEach((p) => console.log(`    id ${p.id}: ${JSON.stringify(p.pergunta)}`));
  }
}

async function adicionar(categoria, ficheiro, { dryRun }) {
  let entrada;
  try {
    entrada = JSON.parse(fs.readFileSync(ficheiro, 'utf8'));
  } catch (e) {
    abortar(`não consegui ler ${ficheiro}: ${e.message}`);
  }
  if (!Array.isArray(entrada)) abortar(`${ficheiro} tem de conter um array de perguntas.`);
  if (!entrada.length) abortar(`${ficheiro} está vazio.`);

  const db = database();
  const plano = await prepararAdicao(db, categoria, entrada);
  relatarAdicao(plano);
  console.log('');

  if (!plano.aceites.length) {
    console.log('Nada a escrever.');
    return;
  }
  if (dryRun) {
    plano.aceites.forEach((p) => console.log(`  ACEITE: ${JSON.stringify(p.pergunta)}`));
    console.log('\n--dry-run: nada foi escrito.');
    return;
  }

  const n = await escreverAdicao(plano);
  console.log(`escritas ${n} perguntas em "${categoria}".`);
}

async function arquivar(categoria, id, { dryRun, motivo }) {
  const db = database();
  const plano = await prepararArquivo(db, categoria, id, motivo);
  if (plano.erro) abortar(plano.erro);

  console.log(`arquivar ${plano.origem} → ${plano.destino}`);
  console.log(`  ${JSON.stringify(plano.pergunta.pergunta)}`);
  console.log(`  motivo: ${motivo || '(não indicado)'}`);

  if (dryRun) {
    console.log('\n--dry-run: nada foi movido.');
    return;
  }

  await escreverArquivo(db, plano);
  console.log('arquivada.');
}

async function snapshot({ dryRun }) {
  const db = database();
  if (dryRun) {
    // Não escreve o ficheiro, mas lê à mesma para mostrar o que lá poria.
    const saida = {};
    for (const c of CATEGORIAS_VISIVEIS) saida[c] = (await lerBanco(db, c)).length;
    Object.entries(saida).forEach(([c, n]) => console.log(`  ${String(n).padStart(5)}  ${c}`));
    console.log('\n--dry-run: o snapshot não foi escrito.');
    return;
  }
  const saida = await gerarSnapshot(db);
  Object.entries(saida).forEach(([c, l]) => console.log(`  ${String(l.length).padStart(5)}  ${c}`));
  console.log(`\nescrito ${SNAPSHOT}`);
}

/**
 * Aplica um ficheiro de propostas da rotina na nuvem.
 *
 * Formato:
 * ```json
 * { "adicionar": { "Geografia": [ {pergunta, opcoes, respostaCorreta, dificuldade} ] },
 *   "arquivar":  [ { "categoria": "Geografia", "id": "42", "motivo": "facto errado" } ] }
 * ```
 *
 * **Prepara tudo antes de escrever o que quer que seja.** Um alvo de arquivo inválido (id que já
 * não existe, ou já arquivado) aborta o lote inteiro sem tocar na base de dados: quando os
 * pedidos não batem certo com a realidade, o snapshot contra o qual a nuvem decidiu está velho,
 * e aplicar metade era pior do que não aplicar nada. Perguntas recusadas são outra coisa — são
 * esperadas (duplicados) e só se ignoram, com o motivo à vista.
 */
async function aplicarPendente(ficheiro, { dryRun }) {
  let entrada;
  try {
    entrada = JSON.parse(fs.readFileSync(ficheiro, 'utf8'));
  } catch (e) {
    abortar(`não consegui ler ${ficheiro}: ${e.message}`);
  }
  if (entrada === null || typeof entrada !== 'object' || Array.isArray(entrada)) {
    abortar(`${ficheiro} tem de conter um objeto com "adicionar" e/ou "arquivar".`);
  }

  const pedidosAdicao = entrada.adicionar || {};
  const pedidosArquivo = entrada.arquivar || [];
  if (typeof pedidosAdicao !== 'object' || Array.isArray(pedidosAdicao)) {
    abortar('"adicionar" tem de ser um objeto {categoria: [perguntas]}.');
  }
  if (!Array.isArray(pedidosArquivo)) abortar('"arquivar" tem de ser um array.');
  if (!Object.keys(pedidosAdicao).length && !pedidosArquivo.length) {
    abortar(`${ficheiro} não pede nada.`);
  }

  const db = database();

  const planosAdicao = [];
  for (const [categoria, perguntas] of Object.entries(pedidosAdicao)) {
    if (CHAVE_INVALIDA.test(categoria)) abortar(`categoria inválida: ${categoria}`);
    if (!Array.isArray(perguntas)) abortar(`"adicionar"."${categoria}" tem de ser um array.`);
    planosAdicao.push(await prepararAdicao(db, categoria, perguntas));
  }

  const planosArquivo = [];
  for (const [i, a] of pedidosArquivo.entries()) {
    if (a === null || typeof a !== 'object' || !a.categoria || a.id === undefined) {
      abortar(`"arquivar"[${i}] precisa de {categoria, id}.`);
    }
    if (CHAVE_INVALIDA.test(String(a.categoria))) abortar(`categoria inválida: ${a.categoria}`);
    planosArquivo.push({ pedido: a, ...(await prepararArquivo(db, a.categoria, a.id, a.motivo)) });
  }

  planosAdicao.forEach((p) => {
    relatarAdicao(p);
    console.log('');
  });
  for (const p of planosArquivo) {
    const alvo = `${p.pedido.categoria}/${p.pedido.id}`;
    if (p.erro) console.log(`  ARQUIVAR ${alvo}: PROBLEMA — ${p.erro}`);
    else console.log(`  ARQUIVAR ${alvo}: ${JSON.stringify(p.pergunta.pergunta)}`);
  }

  const maus = planosArquivo.filter((p) => p.erro);
  const totalAceites = planosAdicao.reduce((s, p) => s + p.aceites.length, 0);
  console.log(`\na acrescentar ${totalAceites} · a arquivar ${planosArquivo.length - maus.length} · problemas ${maus.length}`);

  if (maus.length) {
    abortar(
      `${maus.length} pedido(s) de arquivo não batem certo com a base de dados. Nada foi escrito —\n` +
        '  o snapshot contra o qual as propostas foram feitas está provavelmente desactualizado.'
    );
  }
  if (dryRun) {
    console.log('\n--dry-run: nada foi escrito.');
    return;
  }
  if (!totalAceites && !planosArquivo.length) {
    console.log('Nada a escrever.');
    return;
  }

  for (const p of planosAdicao) {
    const n = await escreverAdicao(p);
    if (n) console.log(`escritas ${n} perguntas em "${p.categoria}".`);
  }
  for (const p of planosArquivo) {
    await escreverArquivo(db, p);
    console.log(`arquivada ${p.origem}`);
  }

  // O snapshot tem de ficar a par para o dia seguinte partir do banco já com estas mudanças.
  await gerarSnapshot(db);
  console.log(`\nsnapshot actualizado: ${SNAPSHOT}`);
}

// ---------------------------------------------------------------------------
// Arranque
// ---------------------------------------------------------------------------

function abortar(mensagem) {
  console.error(`ABORTADO: ${mensagem}`);
  process.exit(1);
}

const USO = `uso:
  node qa/perguntas-admin.js listar <categoria> [--json]
  node qa/perguntas-admin.js adicionar <categoria> <ficheiro.json> [--dry-run]
  node qa/perguntas-admin.js arquivar <categoria> <id> [--motivo "..."] [--dry-run]
  node qa/perguntas-admin.js snapshot [--dry-run]
  node qa/perguntas-admin.js aplicar-pendente <ficheiro.json> [--dry-run]`;

/** Subcomandos cujo 2.º argumento é uma categoria. */
const COM_CATEGORIA = ['listar', 'adicionar', 'arquivar'];

async function principal(argv) {
  const dryRun = argv.includes('--dry-run');
  const json = argv.includes('--json');
  const iMotivo = argv.indexOf('--motivo');
  const motivo = iMotivo !== -1 ? argv[iMotivo + 1] : undefined;
  const posicionais = argv.filter(
    (a, i) => !a.startsWith('--') && !(iMotivo !== -1 && i === iMotivo + 1)
  );
  const [comando, segundo, terceiro] = posicionais;

  if (!comando) abortar(USO);
  if (COM_CATEGORIA.includes(comando)) {
    if (!segundo) abortar(USO);
    if (CHAVE_INVALIDA.test(segundo)) {
      abortar(`categoria com caracteres que a RTDB não aceita numa chave: ${segundo}`);
    }
  }

  switch (comando) {
    case 'listar':
      return listar(segundo, { json });
    case 'adicionar':
      if (!terceiro) abortar(USO);
      return adicionar(segundo, terceiro, { dryRun });
    case 'arquivar':
      if (!terceiro) abortar(USO);
      return arquivar(segundo, terceiro, { dryRun, motivo });
    case 'snapshot':
      return snapshot({ dryRun });
    case 'aplicar-pendente':
      if (!segundo) abortar(USO);
      return aplicarPendente(segundo, { dryRun });
    default:
      return abortar(`comando desconhecido: ${comando}\n${USO}`);
  }
}

// Só corre quando é invocado directamente — `require()` a partir de um teste não dispara nada.
if (require.main === module) {
  principal(process.argv.slice(2))
    .then(() => process.exit(0))
    .catch((e) => {
      console.error(e);
      process.exit(1);
    });
}

module.exports = { normalizar, validarEstrutura, DIFICULDADES };
