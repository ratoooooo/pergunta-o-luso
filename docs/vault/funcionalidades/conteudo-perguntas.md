# Conteúdo das perguntas

← [índice](../00-indice.md)

## Números

**1090 perguntas** em `/categorias/{cat}/perguntas`. Sete categorias existem; **duas estão
escondidas do picker** com os dados intactos (`Património Português`, `Gastronomia Portuguesa`,
55 perguntas).

| Categoria visível | Perguntas | Cor | Ícone |
|---|---|---|---|
| Cultura Geral | 217 | Purple | Lightbulb |
| Desporto | 169 | Teal | SportsSoccer |
| Gentílicos | 111 | Coral | Groups |
| Geografia | 212 | Gold | Public |
| História | 326 | Azul `#3D6EE8` | HistoryEdu |

1035 visíveis + 55 escondidas = 1090. Estes números aparecem no picker, contados por
`shallow=true` — ver abaixo.

A cor de História foi mudada para azul real: a anterior lia-se demasiado perto do Teal do
Desporto. A cor é **identidade** da categoria (reaparece no chip do Modo, no cabeçalho da
pergunta e no Histórico), não hierarquia de ação.

## Formato

```json
{
  "pergunta": "Quem foi o primeiro rei de Portugal?",
  "opcoes": ["D. Afonso Henriques", "D. João I", "D. Dinis", "D. Manuel I"],
  "respostaCorreta": "D. Afonso Henriques",
  "dificuldade": "facil"
}
```

**Verdadeiro/Falso usa o mesmo schema** — é só `opcoes` com dois valores. Não foi preciso mudar
nada na base de dados: o `QuestionRepository` só rejeitava `opcoes.size < 2`. 34 perguntas são
V/F. `Question.isVerdadeiroFalso` deteta o tipo.

## Contagem no picker

`CategoryRepository.loadQuestionCounts` usa **`shallow=true`** (uma chamada por categoria, em
paralelo). Ler o nó para lhe chamar `size` traria ~1 MB só para escrever um número, e o
`QuestionRepository` volta a descarregar a categoria escolhida logo a seguir.

Uma categoria que falhe fica **fora do mapa**, não a zero: o ecrã trata a ausência como "ainda
não sei" e não escreve nada — "0 perguntas" seria uma mentira plausível sobre uma categoria que
está lá.

## Qualidade do conteúdo

As 964 perguntas originais passaram por validação **estrutural automática** (0 respostas fora das
opções, 0 opções duplicadas, 0 dificuldades inválidas, 0 repetidas) e por **revisão factual
manual** das 909 das cinco categorias visíveis. Daí saíram **43 correções + 1 remoção**, em cinco
grupos: erradas com confiança, premissa falsa/mal formuladas, ambíguas, não confirmáveis, e
sensíveis ao tempo (a estas foi acrescentada âncora temporal explícita, ex.: "até 2023").

**~11 perguntas continuam por confirmar** e foram deixadas exactamente como estavam, em vez de
"corrigidas" por especulação. A lista nominal está na Fase 17 do
[arquivo](../historico-fases/README.md).

## Nota operacional

`/categorias` tem `.write: false`. Para semear ou corrigir, o procedimento foi sempre: destrancar
temporariamente as rules → backup de `/categorias` → escrever → **voltar a trancar e confirmar
com um PUT que devolve `Permission denied`**.

Ver também: [modos-de-jogo](modos-de-jogo.md) · [rules](../arquitetura/rules.md)
