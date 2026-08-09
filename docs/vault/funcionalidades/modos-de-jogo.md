# Modos de jogo, pontuação e dificuldade

← [índice](../00-indice.md)

## Fluxo

```
Início → JOGAR → Formato (Solo / 1x1 / 2x2 / Grupo) → Categoria → Modo → Pergunta → Pódio
```

**Todos os formatos passam pela mesma escolha de Categoria e Modo.** No multijogador o passo do
modo leva ao matchmaking (sala de espera → "Encontrado!" → jogo).

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

## Vitória

O critério não é óbvio num quiz solo e está justificado em
[criterio-vitoria](../decisoes/criterio-vitoria.md).

Ver também: [conteudo-perguntas](conteudo-perguntas.md) · [multiplayer](multiplayer.md)
