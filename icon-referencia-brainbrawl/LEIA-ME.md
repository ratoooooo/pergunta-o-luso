# Ícones do BrainBrawl — cópia de referência

Cópia **intacta** dos assets, para edição manual. Nada está integrado no Pergunta ó Luso: o
`app/src/main/res/mipmap-*` deste projeto não foi tocado, e não se fez build.

## Duas pastas — atenção a qual é qual

### `ATUAL-avatar_14/` ← é este

O `AndroidManifest.xml` do BrainBrawl diz, sem margem para dúvida:

```xml
android:icon="@mipmap/avatar_14"
android:roundIcon="@mipmap/avatar_14"
```

19 ficheiros: conjunto completo de densidades (mdpi→xxxhdpi), os XML do ícone adaptativo, o
`avatar_14_background.xml` e o export 512×512 para a loja.

### `OBSOLETO-caravela/` ← não é

Os `ic_launcher*` (caravela azul e amarela, *flat*). **Não são referenciados pelo manifest** —
ficaram no projeto sem uso. Guardados só para registo.

## O verde no 512×512 não faz parte do desenho

`ATUAL-avatar_14/loja/avatar_14-playstore.png` aparece com um **fundo verde aos quadrados**.
Isso é o `avatar_14_background.xml`, que nunca foi editado — continua a ser o placeholder do
Android Studio (`#3DDC84`, o verde do robô, mais a grelha `#33FFFFFF`). O export para a loja
achatou o placeholder para dentro da imagem.

**O desenho verdadeiro é só o `foreground`**, sobre transparente:

```
ATUAL-avatar_14/res/mipmap-xxxhdpi/avatar_14_foreground.webp   ← melhor ponto de partida
```

Ou seja: falta na mesma desenhar um `background` a sério. O verde é para deitar fora.

## Estilo e paleta

Caravela portuguesa desenhada à mão, com **contorno de tinta grosso**, vela creme com um ponto
de interrogação, casco vermelho-escuro com tábuas marcadas, bandeirinha vermelha no mastro.
Muito mais ilustrado do que a versão *flat* obsoleta.

| Cor | Hex aprox. | Uso |
|---|---|---|
| Creme | `#E0D2A8` | vela |
| Tinta castanho-escura | `#1C0E0E` / `#381C0E` | contornos |
| Vermelho-tijolo escuro | `#621C1C` | casco, bandeira, ponto de interrogação |

## Como bate com o design Sticker

Melhor do que a caravela *flat*, mas ainda não bate.

| Sticker (atual) | avatar_14 |
|---|---|
| Contorno de tinta grosso | **tem** — é o ponto forte |
| Tinta `#1A1523` (quase preta, fria) | `#1C0E0E` (castanha, quente) |
| Creme `#EAE6DD` | `#E0D2A8` — mais amarelado |
| Coral `#FF6B5B` | `#621C1C` — vermelho muito mais escuro |
| Roxo `#6C3CE0` / Dourado `#FFC93C` / Teal `#2FBF9F` | não existem |
| Sombra dura deslocada | não tem |

**O traço é compatível; a paleta não.** O contorno grosso já é a linguagem certa. O que
desafina são os tons: tudo puxa para castanho e terra, enquanto o Sticker é roxo, dourado e
teal saturados sobre creme frio.

Caminho mais curto: manter o desenho e o contorno, e **recolorir** — casco para Coral, vela
para Creme do sistema, contorno para Ink `#1A1523`, e um fundo Roxo ou Dourado em vez do
verde. O ponto de interrogação pode ficar Ink.

## O que falta gerar depois de decidires

- `background` próprio (o verde é placeholder)
- Camada `monochrome` para os ícones temáticos do Android 13+ — a app nunca teve
- 512×512 limpo para a ficha da Play Store
- Gráfico de destaque 1024×500, também obrigatório e inexistente
