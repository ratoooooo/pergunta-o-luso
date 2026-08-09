# Os três eixos do ranking

← [índice](../00-indice.md)

## O princípio

Cada dimensão do ranking mostra **três tabelas deliberadamente diferentes** — pico, volume e
consistência. Três listas que medissem quase o mesmo seriam redundantes e o ecrã ficava mais
comprido sem dizer mais.

## Por modo

| Tabela | Fonte | Eixo |
|---|---|---|
| Mais vitórias | `modos.{modo}.vitorias` | consistência — quantas vezes se passa a fasquia |
| Mais pontos | `modos.{modo}.pontos` | volume / dedicação |
| Melhor recorde | `modos.{modo}.recorde` | pico — melhor jogo de sempre |

## Por formato — e a limitação que não foi contornada

Por formato existem **duas** contagens e mais nenhuma: `multiVitorias` e `multiJogos`. O
`ProfileRepository` nunca escreveu pontos nem recorde por formato.

Ou seja: **"Mais pontos" e "Melhor recorde" não são deriváveis** aqui.

**Não foi inventado nenhum campo para os fabricar.** As três tabelas usam os mesmos três eixos,
obtidos com o que existe:

| Tabela | Fonte | Eixo |
|---|---|---|
| Mais vitórias | `multiVitorias[fmt]` | pico |
| Mais jogos | `multiJogos[fmt]` | volume |
| % vitórias | `multiVitorias / multiJogos` | consistência |

Acrescentar "Mais pontos" por formato é **uma linha** no `accumulate` (`bump` de
`multiPontos/{formato}`) — mas **só passaria a contar a partir daí**: as partidas já jogadas não
têm esse dado e a tabela nasceria a mentir sobre quem joga há mais tempo. Fica como decisão
futura, não como omissão.

## O mínimo de 3 jogos na percentagem

`MIN_JOGOS_PARA_PERCENTAGEM = 3`. Sem mínimo, quem ganhou o único jogo que fez aparecia em 1.º
com 100 %, à frente de quem ganhou 8 em 10. **Não é hipótese teórica** — nos dados de teste havia
dois perfis com 1 vitória em 1 jogo, e ambos ficaram (corretamente) fora.

O ecrã explica o corte numa linha por cima da tabela, **também quando a tabela está cheia**:
cinco nomes parecem uma lista completa, e quem não se encontra nela não teria como saber porquê.

## Sempre de `/jogadores`

Nunca de `/scores`. O agregado tem uma linha por jogador; o log em bruto teria o mesmo jogador
várias vezes na mesma tabela.

Ver também: [ranking-historico-perfil](../funcionalidades/ranking-historico-perfil.md) ·
[rtdb-schema](../arquitetura/rtdb-schema.md)
