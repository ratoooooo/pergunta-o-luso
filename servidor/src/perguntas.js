/**
 * Banco de perguntas do lado do servidor.
 *
 * Porte de `data/QuestionRepository.kt`. Duas diferenças que são o ponto do exercício:
 *
 *  1. `/categorias` é estático (`.write: false` nas rules) e são ~1630 perguntas — carrega-se
 *     **tudo para memória ao arrancar**, em vez de uma leitura por partida. Poupa a ida à RTDB
 *     no arranque de cada sala, que é onde os jogadores estão à espera.
 *  2. A `respostaCorreta` fica **só aqui**. O que sai para os clientes é `paraCliente()`, sem
 *     esse campo. Era isto que a arquitectura anterior não conseguia fazer: o cliente tinha de
 *     receber a resposta certa para corrigir, e por isso podia lê-la antes de responder.
 */

const VERDADEIRO = 'Verdadeiro';
const FALSO = 'Falso';

let banco = new Map();      // categoria -> Pergunta[]
let carregadoEm = null;

/** Baralha uma cópia (Fisher-Yates). Não mexe no argumento. */
function baralhar(lista) {
  const copia = [...lista];
  for (let i = copia.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [copia[i], copia[j]] = [copia[j], copia[i]];
  }
  return copia;
}

function ehVerdadeiroFalso(opcoes) {
  if (opcoes.length !== 2) return false;
  const normalizadas = new Set(opcoes.map((o) => o.trim().toLowerCase()));
  return normalizadas.has(VERDADEIRO.toLowerCase()) && normalizadas.has(FALSO.toLowerCase());
}

/**
 * Normaliza um nó em bruto da RTDB. Devolve `null` para o que não dá para jogar — mesma
 * tolerância do `DataSnapshot.toQuestion`, que ignora entradas incompletas em vez de rebentar.
 *
 * As opções são baralhadas para a resposta certa não ficar sempre na mesma casa; Verdadeiro/Falso
 * mantém a ordem canónica, porque baralhar "Verdadeiro"/"Falso" lê-se como um defeito.
 */
export function normalizarPergunta(bruta) {
  if (!bruta || typeof bruta.pergunta !== 'string' || typeof bruta.respostaCorreta !== 'string') return null;
  const opcoes = Array.isArray(bruta.opcoes)
    ? bruta.opcoes.filter((o) => typeof o === 'string')
    : Object.values(bruta.opcoes ?? {}).filter((o) => typeof o === 'string');
  if (opcoes.length < 2) return null;
  return {
    pergunta: bruta.pergunta,
    opcoes: ehVerdadeiroFalso(opcoes) ? [VERDADEIRO, FALSO] : baralhar(opcoes),
    respostaCorreta: bruta.respostaCorreta,
    dificuldade: typeof bruta.dificuldade === 'string' ? bruta.dificuldade : ''
  };
}

const PATAMARES = ['facil', 'medio', 'dificil'];

/** Mesmo `Difficulty.fromId` do cliente, mas só para arrumar em patamares. */
function patamarDe(bruto) {
  const d = (bruto ?? '').trim().toLowerCase();
  if (d === 'medio' || d === 'médio') return 'medio';
  if (d === 'dificil' || d === 'difícil') return 'dificil';
  return 'facil';
}

/**
 * Rampa de dificuldade — porte de `QuestionRepository.buildProgression`.
 *
 * Baralha dentro de cada patamar e concatena fácil → médio → difícil, por isso a ordem varia a
 * cada partida mas sobe sempre. Um patamar curto é compensado pelo que sobrou dos outros, sem
 * repetir: as perguntas usadas saem da poça.
 */
export function progressao(todas, quantas) {
  const pocas = Object.fromEntries(
    PATAMARES.map((p) => [p, baralhar(todas.filter((q) => patamarDe(q.dificuldade) === p))])
  );

  const alvoFacil = Math.ceil(quantas / 3);
  const alvoDificil = Math.floor(quantas / 3);
  const alvoMedio = quantas - alvoFacil - alvoDificil;

  const rampa = [
    ...pocas.facil.splice(0, alvoFacil),
    ...pocas.medio.splice(0, alvoMedio),
    ...pocas.dificil.splice(0, alvoDificil)
  ];

  if (rampa.length < quantas) {
    const sobras = [...pocas.facil, ...pocas.medio, ...pocas.dificil];
    rampa.push(...sobras.slice(0, quantas - rampa.length));
  }
  return rampa.slice(0, quantas);
}

/**
 * Carrega `/categorias` inteiro. [lerCaminho] é injectado para os testes correrem sem Firebase.
 * Só troca o banco em uso **depois** de a leitura correr bem — uma recarga falhada deixa o
 * servidor a jogar com o banco anterior em vez de o deixar vazio.
 */
export async function carregarBanco(lerCaminho) {
  const bruto = (await lerCaminho('categorias')) ?? {};
  const novo = new Map();
  for (const [categoria, dados] of Object.entries(bruto)) {
    const cruas = Object.values(dados?.perguntas ?? {});
    const validas = cruas.map(normalizarPergunta).filter(Boolean);
    if (validas.length > 0) novo.set(categoria, validas);
  }
  if (novo.size === 0) throw new Error('banco de perguntas vazio — leitura recusada');
  banco = novo;
  carregadoEm = Date.now();
  return { categorias: banco.size, perguntas: [...banco.values()].reduce((n, v) => n + v.length, 0) };
}

export function estadoDoBanco() {
  return {
    categorias: banco.size,
    perguntas: [...banco.values()].reduce((n, v) => n + v.length, 0),
    carregadoEm
  };
}

/** Perguntas de uma partida normal. Categoria desconhecida devolve lista vazia. */
export function perguntasParaJogo(categoria, quantas) {
  return progressao(banco.get(categoria) ?? [], quantas);
}

/**
 * Perguntas de um quiz da comunidade, lidas à peça (mudam, ao contrário de `/categorias`).
 *
 * O tecto de 10 é o `MAX_PERGUNTAS_SALA` do cliente: as rules de `/scores` travam
 * `correctCount <= 20` e `total <= 20`, e a sala privada sempre se limitou a 10. Cortar aqui
 * mantém o corte num sítio só, agora que é o servidor a escolher as perguntas — e não o
 * anfitrião, que até aqui as enviava e portanto as podia inventar.
 */
export const MAX_PERGUNTAS_SALA = 10;

export async function perguntasDeQuiz(lerCaminho, quizId) {
  const quiz = await lerCaminho(`categorias_comunitarias/${quizId}`);
  if (!quiz) return null;
  const cruas = Array.isArray(quiz.perguntas) ? quiz.perguntas : Object.values(quiz.perguntas ?? {});
  const validas = cruas.map(normalizarPergunta).filter(Boolean).slice(0, MAX_PERGUNTAS_SALA);
  if (validas.length === 0) return null;
  return { titulo: quiz.titulo ?? 'Comunidade', perguntas: validas };
}

/** O que sai para o cliente. Sem `respostaCorreta` — é a razão de existir desta função. */
export function paraCliente(pergunta) {
  return {
    pergunta: pergunta.pergunta,
    opcoes: pergunta.opcoes,
    dificuldade: pergunta.dificuldade
  };
}
