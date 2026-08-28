/**
 * Porte de `game/multi/MatchFormat.kt`. Os dois números por formato são a decisão central:
 *
 *  - `jogadores`    = capacidade (lugares na sala). Enche e a partida arranca sozinha.
 *  - `minJogadores` = quantos são precisos para a partida PODER começar.
 *
 * Só o Grupo é flexível (4 de um máximo de 10) — ver docs/vault/decisoes/grupo-4-a-10.md.
 * Se esta tabela se desencontrar do Kotlin, o `FormatosTest` do lado Android falha primeiro.
 */
export const FORMATOS = {
  '1x1':   { id: '1x1',   nome: '1x1',   jogadores: 2,  minJogadores: 2, equipas: false },
  '2x2':   { id: '2x2',   nome: '2x2',   jogadores: 4,  minJogadores: 4, equipas: true },
  'grupo': { id: 'grupo', nome: 'Grupo', jogadores: 10, minJogadores: 4, equipas: false }
};

/** Igual ao `MatchFormat.fromId`: id desconhecido cai no Grupo, nunca lança. */
export function formatoDe(id) {
  return FORMATOS[id] ?? FORMATOS['grupo'];
}

/** Há um intervalo de lugares (só o Grupo) em vez de um número fixo. */
export function tamanhoFlexivel(formato) {
  return formato.minJogadores < formato.jogadores;
}
