# Sistema visual e componentes partilhados

← [índice](../00-indice.md)

Estilo **sticker** / banda desenhada: cores planas, contorno grosso, sombra dura sem desfoque.

| Elemento | Valor |
|---|---|
| Fundo | Creme `#EAE6DD` |
| Cartões | Lavanda `#F6F1FB` |
| Contorno | Tinta `#1A1523`, 3 dp |
| Sombra | round-rect de tinta deslocado para baixo/direita, **sem blur** |
| Cantos | 18–36 dp |
| Paleta | Roxo `#6C3CE0` · Dourado `#FFC93C` · Coral `#FF6B5B` · Teal `#2FBF9F` · Azul `#3D6EE8` · Neutral `#C9BEDD` |
| Tipografia | Fredoka (títulos) + Manrope (texto), variable fonts em `res/font` |
| Ícones | Material Symbols Rounded — **sem emojis** |

Base em `ui/theme/Sticker.kt`: `stickerBlock`, `stickerCircle`, `stickerDashed`.

## Componentes partilhados

| Componente | Onde vive | Notas |
|---|---|---|
| `SegmentedTabs` | `ui/theme/Tabs.kt` | nível principal — pastilha roxa em calha lavanda. Corpo desce a 13 sp com 4+ separadores |
| `UnderlineTabs` | `ui/theme/Tabs.kt` | nível secundário — sublinhado roxo 22×3 dp |
| `StickerButton` | `ui/theme/StickerButton.kt` | deriva a cor do texto/ícone do `fillColor` via `textColorFor` |
| `StickerDialog` | `ui/theme/StickerDialog.kt` | substitui `AlertDialog` do Material3, que parecia um formulário colado |
| `StickerTextField` | `ui/theme/StickerTextField.kt` | `icon` opcional; olho de revelar palavra-passe |
| `AnswerOption` | `game/AnswerOption.kt` | **partilhado solo + multijogador** |
| `ResultStats` | `game/ResultStats.kt` | Perguntas · Precisão · XP ganho, no pódio |
| `Motion.kt` | `ui/theme/` | `cascadeIn`, `bounceIn`, `rememberPressScale`, `rememberPulse`, `rememberGlow`, `stickerSpring` |
| `BottomNav` | `ui/theme/BottomNav.kt` | 4 secções: Início · Ranking · Amigos · Perfil |

## Regras de movimento

**Nada bloqueia o jogo.** Todas as entradas são visuais (opacidade/escala/posição), nunca
`enabled = false` enquanto animam — um toque num botão funciona a meio da cascata.

`bounceIn` é usado **num sítio só**: o "Encontrado!" do multijogador, onde os adversários
aparecem pela primeira vez.

## Cor

A regra de hierarquia de cor é a decisão mais estruturante deste sistema e tem nota própria:
[hierarquia-de-cor](../decisoes/hierarquia-de-cor.md).

## Divergências do mockup que se mantêm

O `Pergunta o Luso - Redesign.html` (17 ecrãs) foi a referência. Continuam por igualar, com razão
documentada: contadores de jogadores em estilo marketing, distintivo de streak no Início,
arte exacta das conquistas, secção "ACESSO RÁPIDO" no Início, imagens nas perguntas, separadores
Oficial/Personalizadas no Histórico, ícone `castle` para História, e o ecrã de jogo com fundo
escuro (recusado — um único ecrã escuro leria como outra app). Lista completa e estado de cada
uma na Fase 11 do [arquivo](../historico-fases/README.md).

## Fase 30 — polish (9 ago 2026)

**Chips do cartão de perfil.** "pontos" e "acertos" partiam em duas linhas dentro do chip e
desalinhavam-nos de "jogos". A legenda é rótulo, não texto corrido: desceu para 13 sp com
`maxLines = 1` e o padding horizontal de 12 para 8 dp. O número mantém-se em `labelLarge` — a
hierarquia dentro do chip não mudou. Fica de pé o [reflow adaptativo](../por-fazer.md) para
letra de sistema muito grande; isto resolve o caso normal, não substitui aquilo.

**Barra de XP: roxo liso.** Era um gradiente Teal → Azul → Roxo. Ver
[Progression.kt](../../../app/src/main/java/com/ratoooooo/perguntaoluso/ui/theme/Progression.kt)
para o porquê da cor; em resumo, o gradiente resolvia um conflito com a barra do tempo que não
existe (as duas nunca partilham ecrã) e em troca lia-se diferente conforme a largura. O brilho de
"quase a subir de nível" passou de Dourado a Cream, para a barra não voltar a ler-se como duas
cores nem competir com o botão JOGAR.

**ⓘ nos três ecrãs de escolha.** Formato, categoria e modo passaram a ter um ícone de informação
no `ScreenHeader` — vive no componente partilhado para os três o porem no mesmo sítio. Disco
Cream, neutro de propósito: Dourado é a ação primária do ecrã, Teal e Coral são cores de estado.
Abre um `StickerDialog` cuja regra de texto é **não repetir o cartão**: explica o matchmaking, o
que a contagem de perguntas significa, e como os pontos se calculam.

**Micro-transições novas — só duas.** O coração que se perde nas Eliminatórias dá um pinote antes
de esvaziar (era a única mudança de estado do ecrã que passava despercebida), e o `StickerDialog`
entra com o mesmo `stickerSpring` de tudo o resto em vez de aparecer instantaneamente. O resto
dos ecrãs ficou como estava.

**Quizzes da Comunidade escondidos.** `FeatureFlags.QUIZZES_COMUNIDADE_VISIVEIS = false` tira o
botão do Início. Nada foi apagado — ecrã, repositório, salas privadas por código e rules estão
intactos, e a flag a `true` devolve tudo. Era o **único** ponto de entrada na navegação normal
(o ramo `"COMUNIDADE"` em `selectCategory` já era código morto), por isso não ficou botão sem
destino em lado nenhum.

## Fase 34 — o pódio depois do "A seguir"

**O cartão "A seguir" era Cream sobre fundo Cream.** Praticamente sem superfície: ficava menos
destacado do que as linhas do ranking logo abaixo (essas em Lavanda) e escorria visualmente para
dentro do título "Melhores pontuações" — liam-se como um bloco só. Passou a usar o padrão que os
cartões de Formato e de Modo já usavam: **superfície Lavanda + emblema colorido**, contorno de
3 dp e sombra de 5 dp (mais do que os 4 dp das linhas do top), e o rótulo "A SEGUIR" em Roxo.

Roxo no emblema porque as outras cores estavam tomadas neste ecrã: Dourado é o mérito no cartão
de resultado (e a ação primária noutros ecrãs), Coral é a derrota mesmo por cima, Teal é
"resposta certa". Roxo já é a cor da progressão (barra de XP, `LevelPill`), que é exactamente o
que este cartão mostra. A folga até ao título subiu de 20 para 24 dp — aqui muda-se de secção,
não só de cartão.

### O título "Melhores pontuações" estava centrado — sozinho

Mudar o cartão não chegou. A coluna do pódio é `CenterHorizontally`, e **todos** os outros blocos
ocupam a largura toda, por isso alinham-se sozinhos à esquerda: o cabeçalho da secção era o único
elemento do ecrã sem largura própria e, portanto, o único centrado. Ficava a pairar no vão entre
o cartão "A seguir" e as linhas do top, sem se colar a nenhum dos dois — lia-se como texto solto
e não como o cabeçalho da lista que vem logo a seguir.

`Modifier.fillMaxWidth()` encosta-o à esquerda, ao mesmo x das arestas dos cartões (65 px contra
63 px, medido). É também o alinhamento que "Estatísticas globais" e "Por modo" já usam no Perfil —
o padrão da casa para títulos de secção era este, o pódio é que não o seguia.

### O pódio passou a deslizar

Acrescentar o cartão teve um efeito que só se vê no dispositivo: a coluna tinha altura fixa e a
lista tomava **o que sobrava** (`weight(1f)`). Com mais um cartão pelo meio, o que sobrava dava
para **uma** linha e a segunda ficava cortada a meio por cima dos botões — precisamente o defeito
que o comentário do `take(3)` já avisava para não repetir.

Tentou-se primeiro contar quantas linhas cabiam (`BoxWithConstraints`). Funcionava, mas a resposta
honesta era "uma": medido, o espaço livre eram 129,5 dp para linhas de 76 dp. Mostrar uma única
"melhor pontuação" não é secção nenhuma.

A correção foi tirar a competição por espaço: **a coluna desliza** (`verticalScroll`, como o ecrã
de Formato e o de Modo já fazem), e as três linhas aparecem sempre.

### E a lista deixou de ser uma `LazyColumn`

A primeira tentativa manteve a `LazyColumn` e deu-lhe **altura fixa** calculada à mão. Parecia
resolver e não resolvia: as sombras do sistema sticker são desenhadas **fora** dos limites do
bloco, por isso a conta ficava ~1 dp curta, a lista passava a ter **scroll próprio** e arrastar o
dedo por cima dela deslocava-a dentro da sua janela. O cartão do 1.º lugar aparecia cortado —
medido: **153 px de altura contra os 164 px dos outros dois**. Scroll dentro de scroll.

São três linhas: não há nada a reciclar e a laziness não paga nada. Passou a ser uma `Column`
simples, que se mede pelo conteúdo, não tem janela e não corta nada; o deslize do ecrã é o único
que existe. Depois da mudança os três cartões medem **164 px cada**.

**O que se perdeu:** o `animateItemPlacement`, que só existe em listas lazy. Não faz falta e é o
mesmo motivo já registado no defeito C da [por-fazer](../por-fazer.md): a lista **nunca reordena
com o ecrã à frente**. A identidade estável das linhas ficou num `key(...)`, que é o que a
cascata precisa para não repetir a entrada.

### A lista do top tinha chaves por posição

`itemsIndexed(topScores.take(3))` **sem `key`**: a `LazyColumn` identificava cada linha pelo
índice. Quando uma pontuação nova entrava no top, as linhas ficavam no lugar e trocavam de
*conteúdo* — lê-se como um pisca, não como uma lista a reordenar. Pior, `cascadeIn` refaz a
animação de entrada sempre que o `index` muda.

Agora:

- `key = { _, e -> "${e.uid}:${e.timestamp}" }` — o `uid` sozinho não chega, o mesmo jogador pode
  ter várias entradas no top.
- `key(chave) { ... }` à volta de cada linha, agora que a lista é uma `Column` simples.
- O escalão da cascata é fixado à identidade da linha (`remember(chave) { index + 4 }`), para uma
  linha que apenas desce de posição não repetir a entrada.

**O que foi observado:** capturas em rajada durante a construção do pódio mostram as três linhas a
assentar em 1492/1698/1904 px e a ficarem lá — sem salto depois de compostas. Forçou-se uma
pontuação nova a entrar no top 3 (2370 e depois 2380, empurrando o antigo #3 para fora) e a lista
seguinte desenha a ordem nova correctamente.

**O que não foi observado, e porquê:** uma reordenação *ao vivo*, com a lista já no ecrã. Não é
alcançável neste ecrã — `sessionOnly()` limpa `topScores` a cada partida e `loadTopScores()` corre
uma única vez por pódio, por isso a lista só faz vazio → preenchido. As chaves e o
`animateItemPlacement` continuam a ser a correção certa (é o que dá identidade estável às linhas e
o que faz falta no dia em que a lista passar a actualizar-se em directo), mas a animação de
reordenação em si não teve como ser filmada.
