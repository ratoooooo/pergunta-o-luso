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
| `/categorias_comunitarias/{id}` | quizzes criados por jogadores |
| `/denuncias/{quizId}/{uid}` | denúncias de quizzes |

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

## A partida ao vivo já não está aqui

`/lobbies`, `/multisalas`, `/salas_privadas` e `/matchmakingN` **foram removidos** a 29 ago 2026,
com a fase 6 do [servidor da partida](servidor-partida.md). A sala de espera e a partida vivem em
memória no servidor, que é quem decide certo/errado e a pontuação. O que sobra na RTDB é o
resultado: o servidor escreve `/scores` com a identidade `pol-servidor`, e a app agrega o perfil.

Separar `meta` (imutável) das pontuações era o que permitia trancar cada pontuação ao dono, porque
em RTDB o `.write` cascateia para baixo. Deixou de ser preciso: já não há caminho pelo qual um
dispositivo declare a sua própria pontuação de multijogador.

## Sequência diária (Fase 33)

`/jogadores/{uid}` ganhou cinco campos, todos declarados nas rules (o nó recusa campos não
declarados):

```
diasSeguidos        número   dias civis seguidos com pelo menos uma partida
ultimoDiaJogado     string   "AAAA-MM-DD", dia de Europe/Lisbon
maiorSequenciaDias  número   recorde histórico, nunca desce
protecoesStreak     número   0 ou 1
protecaoUsadaEm     string   "AAAA-MM-DD" do dia que a protecção tapou
```

Data e não timestamp, de propósito, e num fuso fixo — o porquê está em
[streak-diario](../funcionalidades/streak-diario.md). Escritos pela mesma transação que agrega a
partida, por isso contam em solo e em multijogador sem código duplicado.
