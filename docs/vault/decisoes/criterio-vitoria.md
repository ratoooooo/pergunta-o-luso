# Critério de vitória por modo

← [índice](../00-indice.md)

"Vencer" não é intrínseco a um quiz solo — teve de ser decidido.

| Modo | Vitória | Porquê |
|---|---|---|
| Clássico | ≥ 70 % certas (≥ 7 de 10) | ambos correm sempre 10 perguntas, por isso "terminar a ronda" é trivial e não distinguiria mérito nenhum; 70 % de precisão distingue |
| Caótico | ≥ 70 % certas | idem |
| Eliminatórias | sobreviver às 20 | já tem uma condição natural e significativa de vitória/derrota, usada diretamente |

`GameViewModel.didWin` implementa exactamente isto. A vitória incrementa `vitorias` (global e por
modo) no agregado, e liberta o bónus de XP.

## Multijogador

Por formato, consistente com o critério que cada pódio já usava:

| Formato | Vitória |
|---|---|
| 1x1 | pontuação **estritamente** mais alta (empate não é vitória) |
| Grupo | pontuação **estritamente** mais alta |
| 2x2 | total da **equipa** estritamente maior (empate não é vitória) |
| 2x2 walkover | a equipa presente ganha |

Contabilizado em `multiVitorias/{formato}` — é o que alimenta as conquistas de multijogador e o
[ranking por formato](eixos-ranking.md).

Ver também: [modos-de-jogo](../funcionalidades/modos-de-jogo.md) ·
[xp-niveis-patentes](../funcionalidades/xp-niveis-patentes.md)
