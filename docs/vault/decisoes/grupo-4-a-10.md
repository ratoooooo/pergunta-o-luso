# Grupo joga com 4 a 10

← [índice](../00-indice.md)

## A decisão

`MatchFormat` tem **dois** números, e a distinção é o cerne:

- **`players`** = capacidade (lugares na sala). Enche → arranca sozinha.
- **`minPlayers`** = quanto é preciso para **poder** começar.

| Formato | `minPlayers` | `players` |
|---|---|---|
| 1x1 | 2 | 2 |
| 2x2 | 4 | 4 |
| Grupo | **4** | **10** |

## Porquê

O Grupo tinha `players = 10` e mais nada. Isso significava que **só era jogável com dez pessoas
na mesma categoria e no mesmo modo ao mesmo tempo** — na prática, nunca. Quatro é o mínimo em que
"todos contra todos" ainda significa alguma coisa.

1x1 e 2x2 mantêm `minPlayers = players`: um duelo precisa dos dois, e o 2x2 precisa de quatro
porque o anfitrião divide em duas equipas de dois.

## Um defeito corrigido de caminho

O botão "INICIAR JOGO" tinha a condição `joinedCount >= 2`. Com isso, **um 2x2 podia ser
arrancado com 2 ou 3 jogadores** — e aí a divisão em equipas deixava uma equipa com um jogador
só. Passar para `>= format.minPlayers` (4 no 2x2) fecha essa janela. Não era o alvo da mudança.

A guarda está repetida no `MultiMatchViewModel.forceStartGame`, **não só no ecrã**: o temporizador
de auto-arranque dispara de uma corrotina que pode chegar **depois** de alguém sair da sala, e
nesse instante a contagem já não é a que estava no ecrã quando o relógio começou.

## Auto-arranque: mantido

O temporizador de 60 s reinicia a cada entrada nova — **essa reposição é a janela de graça**:
cada jogador novo compra mais 60 s, por isso uma sala a encher continua a esperar e só uma sala
parada fecha sozinha.

Manteve-se em vez de exigir sempre acção do anfitrião porque sem ele um Grupo com 4 fica refém da
atenção de uma pessoa: se o anfitrião se distrai, ninguém joga. Como agora só arma a partir do
mínimo, deixou de poder fechar uma sala de 2 ou 3.

## O matchmaking não precisou de mudar

O limiar de entrada continua `membrosCount < players` (até 10) — **nenhum limiar do repositório
foi tocado**. O que mudou foi só o *gate de arranque*, que é uma decisão de jogo, não de fila.

## Texto gerado, não escrito à mão

O `FormatScreen` dizia "Quatro jogadores, todos contra todos" e ficou errado quando o Grupo
passou para 10 — durante fases, sem ninguém dar por isso. Agora vem de
`MatchFormat.GRUPO.sizeLabel`. Foi assim que se desencontrou da primeira vez.

`MatchFormatTest` (6 testes) prende as invariantes: mínimo nunca acima da capacidade, mínimo ≥ 2,
formatos por equipas com tamanho fixo e par, só o Grupo flexível, e o rótulo.

## Observado a 9 ago 2026

Salas de **5 e de 6 jogadores em simultâneo** correram de ponta a ponta: a sala continuou a
aceitar entradas depois do mínimo (`4/10 → 5/10 → 6/10`), o temporizador reiniciou a cada
entrada, e o arranque manual acima do mínimo e abaixo da capacidade funcionou nas duas corridas.
Detalhes em [multiplayer](../funcionalidades/multiplayer.md).

**7 a 10 continua por observar** — não há emuladores que cheguem.

O teste apanhou um defeito que só existe acima de 4: o título do pódio pára no 4.º lugar
(`MultiMatchViewModel.kt:559`), por isso um 5.º classificado lê *"4.º lugar"*. É consequência
directa de o Grupo ter passado de 4 fixos para 4–10 — o `when` do título ficou com a forma
antiga.

Ver também: [multiplayer](../funcionalidades/multiplayer.md)
