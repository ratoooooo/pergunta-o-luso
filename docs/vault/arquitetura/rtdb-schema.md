# Schema da Realtime Database

← [índice](../00-indice.md)

Caminhos de topo **realmente existentes** (confirmável em `database.rules.json`):

| Caminho | Para quê |
|---|---|
| `/categorias/{cat}/perguntas` | as 1090 perguntas oficiais, só leitura |
| `/scores/{pushId}` | registo em bruto de cada partida terminada |
| `/jogadores/{uid}` | perfil agregado — a fonte de tudo o que é ranking/perfil |
| `/amigos/{uid}` | lista + pedidos enviados/recebidos |
| `/convites/{uid}` | desafios diretos 1x1 |
| `/presenca/{uid}` | booleano "app aberta", com `onDisconnect` |
| `/lobbies/{formato}/{lobbyId}` | **matchmaking em uso** — salas de espera |
| `/multisalas/{salaId}` | a partida em si (perguntas, jogadores, pontuações) |
| `/salas_privadas/{codigo}` | tabela código de 4 dígitos → sala |
| `/categorias_comunitarias/{id}` | quizzes criados por jogadores |
| `/denuncias/{quizId}/{uid}` | denúncias de quizzes |
| `/matchmakingN/...` | **código morto** — fila antiga, sem chamadores |

## `/jogadores/{uid}` — o nó central

Uma linha por jogador, acumulada por transação a cada partida
(`ProfileRepository.updateAfterGame`). O Ranking, o Perfil e as Conquistas leem **daqui**, nunca
de `/scores` — o log em bruto teria o mesmo jogador várias vezes na mesma tabela.

```
nome, nomeBusca        String   // nomeBusca = nome trimmed+minúsculas, escrito na MESMA
                                // updateChildren que nome, por isso nunca divergem
atualizadoEm           Number
jogos, pontos          Number   // globais
respostasCertas/Totais Number   // taxaAcertos = certas / totais
vitorias, recorde      Number
maxStreak              Number   // melhor sequência de respostas certas (não de vitórias)
xpTotal                Number   // único campo de progressão persistido; nível é derivado
avatar                 String
partidasPerfeitas      Number
modos/{modo}           { jogos, pontos, respostasCertas, respostasTotais, vitorias, recorde }
multiVitorias/{fmt}    Number   // fmt = 1x1 | 2x2 | grupo
multiJogos/{fmt}       Number
categorias/{slug}      { jogos, vitorias }   // slug sem acentos: "História" → "historia"
```

**Não existe** `multiPontos` nem recorde por formato — é o que limita o
[ranking por formato](../decisoes/eixos-ranking.md).

## `/scores/{pushId}`

Log em bruto, um por partida. Campos exactos:

```
uid, modo, categoria, formato, score, correctCount, total, timestamp
```

`formato` = `solo | 1x1 | 2x2 | grupo` (o Histórico filtra por aqui). Registos antigos anteriores
a estes campos continuam legíveis; a validação só se aplica a escritas novas. **12 registos
legados não têm `uid`** e por isso deixaram de ser apagáveis por ninguém — não são atribuíveis a
nenhuma conta.

## `/lobbies` e `/multisalas`

O par que faz o multijogador:

- **lobby** = sala de espera (`hostUid`, `format`, `categoria`, `modo`, `estado`, `membros`,
  e `codigo` quando é privada). `estado`: `waiting` → `started`.
- **multisala** = a partida. `meta` é **create-once** (host escreve `membros`, `membrosNomes`,
  `perguntas` e fica imutável); cada jogador escreve só o seu nó em `jogadores/{uid}` e
  `pontuacoes/{uid}`, com tectos numéricos. `perguntaInicios/{index}` é o carimbo de tempo
  partilhado que sincroniza os relógios.

Separar `meta` (imutável) das pontuações é o que permite trancar cada pontuação ao dono: em RTDB
o `.write` **cascateia para baixo**, por isso uma permissão ao nível da sala teria anulado a
proteção por jogador.

Ver também: [rules](rules.md) · [multiplayer](../funcionalidades/multiplayer.md)
