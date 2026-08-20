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
 *   4. node qa/perguntas-admin.js listar "Geografia" --dry-run
 *      node qa/perguntas-admin.js adicionar "Geografia" novas.json --dry-run
 *      node qa/perguntas-admin.js arquivar "Geografia" 42 --motivo "facto errado" --dry-run
 *
 *   Tira o `--dry-run` para escrever mesmo. `listar` nunca escreve, com ou sem a flag.
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

// ---------------------------------------------------------------------------
// Subcomandos
// ---------------------------------------------------------------------------

async function listar(categoria) {
  const db = database();
  const banco = await lerBanco(db, categoria);
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
  const banco = await lerBanco(db, categoria);
  // O Set cresce à medida que as perguntas são aceites, por isso apanha duplicados dentro do
  // próprio ficheiro e não só contra o que já está na base de dados.
  const vistos = new Set(banco.map((p) => normalizar(p.pergunta)));

  const aceites = [];
  const recusadas = [];
  entrada.forEach((p, i) => {
    const erros = validarEstrutura(p, vistos);
    if (erros.length) {
      recusadas.push({ i, p, erros });
      return;
    }
    vistos.add(normalizar(p.pergunta));
    aceites.push(p);
  });

  console.log(`"${categoria}" · no banco ${banco.length} · no ficheiro ${entrada.length}`);
  console.log(`aceites ${aceites.length} · recusadas ${recusadas.length}\n`);

  for (const r of recusadas) {
    const titulo = typeof r.p?.pergunta === 'string' ? r.p.pergunta : '(sem enunciado)';
    console.log(`  RECUSADA #${r.i}: ${JSON.stringify(titulo)}`);
    r.erros.forEach((e) => console.log(`      - ${e}`));
  }
  if (recusadas.length) console.log('');

  if (!aceites.length) {
    console.log('Nada a escrever.');
    return;
  }
  if (dryRun) {
    aceites.forEach((p) => console.log(`  ACEITE: ${JSON.stringify(p.pergunta)}`));
    console.log('\n--dry-run: nada foi escrito.');
    return;
  }

  // `push()` sem valor NÃO escreve — só gera a chave. Com as chaves todas na mão, o lote vai
  // numa única `update()`, para não haver escrita parcial se a ligação cair a meio.
  const raiz = db.ref(`categorias/${categoria}/perguntas`);
  const lote = {};
  for (const p of aceites) {
    lote[raiz.push().key] = {
      pergunta: p.pergunta,
      opcoes: p.opcoes,
      respostaCorreta: p.respostaCorreta,
      dificuldade: p.dificuldade,
    };
  }
  await raiz.update(lote);
  console.log(`escritas ${aceites.length} perguntas em "${categoria}".`);
}

async function arquivar(categoria, id, { dryRun, motivo }) {
  const db = database();
  const origem = `categorias/${categoria}/perguntas/${id}`;
  const destino = `perguntas_arquivadas/${categoria}/${id}`;

  const snap = await db.ref(origem).once('value');
  const pergunta = snap.val();
  if (!pergunta) abortar(`${origem} não existe — nada para arquivar.`);

  // Os ids são índices de array e podem repetir-se entre limpezas; sobrescrever o arquivo
  // apagava em silêncio uma pergunta já arquivada, que é exactamente o que este nó evita.
  const jaLa = await db.ref(destino).once('value');
  if (jaLa.exists()) {
    abortar(`${destino} já existe. Arquiva com outro id ou trata o conflito à mão.`);
  }

  const registo = { ...pergunta, arquivadaEm: new Date().toISOString() };
  if (motivo) registo.motivo = motivo;

  console.log(`arquivar ${origem} → ${destino}`);
  console.log(`  ${JSON.stringify(pergunta.pergunta)}`);
  console.log(`  motivo: ${motivo || '(não indicado)'}`);

  if (dryRun) {
    console.log('\n--dry-run: nada foi movido.');
    return;
  }

  // Escrita e apagamento no MESMO `update()` a partir da raiz: a RTDB aplica os dois caminhos
  // de uma vez, por isso não há instante em que a pergunta esteja nos dois sítios nem em nenhum.
  await db.ref().update({ [destino]: registo, [origem]: null });
  console.log('arquivada.');
}

// ---------------------------------------------------------------------------
// Arranque
// ---------------------------------------------------------------------------

function abortar(mensagem) {
  console.error(`ABORTADO: ${mensagem}`);
  process.exit(1);
}

const USO = `uso:
  node qa/perguntas-admin.js listar <categoria> [--dry-run]
  node qa/perguntas-admin.js adicionar <categoria> <ficheiro.json> [--dry-run]
  node qa/perguntas-admin.js arquivar <categoria> <id> [--motivo "..."] [--dry-run]`;

async function principal(argv) {
  const dryRun = argv.includes('--dry-run');
  const iMotivo = argv.indexOf('--motivo');
  const motivo = iMotivo !== -1 ? argv[iMotivo + 1] : undefined;
  const posicionais = argv.filter(
    (a, i) => !a.startsWith('--') && !(iMotivo !== -1 && i === iMotivo + 1)
  );
  const [comando, categoria, terceiro] = posicionais;

  if (!comando || !categoria) abortar(USO);
  if (CHAVE_INVALIDA.test(categoria)) abortar(`categoria com caracteres que a RTDB não aceita numa chave: ${categoria}`);

  switch (comando) {
    case 'listar':
      return listar(categoria);
    case 'adicionar':
      if (!terceiro) abortar(USO);
      return adicionar(categoria, terceiro, { dryRun });
    case 'arquivar':
      if (!terceiro) abortar(USO);
      return arquivar(categoria, terceiro, { dryRun, motivo });
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
