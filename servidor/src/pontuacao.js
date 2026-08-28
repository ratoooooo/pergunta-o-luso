/**
 * Porte de `game/Scoring.kt`, `game/ChaoticEvent.kt` e `game/Difficulty.kt`.
 *
 * **Esta é a única lógica genuinamente duplicada entre o servidor e a app**, e é inevitável:
 * quem corrige a resposta tem de saber pontuá-la. O desvio silencioso entre as duas versões
 * mudaria o jogo sem partir nada, por isso está preso por `testes/pontuacao.json` — o mesmo
 * ficheiro de vectores é lido pelo teste daqui e pelo `PontuacaoVectoresTest` do lado Android.
 * Mexer na fórmula sem actualizar os vectores faz falhar os dois.
 */

export const EVENTOS = {
  PERGUNTA_DUPLA: 'pergunta_dupla',
  VELOCIDADE_MAXIMA: 'velocidade_maxima',
  ROUBO: 'roubo',
  TUDO_OU_NADA: 'tudo_ou_nada'
};

/** Ordem do `ChaoticEvent.ORDER` — o evento é determinístico pelo índice da pergunta. */
const ORDEM_EVENTOS = [
  EVENTOS.PERGUNTA_DUPLA,
  EVENTOS.VELOCIDADE_MAXIMA,
  EVENTOS.ROUBO,
  EVENTOS.TUDO_OU_NADA
];

/** Só o modo Caótico tem eventos; no Clássico devolve null. */
export function eventoPara(modo, indice) {
  if (modo !== 'caotico') return null;
  return ORDEM_EVENTOS[indice % ORDEM_EVENTOS.length];
}

const MULTIPLICADORES = { facil: 1.0, medio: 1.5, dificil: 2.0 };

/** Igual ao `Difficulty.fromId`: aceita com e sem acento, e o desconhecido é `facil`. */
export function multiplicadorDificuldade(bruto) {
  const d = (bruto ?? '').trim().toLowerCase();
  if (d === 'medio' || d === 'médio') return MULTIPLICADORES.medio;
  if (d === 'dificil' || d === 'difícil') return MULTIPLICADORES.dificil;
  return MULTIPLICADORES.facil;
}

const PONTOS_POR_SEGUNDO = 10;

export function bonusSequencia(sequencia) {
  if (sequencia >= 4) return 100;
  if (sequencia === 3) return 75;
  if (sequencia === 2) return 50;
  return 0;
}

/** Duração da pergunta em ms. Velocidade Máxima corta a tempo a meio. */
export const DURACAO_BASE_MS = 15_000;
export function duracaoDaPergunta(evento) {
  return evento === EVENTOS.VELOCIDADE_MAXIMA ? DURACAO_BASE_MS / 2 : DURACAO_BASE_MS;
}

/**
 * Pontos de UMA resposta.
 * @param sequenciaDepois a sequência de certas **depois** desta resposta (1 na primeira certa).
 */
export function pontosDaResposta({ certa, segundosRestantes, dificuldade, evento, sequenciaDepois }) {
  if (!certa) return evento === EVENTOS.TUDO_OU_NADA ? -50 : 0;

  let base = Math.max(0, segundosRestantes) * PONTOS_POR_SEGUNDO;
  if (evento === EVENTOS.PERGUNTA_DUPLA) base *= 2;

  // `Math.trunc` porque o Kotlin faz `.toInt()`, que trunca. Hoje as duas contas dão o mesmo:
  // `base` é sempre múltiplo de 10, por isso ×1.5 e ×2.0 caem sempre em inteiro. Mantém-se a
  // truncatura na mesma — é o que o Kotlin faz, e um multiplicador novo (×1.25, por exemplo)
  // passaria a distinguir as duas sem avisar ninguém.
  let pontos = Math.trunc(base * multiplicadorDificuldade(dificuldade));
  pontos += bonusSequencia(sequenciaDepois);

  if (evento === EVENTOS.ROUBO) pontos += 50;
  if (evento === EVENTOS.TUDO_OU_NADA) pontos += 100;
  return pontos;
}

/** O total corrente nunca desce abaixo de zero (`Scoring.clampTotal`). */
export function limitarTotal(total) {
  return Math.max(0, total);
}

/**
 * Segundos restantes creditados, na mesma conta que a app fazia:
 * `ceil(msRestantes / 1000)`.
 */
export function segundosRestantes(msRestantes) {
  return Math.ceil(Math.max(0, msRestantes) / 1000);
}
