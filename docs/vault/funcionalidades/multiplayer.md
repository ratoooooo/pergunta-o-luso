# Multijogador e matchmaking

← [índice](../00-indice.md)

**A partida ao vivo corre num servidor próprio** (`servidor/`, num VPS), não na RTDB. O servidor
decide se a resposta está certa, calcula a pontuação, carimba o início de cada pergunta e apura o
vencedor. O cliente manda a opção que o jogador tocou e mais nada.

Como se lá chegou, fase a fase: [servidor-partida](../arquitetura/servidor-partida.md).
O contrato das mensagens é `servidor/PROTOCOLO.md` — é a fonte única, não este ficheiro.

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

## Matchmaking

Baseado em salas de espera, em memória no servidor. Não há fila.

1. `procurar` entra na primeira sala **à espera** com a mesma categoria e modo e com lugar; se
   não houver nenhuma, cria uma e quem cria é anfitrião.
2. O jogador vê os outros a entrar em tempo real e pode trocar de sala ("VER OUTRAS SALAS
   ABERTAS"), que manda `trocar_sala`.
3. A partida arranca quando a sala **enche**, quando o anfitrião carrega em **INICIAR JOGO**
   (disponível a partir de `minPlayers`), ou quando o **temporizador de 60 s** expira.
4. O temporizador reinicia a cada entrada nova — essa reposição *é* a janela de graça: uma sala a
   encher continua a esperar, uma sala parada fecha sozinha. Só arma a partir de `minPlayers`.

**Correcção que veio com o servidor:** o temporizador de 60 s corria na composição do ecrã do
anfitrião e parava se ele pusesse a app em segundo plano. Agora corre no servidor.

A guarda do mínimo vive **num sítio só** — o servidor. Antes estava repetida no ecrã e no
ViewModel, porque o temporizador podia disparar depois de alguém sair.

## Salas privadas por código

Alternativa ao matchmaking aleatório, usada pelos [Quizzes da
Comunidade](quizzes-comunidade.md). O cliente manda `privada_criar` com o id do quiz e o servidor
devolve a sala já com um código de 4 dígitos; quem entra manda `privada_entrar` com o código.

Duas coisas mudaram e são o ponto:

- **O anfitrião deixou de escolher as perguntas.** Era ele que as enviava, e portanto podia
  inventá-las. Agora é o servidor que lê o quiz de `/categorias_comunitarias`.
- **A ordem "lobby → código → sala" desapareceu.** Existia porque as rules da RTDB exigiam ver o
  lobby escrito antes de aceitar o código. O servidor cria as três coisas de uma vez.

O tecto de 10 perguntas (`MAX_PERGUNTAS_SALA`) mantém-se: as rules de `/scores` travam
`correctCount <= 20` e `total <= 20`.

## Desafios de amigos

O convite continua a viajar por `/convites` na RTDB. O que mudou é **quando** a sala nasce.

Na RTDB a sala era criada primeiro e o id viajava dentro do convite, com o desafiante a esperar
no ecrã Amigos com uma contagem decrescente. No servidor o id só existe depois de o socket abrir,
por isso **o desafiante entra já na sala de espera** e o convite sai de lá.

Não foi preferência: o servidor larga a sala quando o socket fecha, por isso um desafiante que
voltasse ao ecrã Amigos destruía a sala que acabou de criar. O convite é limpo quando a partida
arranca e quando o desafiante sai da sala.

## Sincronização (lockstep)

- O servidor abre cada pergunta com um `fimEm` no **seu** relógio e fecha-a quando todos os
  activos responderem ou o tempo esgotar.
- O cliente afere o desvio do seu relógio com `ping`/`pong` **a cada pergunta**, não uma vez por
  partida — um desvio velho era a principal fonte de dessincronia na versão RTDB, e a lição
  migrou com o resto.
- **A latência não custa pontos.** Os pontos dependem dos segundos que sobram, por isso carimbar
  a resposta à chegada penalizava quem tem rede pior. O cliente envia o instante em que
  respondeu e o servidor credita-o preso ao intervalo `[chegada − rtt, chegada]`, com o rtt
  medido por ele. Mentir só devolve o rtt real do próprio jogador.

## Desistências e walkover

O servidor vê o socket fechar. Abre-se uma **carência de 10 s** com "a reconectar…" no ecrã — o
lugar continua a ser do jogador, e o cliente tenta voltar. Passada a carência, é desistência.

- **Sem equipas** (1x1, Grupo): se sobrar **um só jogador activo** numa sala que tinha mais do
  que um, a partida fecha por walkover, e **quem fica ganha** — mesmo 0-0. No Grupo, sair um de
  quatro deixa três e o jogo segue.
- **2x2:** se **qualquer** jogador sai, **a equipa dele perde**, mesmo indo à frente no placar.
  Escolhido em vez de "jogar 1 contra 2", que tornaria o total de equipa injusto.

## Pontuação e agregação

A fórmula é a mesma do solo (`Scoring`), mas quem a aplica é o servidor. No fim:

- **o servidor** grava `/scores` de todos os jogadores, com a identidade `pol-servidor`;
- **a app** agrega o perfil (`/jogadores/{uid}`) pela mesma via do solo, com os números que o
  servidor lhe mandou — é daí que vêm XP, conquistas e sequência diária.

"Ganhou" por formato: 1x1 e Grupo = pontuação estritamente mais alta (empate não conta); 2x2 =
total de equipa estritamente maior; walkover = quem ficou.

> A fórmula de pontos existe em Kotlin e em JavaScript, e é a **única** duplicação deliberada do
> desenho — quem corrige tem de saber pontuar. Está presa por vectores calculados à mão em
> `servidor/testes/pontuacao.json`, lidos pelos testes dos dois lados.

## Observado

**Grupo com 5 e 6 jogadores** (9 ago 2026, ainda na RTDB): a sala aceitou entradas até 6/10 sem
tocar em nenhum limiar, o temporizador reiniciou a cada entrada, e o arranque manual acima do
mínimo e abaixo da capacidade funcionou. Os 5 dispositivos mostraram o mesmo pódio, mesma ordem e
mesmas pontuações.

**Grupo com 10** (28 ago 2026): corrido contra o servidor pelo cliente de teste em
`servidor/testes/` — o cenário que nunca se conseguiu com emuladores, por não haver máquinas que
cheguem.

**1x1, 2x2 e Grupo em produção** (29 ago 2026): partidas reais com dispositivos, pontuações
idênticas nos dois lados, cronómetros alinhados ao pixel em capturas com 166 ms de intervalo, e
walkover observado entre t+8 s e t+10 s depois de fechar a app — dentro da carência.

## Limitações conhecidas

- **Ponto único de falha:** o VPS em baixo é multijogador em baixo. O solo não é afectado.
- **Quem desiste não agrega.** O `/scores` fica com os dois jogadores, porque é o servidor que o
  escreve, mas o perfil só é actualizado por quem estava vivo no pódio — a agregação corre no
  dispositivo. Vem do desenho antigo e não é regressão.
- **Quem esgota o tempo não vê a resposta certa.** O servidor só a manda a quem respondeu. Antes,
  na RTDB, toda a gente a via porque vinha com a pergunta — que era exactamente o problema.
- **O perfil agregado continua escrito pelo cliente** e continua falsificável, ao mesmo nível do
  solo. Ver [limitacoes-conhecidas](../seguranca/limitacoes-conhecidas.md).

Ver também: [modos-de-jogo](modos-de-jogo.md) · [servidor-partida](../arquitetura/servidor-partida.md) ·
[grupo-4-a-10](../decisoes/grupo-4-a-10.md)
