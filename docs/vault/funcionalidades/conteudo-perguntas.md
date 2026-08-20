# Conteúdo das perguntas

← [índice](../00-indice.md)

## Números

**1685 perguntas** em `/categorias/{cat}/perguntas`. Sete categorias existem; **duas estão
escondidas do picker** com os dados intactos (`Património Português`, `Gastronomia Portuguesa`,
55 perguntas).

| Categoria visível | Perguntas | Cor | Ícone |
|---|---|---|---|
| Cultura Geral | 326 | Purple | Lightbulb |
| Desporto | 326 | Teal | SportsSoccer |
| Gentílicos | 326 | Coral | Groups |
| Geografia | 326 | Gold | Public |
| História | 326 | Azul `#3D6EE8` | HistoryEdu |

1630 visíveis + 55 escondidas = 1685. Estes números aparecem no picker, contados por
`shallow=true` — ver abaixo.

> O valor antigo de **1090** andou a ser citado como "1090 em 5 categorias visíveis", o que nunca
> foi verdade: 1090 era o total das **sete**; as visíveis eram 1035. Corrigido aqui e no índice.

## Nivelamento de 9 ago 2026

As cinco categorias visíveis estavam desiguais (111 a 326). Foram niveladas **pelo topo** —
História, 326 — com **595 perguntas novas**:

| Categoria | Antes | Novas | Depois |
|---|---|---|---|
| Gentílicos | 111 | +215 | 326 |
| Desporto | 169 | +157 | 326 |
| Geografia | 212 | +114 | 326 |
| Cultura Geral | 217 | +109 | 326 |
| História | 326 | — | 326 |

Como foram feitas, e o que isso implica para quem confiar nelas:

- **Deduplicadas contra o banco existente** por enunciado normalizado (sem acentos, sem
  pontuação). Quatro colisões apanhadas e substituídas. Zero duplicados dentro de cada categoria
  depois da escrita.
- **30 perguntas foram escritas e depois deitadas fora** antes de chegarem à base de dados, por
  enunciado ambíguo, resposta contida na pergunta, ou facto que não se conseguiu dar como
  seguro. A lista está em `BLOQUEADAS`, no script de merge.
- **Gentílicos** cobre agora ~200 concelhos que faltavam, mais regiões e países lusófonos. As
  opções erradas são variações de sufixo sobre o mesmo topónimo, o mesmo padrão do banco antigo
  (Braga → Bracarense / Braguês / Bragão / Bragense).
- Dificuldade continua misturada: no banco inteiro, 455 `facil`, 742 `medio`, 488 `dificil`.

**Por confirmar:** as 595 novas **não** passaram pela revisão factual manual que as originais
levaram na Fase 17 — foram escritas com factos estáveis e verificáveis e passaram a validação
estrutural, mas não há uma segunda leitura humana. Convém uma passagem de olhos, sobretudo em
Desporto (datas e palmarés envelhecem) e nos gentílicos menos correntes.

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
nada na base de dados: o `QuestionRepository` só rejeitava `opcoes.size < 2`. 35 perguntas são
V/F. `Question.isVerdadeiroFalso` deteta o tipo.

## Contagem no picker

`CategoryRepository.loadQuestionCounts` usa **`shallow=true`** (uma chamada por categoria, em
paralelo). Ler o nó para lhe chamar `size` traria ~1 MB só para escrever um número, e o
`QuestionRepository` volta a descarregar a categoria escolhida logo a seguir.

Uma categoria que falhe fica **fora do mapa**, não a zero: o ecrã trata a ausência como "ainda
não sei" e não escreve nada — "0 perguntas" seria uma mentira plausível sobre uma categoria que
está lá.

## Qualidade do conteúdo

As 595 acrescentadas em 9 ago 2026 estão cobertas acima. O que se segue é sobre o banco original.

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

**Há um caminho melhor e foi o usado no nivelamento de 9 ago 2026:** `firebase database:set`
autentica-se como administrador e as rules **não se aplicam** a esse token. Escreve-se com as
rules trancadas o tempo todo — a janela em que `/categorias` fica aberta ao mundo deixa de
existir. O passo do backup mantém-se, e o PUT sem token continua a valer como confirmação
(devolveu `Permission denied` depois da escrita).

Ver também: [modos-de-jogo](modos-de-jogo.md) · [rules](../arquitetura/rules.md)
