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
