# Ranking, histórico e perfil

← [índice](../00-indice.md)

Tudo isto lê de `/jogadores` (agregado), **nunca de `/scores`** — o log em bruto teria o mesmo
jogador várias vezes na mesma tabela. Excepção: o Histórico, que é por definição a lista de
partidas.

## Ranking — duas dimensões, três níveis

| Nível | Componente | Conteúdo |
|---|---|---|
| Dimensão | `UnderlineTabs` | Por modo · Por formato |
| Valor | `SegmentedTabs` (pastilha roxa) | Clássico/Caótico/Eliminatórias **ou** 1x1/2x2/Grupo |
| Tabela | `UnderlineTabs` | as três listas da dimensão |

A pastilha — o elemento mais pesado — fica **onde já estava**, no meio. A dimensão entra por cima
em sublinhado porque é uma troca rara de duas opções e não deve pesar mais do que a escolha
seguinte. Trocar de dimensão repõe os separadores de baixo no primeiro: "Eliminatórias" e "Grupo"
não são a mesma coisa e herdar o índice dava um quadro que o jogador não pediu.

**Por modo** (de `modos/{modo}`): Mais vitórias · Mais pontos · Melhor recorde.
**Por formato** (de `multiVitorias`/`multiJogos`): Mais vitórias · Mais jogos · % vitórias.

Porque é que por formato não há "Mais pontos": [eixos-ranking](../decisoes/eixos-ranking.md).

A tabela de percentagem exige **3 jogos** (`MIN_JOGOS_PARA_PERCENTAGEM`) — sem mínimo, quem
ganhou o único jogo que fez aparecia em 1.º com 100 %. O ecrã explica o corte numa linha por cima
da tabela, **também quando a tabela está cheia**: cinco nomes parecem uma lista completa e quem
não se encontra nela não teria como saber porquê.

Cada linha mostra posição, avatar, nome, pastilha `Nv 5 · Marinheiro` e o valor. O próprio
jogador aparece com **contorno roxo e "(tu)"** — sem isso era preciso ler todos os nomes para se
encontrar. O 1.º lugar é dourado, com um encaixe creme por trás do avatar (um avatar dourado
sobre linha dourada desaparecia). Top 5 por tabela.

## Histórico

As partidas do próprio jogador, mais recentes primeiro: categoria · modo, certas/total, data,
pontos. Lido de `/scores` filtrado por uid **no cliente** (não há índice por `uid`; o histórico é
pequeno).

Separadores por **formato**: Todos / Solo / 1x1 / 2x2 / Grupo, a partir do campo `formato` do
`ScoreEntry`.

## Perfil

Grelha de estatísticas globais + detalhe por modo, de `/jogadores/{uid}`. Edição de nome inline
(passa pelo mesmo `setNome` do registo, por isso `nomeBusca` acompanha).

"Por modo" usa **separadores**, não três cartões empilhados — as mesmas cinco métricas repetidas
três vezes inflacionavam a densidade sem acrescentar informação.

Terminar sessão fica no fim, pequeno e discreto. **Eliminar conta** fica ainda mais abaixo,
separado por uma linha, e é o único elemento do ecrã em Coral cheio — ver
[eliminacao-conta](eliminacao-conta.md).

## Limitação conhecida

`loadAllProfiles` e `loadMyScores` descarregam `/jogadores` e `/scores` **inteiros** para filtrar
no cliente. Não é problema de segurança, é de escala e custo: falta `.indexOn` em `pontos`/`uid`
e queries do lado do servidor. Está identificado desde a auditoria de pré-lançamento.

Ver também: [xp-niveis-patentes](xp-niveis-patentes.md) ·
[rtdb-schema](../arquitetura/rtdb-schema.md)
