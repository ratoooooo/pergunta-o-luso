# Servidor da partida ao vivo

← [índice](../00-indice.md)

A partida de multijogador corre num processo Node num VPS, e não na RTDB. O servidor decide se a
resposta está certa, calcula a pontuação, abre e fecha cada pergunta e apura o vencedor.

- **Código:** `servidor/` neste repositório. **Protocolo:** `servidor/PROTOCOLO.md` — fonte única.
- **Como funciona para quem joga:** [multiplayer](../funcionalidades/multiplayer.md).
- **Instalação e deploy:** `servidor/deploy/INSTALAR.md`.

## Porque existe

O dispositivo decidia se a resposta estava certa e somava os pontos; as rules só travavam o
matematicamente impossível. Esconder a `respostaCorreta` era impossível — o cliente tinha de a
ler para corrigir. Ver [limitacoes-conhecidas](../seguranca/limitacoes-conhecidas.md), ponto 1.

## O que sai e o que fica

| Caminho | Onde está hoje |
|---|---|
| Sala de espera, partida, salas por código | **memória do servidor** (eram `/lobbies`, `/multisalas`, `/salas_privadas`) |
| `/matchmakingN` | apagado — já era código morto antes deste trabalho |
| `/convites` | **fica na RTDB** — o convite viaja por lá; muda só o que o `salaId` significa |
| `/categorias`, `/categorias_comunitarias` | **ficam**, e passam a ser lidas pelo servidor |
| `/scores` | **fica**, escrito pelo servidor |
| `/jogadores`, `/amigos`, `/presenca`, `/denuncias` | **intocados** |

## Duas decisões que explicam o resto

**Sem Colyseus, `ws` puro.** Uma partida são 10 perguntas com uma resposta por jogador — não há
estado contínuo para comprimir. O que o Colyseus daria de real (ciclo de sala, reconexão) são
poucas linhas, e o custo caía todo no Android: cliente Java e descodificador de schema presos à
versão do servidor. Com `ws` + JSON, o Android ganhou **uma** dependência (OkHttp) em vez de três.

**O servidor não é admin do Firebase.** `databaseAuthVariableOverride` fá-lo autenticar-se como
`pol-servidor`, um utilizador **sujeito às rules**. Os poderes que tem são os que
`database.rules.json` lhe dá: ler o que qualquer autenticado lê, e criar registos em `/scores`
com um `formato` de multijogador e com o uid de outra pessoa (é ele que apura o resultado de
todos). Um defeito no processo não pode fazer mais do que isso.

## Fases — todas feitas

| Fase | O quê | Concluída |
|---|---|---|
| 0 | Infra-estrutura: DNS, TLS por Caddy, systemd, ufw, `/saude` | 28 ago 2026 |
| 1 | Servidor completo, com testes, sem tocar no Android | 28 ago 2026 |
| 2 | Rules de `/scores`: via do `pol-servidor`, clientes só `solo` | 28 ago 2026 |
| 3 | Transporte Android atrás de flag desligada | 28 ago 2026 |
| 4 | 1x1 pelo servidor, verificado com dispositivos | 28 ago 2026 |
| 5 | 2x2, Grupo, salas privadas e desafios no transporte | 29 ago 2026 |
| 5b | `GameViewModel` ligado ao servidor (desafios e salas privadas) | 29 ago 2026 |
| 6 | Flag removida, caminho RTDB apagado, rules e dados limpos | 29 ago 2026 |

A fase 5b não estava no plano. Apareceu porque a 6 não era executável sem ela: o `GameViewModel`
continuava a criar salas na RTDB, por isso apagar essas regras partia desafios e salas privadas,
e apagar as funções não compilava.

## Tectos assumidos

- **Um processo, estado em memória, sem Redis.** Lobbies e partidas morrem num reinício — daí a
  drenagem no `SIGTERM`, que recusa partidas novas e deixa acabar as que estão a correr. Um
  segundo processo obrigaria a estado partilhado, e é aí que Redis ou Colyseus voltam à mesa.
- **Ponto único de falha.** O VPS em baixo é multijogador em baixo; o solo continua.
- **A fórmula de pontos existe em duas linguagens.** Inevitável — quem corrige tem de pontuar.
  Presa por vectores calculados à mão, lidos pelos testes dos dois lados.
