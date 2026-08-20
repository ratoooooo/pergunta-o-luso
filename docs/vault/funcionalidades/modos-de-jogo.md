# Modos de jogo, pontuação e dificuldade

← [índice](../00-indice.md)

## Fluxo

```
Início → JOGAR → Formato (Solo / 1x1 / 2x2 / Grupo) → Categoria → Modo → Pergunta → Pódio
```

**Todos os formatos passam pela mesma escolha de Categoria e Modo.** No multijogador o passo do modo leva ao matchmaking (sala de espera → "Encontrado!" → jogo).

## Modos

| Modo | Perguntas | Acaba ao primeiro erro | Eventos |
|---|---|---|---|
| Clássico | 10 | não | não |
| Caótico | 10 | não | sim |
| Eliminatórias | 20 | **sim** | não |

- **Caótico:** um evento determinístico por pergunta (`ChaoticEvent.forIndex`), em faixa no topo.
  Ciclo: `pergunta_dupla`, `velocidade_maxima`, `roubo`, `tudo_ou_nada`.
- **Eliminatórias:** um erro (ou tempo esgotado) acaba o jogo → pódio "Eliminado!". Carrega 20
  perguntas para a sobrevivência ser o desafio.
- Multijogador: **só Clássico e Caótico**.

## Cronómetro e pontuação (`game/Scoring.kt`)

15 s por pergunta (7,5 s com o evento `velocidade_maxima`).

**Resposta certa:**
```
base = segundosRestantes * 10
  × 2                    se pergunta_dupla
  × multiplicador de dificuldade
  + bónus de sequência   2 seguidas +50 · 3 +75 · 4 ou mais +100
  + 50                   se roubo
  + 100                  se tudo_ou_nada
```

**Errada ou tempo esgotado:** 0 pontos e a sequência reinicia — excepto `tudo_ou_nada`, que tira
50. Total corrente nunca desce abaixo de 0.

## Dificuldade

Cada pergunta tem `dificuldade` = `facil | medio | dificil`, usada de **duas** maneiras:

1. **Rampa** — as perguntas são baralhadas *dentro* de cada nível e depois concatenadas
   `facil → medio → dificil` (`QuestionRepository.buildProgression`, ~⅓ por nível). A ordem varia
   a cada jogo mas a ronda sobe sempre de dificuldade.
2. **Multiplicador** — `facil ×1.0`, `medio ×1.5`, `dificil ×2.0` sobre a base.

Assim o campo é usado em todos os modos, não como filtro pontual, e a rampa torna as
Eliminatórias progressivamente mais difíceis à medida que se sobrevive.

## Eliminatórias: três vidas, sem fim (9 ago 2026)

Era **"erras uma e acabou"** com 20 perguntas. Um erro na primeira pergunta acabava a partida em
vinte segundos, e o modo vive de sequências longas — a regra antiga castigava exactamente o que
o modo queria premiar.

- **Três vidas** (`GameMode.vidas = 3`). O `endsOnFirstWrong: Boolean` deu lugar a um
  `vidas: Int`, onde `0` significa "este modo não elimina". Um modo com vidas é, por construção,
  um modo sem limite de perguntas (`semLimiteDePerguntas`).
- **Não há total.** O cabeçalho diz "Pergunta 14", não "Pergunta 14 de 20" — prometer um fim que
  não existe era pior do que não prometer nada.
- **Vidas no ecrã:** três corações, os gastos ficam em contorno em vez de desaparecerem (a fila
  não encolhe, vê-se de relance quanto já se perdeu). O último pulsa. O coração que morre dá um
  pinote antes de esvaziar.
- **Vitória passou a ser um marco.** "Sobreviver às 20" deixou de existir, porque a corrida acaba
  sempre em eliminação — a manter-se, o bónus de XP ficava inalcançável para sempre. Agora ganha
  quem chega às **20 perguntas respondidas** (`ELIMINATORIAS_MARCO_VITORIA`), o mesmo número de
  antes. A fórmula de XP não mudou: base 40 + 10 por acerto + 80 de vitória.

### O pool de 20 recarrega em fundo

O lote inicial continua a ser 20; o modo é que já não acaba lá. A **5 perguntas do fim do lote**
(`PREFETCH_MARGEM`) arranca um carregamento em fundo que acrescenta perguntas ainda não vistas
nesta corrida. Cinco e não uma porque descarregar a categoria leva bem mais do que os 15 s de
uma pergunta num emulador lento.

Se o lote acabar mesmo assim — pedido falhado, ou categoria sem perguntas por usar — as já
respondidas voltam **baralhadas de novo**. Repetir uma pergunta é menos mau do que cortar uma
sequência boa por falta de banco.

O lote a caminho é cancelado quando a partida acaba ou o jogador sai (`cancelPrefetch`), e a
escrita verifica que ainda se está no mesmo jogo antes de aterrar. Sem isso um lote atrasado
escrevia por cima de um ecrã que já não era o do jogo — foi observado a deixar o ecrã de
categorias preso em "A carregar…".

Verificado no emulador: corrida levada até à **pergunta 28** sem interrupção, atravessando a
fronteira do lote de 20; e as três vidas gastas uma a uma até "Eliminado!", com +400 XP
(40 + 28×10 + 80), ou seja com o bónus de vitória a entrar pelo marco.

## Resposta e revelação

Em repouso as opções são **cartões lavanda neutros com emblema roxo A/B/C/D** — a letra
distingue, não a cor. Só depois de responder é que o cartão toma a cor do resultado:

- certa **e** escolhida → Teal
- certa mas escolheste outra (ou esgotou o tempo) → Dourado (revela a certa)
- a tua escolha errada → Coral
- restantes → Neutral

Em Verdadeiro/Falso o emblema mostra ✓/✗ e a ordem é canónica (Verdadeiro primeiro) — baralhar
"Verdadeiro"/"Falso" lê-se como bug. Porquê neutro em repouso:
[hierarquia-de-cor](../decisoes/hierarquia-de-cor.md).

Há uma **carência de 350 ms** ao abrir cada pergunta em que toques são ignorados
(`INPUT_GRACE_MS`), porque o feedback dura 1000 ms e a pergunta seguinte abre no mesmo sítio —
um duplo-toque respondia-a sozinho.

**Medida em emulador (9 ago 2026), não deduzida.** Dois toques na mesma linha disparados de uma
só invocação `adb shell`, variando o intervalo entre eles; o primeiro responde à pergunta *n*, o
segundo cai na *n+1* (que abre 1000 ms depois). O índice avança 1 se a carência o travou, 2 se
passou:

| Intervalo entre toques | Segundo toque cai a… | Índice avançou |
|---|---|---|
| 811 ms | ainda no feedback | 1 — travado |
| 1010 ms | ~10 ms na pergunta nova | 1 — travado |
| 1237 ms | ~237 ms | 1 — travado |
| 1309 ms | ~309 ms | 1 — travado |
| 1357 ms | ~357 ms | **2 — passou** |
| 1406 ms | ~406 ms | **2 — passou** |

Os intervalos são medidos com `date +%s%3N` dentro do próprio `adb shell`, entre o fim do
primeiro `input tap` e o início do segundo; o toque real chega algumas dezenas de ms depois,
por isso a coluna do meio é um mínimo. Mesmo assim a fronteira cai entre ~310 ms e ~360 ms
depois de a pergunta abrir, ou seja **em cima dos 350 ms da constante**. Uma partida solo completa (Geografia · Clássico) a seguir: as 10 respostas
registaram todas à primeira, cada uma a avançar ~2 s depois do toque — nenhuma foi por
esgotamento de tempo (15 s). A carência não estorva quem responde depressa e continua a apanhar
o duplo-toque.

## Vitória

O critério não é óbvio num quiz solo e está justificado em
[criterio-vitoria](../decisoes/criterio-vitoria.md).

Ver também: [conteudo-perguntas](conteudo-perguntas.md) · [multiplayer](multiplayer.md)
