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
