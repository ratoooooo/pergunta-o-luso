# Pergunta ó Luso

Jogo de trivia sobre cultura portuguesa para Android — história, geografia, desporto,
gentílicos e cultura geral, a solo ou contra outros jogadores em tempo real.

Escrito em **Kotlin** com **Jetpack Compose**, apoiado em **Firebase Authentication** e
**Firebase Realtime Database**. Mais de **1090 perguntas** de escolha múltipla e
Verdadeiro/Falso, organizadas por categoria e por três níveis de dificuldade.

> Em desenvolvimento ativo. **Ainda não publicado na Google Play Store.**

---

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Kotlin 2.2 |
| UI | Jetpack Compose (Material 3) |
| Arquitetura | MVVM — `ViewModel` + `StateFlow`, repositórios por domínio |
| Autenticação | Firebase Auth (anónima + email/palavra-passe, com *linking* de conta) |
| Base de dados | Firebase Realtime Database (listeners em tempo real, transações, `onDisconnect`) |
| Build | Gradle 9.4.1 · AGP 9.2 · compileSdk 36 · **minSdk 26** |

---

## Funcionalidades

### Modos de jogo
- **Clássico** — 10 perguntas, pontos por rapidez e por sequência de acertos.
- **Caótico** — 10 perguntas com eventos-surpresa a cada ronda (tempo a metade, roubo de
  pontos, tudo-ou-nada…).
- **Eliminatórias** — sobrevivência: um erro e acabou.

### Multiplayer em tempo real
- **1x1**, **2x2** (por equipas) e **Grupo** (todos contra todos).
- **Matchmaking aleatório** por fila atómica, agrupando jogadores por formato + categoria + modo.
- **Desafio direto a um amigo** (1x1): convite em tempo real com aceitar/recusar e expiração
  automática ao fim de 45 s.
- **Sincronização lockstep**: o início de cada pergunta é carimbado pelo servidor, por isso
  todos os dispositivos contam o mesmo tempo; deteção de desistência via `onDisconnect`.
- Salas de espera dedicadas por formato, com lugares a preencherem-se à medida que entram jogadores.

### Progressão e social
- **XP e níveis** com barra de progresso no Início, no Perfil e no Ranking.
- **Conquistas** (15) ligadas a dados reais do perfil: primeira vitória, sequência, partida
  perfeita, mestre de cada categoria, marcos de jogos, vitórias em cada formato multijogador
  e marco de nível.
- **Avatares** com símbolos culturais portugueses desenhados à mão em vetor — azulejo, pastel
  de nata, caravela, farol, sardinha, Galo de Barcelos, Os Lusíadas, guitarra portuguesa,
  calçada e coração de Viana (iniciais do nome como alternativa).
- **Amigos**: pesquisa de jogadores por nome, pedidos de amizade (enviar / aceitar / recusar /
  cancelar) e lista bidirecional.
- **Ranking** segmentado por modo (mais vitórias, mais pontos, melhor recorde).
- **Histórico** das últimas partidas e **perfil** com estatísticas globais e por modo.
- Contador de **jogadores online** em tempo real.

---

## Sistema visual

Estilo *sticker* / banda desenhada: cores planas, contornos grossos e sombras duras
(sem desfoque), aplicado de forma consistente a todos os ecrãs.

| Elemento | Valor |
|---|---|
| Fundo | Creme `#EAE6DD` |
| Cartões | Lavanda `#F6F1FB` |
| Contorno | Tinta `#1A1523`, 3 dp |
| Sombra | Deslocamento sólido para baixo/direita, sem blur |
| Cantos | 18–36 dp |
| Paleta | Roxo `#6C3CE0` · Dourado `#FFC93C` · Coral `#FF6B5B` · Teal `#2FBF9F` · Azul `#3D6EE8` |
| Tipografia | **Fredoka** (títulos) + **Manrope** (texto), como *variable fonts* |
| Ícones | Material Symbols Rounded (sem emojis) |

O sistema vive em `ui/theme/Sticker.kt` (`stickerBlock`, `stickerCircle`, `stickerDashed`).

---

## Estrutura do projeto

```
app/src/main/java/com/starforge/app/
├── MainActivity.kt          # entrada, aplica o tema
├── AuthGate.kt              # sign-in silencioso no arranque
├── data/                    # repositórios + modelos (sem Compose)
│   ├── AuthRepository.kt          ProfileRepository.kt   Profile.kt
│   ├── QuestionRepository.kt      Question.kt            CategoryRepository.kt
│   ├── ScoreRepository.kt         Progressao.kt          PresenceRepository.kt
│   ├── FriendsRepository.kt       ChallengeRepository.kt
│   └── MultiMatchRepository.kt
├── game/                    # ViewModel + ecrãs
│   ├── GameViewModel.kt  GameApp.kt  MainScaffold.kt
│   ├── GameMode.kt  Difficulty.kt  ChaoticEvent.kt  Scoring.kt
│   ├── (ecrãs) Start, Category, Mode, Question, Podium, Ranking,
│   │           History, Profile, Login, Register, Format,
│   │           Friends, FriendSearch, Achievements
│   ├── avatar/            # símbolos portugueses em vetor + seletor
│   └── multi/             # matchmaking, salas e jogo multijogador
└── ui/theme/                # design system sticker, cores, tipografia, navegação
```

Outros ficheiros relevantes:

- `database.rules.json` — regras de segurança da Realtime Database (validação de tipos,
  escrita restrita ao próprio `uid`, limites de pontuação).
- `GAME_DESIGN.md` — registo de todas as decisões de design e das fases de desenvolvimento.

---

## Correr o projeto localmente

### Pré-requisitos
- Android Studio (versão recente) e JDK 17+
- Um dispositivo ou emulador com **Android 8.0 (API 26)** ou superior

### 1. Clonar

```bash
git clone https://github.com/ratoooooo/pergunta-o-luso.git
```

### 2. Criar o teu `google-services.json`

**O ficheiro `app/google-services.json` não está no repositório** (contém a chave de API e o
id da app do projeto Firebase original). Cada programador gera o seu:

1. Cria um projeto em [console.firebase.google.com](https://console.firebase.google.com).
2. Adiciona uma app **Android** com o package name `com.starforge.app`.
3. Descarrega o `google-services.json` e coloca-o em `app/google-services.json`.
4. No projeto Firebase, ativa:
   - **Authentication** → métodos *Anónimo* e *Email/Palavra-passe*;
   - **Realtime Database** (começa em modo de teste ou publica as regras deste repo).

### 3. Publicar as regras e semear perguntas

```bash
npx -y firebase-tools@latest deploy --only database --project <o-teu-projeto>
```

A base de dados espera perguntas em `/categorias/{categoria}/perguntas`, cada uma no formato:

```json
{
  "pergunta": "Quem foi o primeiro rei de Portugal?",
  "opcoes": ["D. Afonso Henriques", "D. João I", "D. Dinis", "D. Manuel I"],
  "respostaCorreta": "D. Afonso Henriques",
  "dificuldade": "facil"
}
```

Perguntas de Verdadeiro/Falso usam o mesmo formato, com `opcoes` a conter apenas
`["Verdadeiro", "Falso"]`.

### 4. Compilar

```bash
./gradlew :app:assembleDebug
```

---

## Estado atual

Em **desenvolvimento ativo**. O jogo está funcional de ponta a ponta (solo e multijogador,
testado em emuladores múltiplos), mas **ainda não foi publicado na Google Play Store**.

Por fazer, entre outros: ecrã de entrada em sala por código, sistema de convites para 2x2 e
Grupo, e revisão final de conteúdo de algumas perguntas (ver `GAME_DESIGN.md`).

---

## Créditos

O desenho de regras e algumas ideias de mecânica (curva de XP e níveis, estrutura de modos de
jogo) têm origem num projeto anterior do mesmo autor, o **BrainBrawl** — usado apenas como
referência de comportamento. **Nenhum código foi reutilizado**; este projeto foi escrito de raiz.

Perguntas: elaboradas de raiz e complementadas a partir de cartas de trivia de cultura geral
portuguesa, com verificação factual documentada em `GAME_DESIGN.md`.
