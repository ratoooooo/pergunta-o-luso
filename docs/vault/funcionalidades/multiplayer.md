# Multijogador e matchmaking

← [índice](../00-indice.md)

Um sistema **generalizado de N jogadores** (`game/multi/`, `data/MultiMatchRepository.kt`)
serve os três formatos. O 1x1 autónomo que existiu no início foi dobrado aqui e removido.

## Formatos

`MatchFormat` tem **dois** números por formato, e a distinção importa:

| Formato | `minPlayers` (poder começar) | `players` (capacidade) | Equipas |
|---|---|---|---|
| 1x1 | 2 | 2 | não |
| 2x2 | 4 | 4 | sim (2+2) |
| **Grupo** | **4** | **10** | não (todos contra todos) |

Só o Grupo é flexível (`hasFlexibleSize`). Porquê 4 a 10 e não 10 fixos:
[grupo-4-a-10](../decisoes/grupo-4-a-10.md).

Multijogador oferece **Clássico + Caótico apenas** — Eliminatórias é solo, o formato de
sobrevivência não mapeia numa ronda sincronizada.

## Matchmaking (o que corre mesmo)

**É baseado em lobbies**, sobre `/lobbies/{formato}`. Não há fila.

1. `findOrCreateLobby` corre uma **transação sobre o nó do formato inteiro**: entra no primeiro
   lobby `waiting` com a **mesma categoria e modo** e com `membrosCount < players`; se não houver
   nenhum, cria um e quem cria é anfitrião.
2. O jogador vê os outros a entrarem em tempo real (`observeLobby`) e pode trocar de sala com
   `switchLobby` ("VER OUTRAS SALAS ABERTAS").
3. A partida arranca quando:
   - a sala **enche** (`>= players`) → automático, o anfitrião cria a multisala; **ou**
   - o anfitrião carrega em **INICIAR JOGO**, disponível a partir de `minPlayers`; **ou**
   - o **temporizador de 60 s** expira. Reinicia a cada entrada nova — essa reposição *é* a
     janela de graça: uma sala a encher continua a esperar, uma sala parada fecha sozinha. Só
     arma a partir de `minPlayers`.
4. `startLobbyRoom` cria `/multisalas/{id}`, escreve `meta` (create-once) e põe o lobby a
   `started`; toda a gente entra por aí.

> **`/matchmakingN` é código morto.** `MultiMatchRepository.createRoom` — a única função que lá
> escreve — não é chamada de lado nenhum. As rules continuam publicadas. Apagar os dois é
> limpeza segura, ainda por fazer.

## Salas privadas por código

Alternativa ao matchmaking aleatório, usada pelos [Quizzes da
Comunidade](quizzes-comunidade.md). Ordem obrigatória: **lobby → código → sala**, porque a regra
de `/salas_privadas` exige que quem regista o código já seja o anfitrião do lobby referido.

O código tem 4 dígitos (9000 possíveis) e é **create-once**, por isso uma colisão falha em vez de
repontar silenciosamente a sala de outra pessoa — daí a retentativa. Quem entra pelo código tem
de ser acrescentado a `meta.membrosNomes`, senão entra no lobby e **não consegue ler a sala** (a
regra de leitura exige constar dessa lista).

## Sincronização (lockstep)

- O início de cada pergunta é carimbado **uma vez, no servidor**: transação sobre
  `perguntaInicios/{index}` com `ServerValue.TIMESTAMP`. Todos os clientes leem o mesmo valor.
- Cada cliente calcula `remaining = duração − (serverNow − inícioPartilhado)`, com
  `serverNow = System.currentTimeMillis() + serverTimeOffset`.
- **O offset é relido no início de cada pergunta**, não uma vez por partida — um offset velho era
  a principal fonte de desvio.
- Avança-se para a pergunta seguinte quando **todos os jogadores activos responderam** ou o
  cronómetro partilhado expira.

Desvio residual: limitado pelo tick de 100 ms mais jitter de rede. Eliminá-lo por completo exigia
um canal de sincronização contínuo, ou seja, um servidor.

## Desistências e walkover

Cada cliente arma `onDisconnect` para pôr o seu `estado = "off"` ao entrar na sala.

- **Sem equipas** (1x1, Grupo): se sobrar **um só jogador activo** numa sala que tinha mais do
  que um, a partida fecha por walkover. A condição é genérica de propósito — no Grupo, sair um de
  quatro deixa três e o jogo segue.
- **2x2:** se **qualquer** jogador sai, **a equipa dele perde**. Escolhido em vez de "jogar 1
  contra 2", que tornaria o total de equipa injusto.

Deteção em ~2 s depois de um force-stop.

## Pontuação e agregação

Reutiliza o `Scoring` do solo. No fim, cada dispositivo grava o **seu** resultado em `/scores`
(com `formato`) e agrega no perfil pela mesma via do solo, incluindo o caminho de walkover.
"Ganhou" por formato: 1x1 e Grupo = pontuação estritamente mais alta (empate não conta); 2x2 =
total de equipa estritamente maior.

## Grupo com mais do que o mínimo — observado

Testado a 9 ago 2026 com **6 emuladores** e depois com **5** (`/lobbies` e `/multisalas` limpos
antes). O que se viu, não o que se deduziu:

- A sala aceitou entradas até **6/10** sem tocar em nenhum limiar: o rótulo passou por
  `2/10 … 6/10`, sempre com `MÍNIMO 4`.
- Abaixo de 4: *"Faltam N para poder começar (mínimo 4, máximo 10)"*. Ao chegar a 4 apareceu
  *"Já podes começar. A sala continua a aceitar jogadores até 10."* e o botão **INICIAR JOGO
  (N JOGADORES) · Auto: NNs**, com o N a acompanhar cada entrada.
- **O temporizador reinicia mesmo a cada entrada** — lido logo a seguir a cada entrada nova
  deu 54 s, 49 s e 52 s numa corrida, 55 s e 56 s na outra: nunca ia abaixo do valor anterior,
  apesar de os segundos correrem entre leituras.
- Arranque **manual acima do mínimo e abaixo da capacidade** (6 de 10, e noutra corrida 5 de 10):
  funciona. A capacidade 10 nunca chegou a encher — não há emuladores para isso.
- **Pódio com 5 jogadores:** os 5 dispositivos mostraram a mesma lista, mesma ordem e mesmas
  pontuações (275 / 100 / 90 / 80 / 0), cada um com o seu *(tu)*. Sem discrepâncias.

**Defeito encontrado por causa disto:** o título do pódio está limitado ao 4.º lugar —
`MultiMatchViewModel.kt:559` faz `else -> "4.º lugar"`, por isso quem termina em 5.º a 10.º lê
*"4.º lugar"* com a sua linha a dizer **#5**. Só aparece com 5+ jogadores, que é exactamente o
cenário que nunca tinha sido corrido. Ver [por-fazer](../por-fazer.md).

## Limitações conhecidas

- **7 a 10 jogadores continua por observar** — a casa dá para 6 emuladores, e o sexto (a imagem
  16 KB) encravou. Que a sala aceita além de 6 é dedução a partir de `membrosCount < players`.
- Na corrida com 6, dois clientes apareceram como **"— saiu"** no pódio dos outros e uma
  pontuação chegou tarde. A RTDB dizia `estado: "terminado"` para todos: foi o Mac a ficar sem
  fôlego com 6 emuladores, não o código. Com 5 não voltou a acontecer.
- O temporizador de auto-arranque vive na **composição** da sala de espera e só corre para o
  anfitrião: se ele puser a app em segundo plano, o relógio pára.
- Salas e lobbies abandonados **não expiram** — a RTDB não tem TTL e não há limpeza. Estado velho
  de QA já causou emparelhamentos fantasma mais do que uma vez; convém limpar antes de testar.
- Pontuação é validada no cliente — ver [limitacoes-conhecidas](../seguranca/limitacoes-conhecidas.md).

Ver também: [modos-de-jogo](modos-de-jogo.md) · [rtdb-schema](../arquitetura/rtdb-schema.md) ·
[grupo-4-a-10](../decisoes/grupo-4-a-10.md)
