# Pergunta ó Luso

Jogo de trivia sobre cultura portuguesa para Android — história, geografia, desporto,
gentílicos e cultura geral, a solo ou contra outros jogadores em tempo real.

Escrito em **Kotlin** com **Jetpack Compose**, apoiado em **Firebase Authentication**,
**Firebase Realtime Database** e um **servidor Node** dedicado ao multijogador.
Mais de **1650 perguntas** de escolha múltipla e Verdadeiro/Falso, organizadas por
categoria e por três níveis de dificuldade.

> Em desenvolvimento ativo. **Ainda não publicado na Google Play Store.**

---

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Kotlin 2.2 |
| UI | Jetpack Compose (Material 3) |
| Arquitetura | MVVM — `ViewModel` + `StateFlow`, repositórios por domínio |
| Autenticação | Firebase Auth (anónima + email/palavra-passe, com *linking* de conta) |
| Base de dados | Firebase Realtime Database (listeners em tempo real, transações) |
| Servidor multijogador | Node.js (`ws` sobre TLS) num VPS — decide correcção, pontua e escreve `/scores` |
| Build | Gradle 9.4.1 · AGP 9.2 · compileSdk 36 · **minSdk 26** |

---

## Funcionalidades

### Modos de jogo
- **Clássico** — 10 perguntas, pontos por rapidez e por sequência de acertos.
- **Caótico** — 10 perguntas com eventos-surpresa a cada ronda (tempo a metade, roubo de
  pontos, tudo-ou-nada…).
- **Eliminatórias** — sobrevivência: um erro e acabou.

### Multiplayer em tempo real
- **1x1**, **2x2** (por equipas) e **Grupo** — todos contra todos, joga-se com **4 a 10**
  jogadores (10 é a capacidade máxima da sala; a partir de 4 já se pode arrancar à mão).
- **Servidor dedicado** (`servidor/`): a partida corre num processo Node num VPS, que decide
  se a resposta está certa, calcula a pontuação e escreve o resultado em `/scores` como
  `pol-servidor`. O cliente comunica por WebSocket (`wss://`, TLS terminado pelo Caddy).
- **Matchmaking por lobby**: entra-se numa sala em espera compatível (mesma categoria e modo)
  ou cria-se uma nova; "ver outras salas abertas" permite trocar antes de a partida começar.
  Lobbies, salas e salas privadas vivem **na memória do servidor** (já não na RTDB).
- **Salas privadas por código**: cria-se uma sala fechada (formato + quiz da comunidade
  escolhido) e convida-se por um código de 4 dígitos.
- **Desafio direto a um amigo** (1x1): convite em tempo real com aceitar/recusar e expiração
  automática ao fim de 45 s. Os convites viajam pela RTDB (`/convites`).
- **Sincronização lockstep**: o início de cada pergunta é carimbado pelo servidor no seu
  relógio, e o cliente recalcula o *offset* a cada pergunta; deteção de desistência pelo
  servidor (heartbeat + timeout), com vitória por *walkover* quando só resta um jogador
  activo.

### Quizzes da Comunidade
- Qualquer jogador pode **criar um quiz** (título, categoria, dificuldade, perguntas de
  escolha múltipla ou Verdadeiro/Falso), publicá-lo e jogá-lo a solo ou em sala privada.
- Avaliação por estrelas e **denúncia** — um quiz oculta-se automaticamente ao fim de 3
  denúncias, para revisão manual.
- Filtro de linguagem imprópria aplicado no repositório (não só no ecrã), com lista pensada
  para não bloquear falsos positivos comuns em português ("cabra-cega", "burro", "puto"…).

### Progressão e social
- **XP, níveis e patentes**: seis patentes (Grumete → Descobridor) derivadas do nível, com
  barra de progresso no Início, no Perfil e no Ranking.
- **Conquistas** (15) ligadas a dados reais do perfil: primeira vitória, sequência, partida
  perfeita, mestre de cada categoria, marcos de jogos, vitórias em cada formato multijogador
  e marco de nível.
- **Avatares** com símbolos culturais portugueses desenhados à mão em vetor — azulejo, pastel
  de nata, caravela, farol, sardinha, Galo de Barcelos, Os Lusíadas, guitarra portuguesa,
  calçada e coração de Viana (iniciais do nome como alternativa).
- **Amigos**: pesquisa de jogadores por nome, pedidos de amizade (enviar / aceitar / recusar /
  cancelar) e lista bidirecional.
- **Ranking** com duas dimensões — por modo (mais vitórias, mais pontos, melhor recorde) e por
  formato multijogador (mais vitórias, mais jogos, % de vitórias).
- **Histórico** das últimas partidas (filtrável por formato) e **perfil** com estatísticas
  globais e por modo.
- Contador de **jogadores online** em tempo real.
- **Som e retorno háptico** nos momentos de jogo (resposta certa/errada, vitória/derrota,
  conquista desbloqueada, subida de nível) — respeita o volume e o modo de silêncio do
  telemóvel.
- **Eliminação de conta** dentro da própria app (perfil, histórico, amigos e quizzes),
  exigida pela Google Play para qualquer app que permita criar conta.

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
app/src/main/java/com/ratoooooo/perguntaoluso/
├── MainActivity.kt          # entrada, aplica o tema, inicializa o som
├── AuthGate.kt              # sign-in silencioso no arranque
├── audio/                   # SoundEffects (SoundPool) — sons + retorno háptico
├── data/                    # repositórios + modelos (sem Compose)
│   ├── AuthRepository.kt          ProfileRepository.kt     Profile.kt
│   ├── AccountDeletionRepository  QuestionRepository.kt     Question.kt
│   ├── CategoryRepository.kt      ScoreRepository.kt        Progressao.kt
│   ├── Patente.kt                 PresenceRepository.kt
│   ├── FriendsRepository.kt       ChallengeRepository.kt
│   ├── CustomCategory.kt          CustomCategoryRepository  ProfanityFilter.kt
│   └── multi/MultiSocketClient.kt # transporte WebSocket para o servidor
├── game/                    # ViewModel + ecrãs
│   ├── GameViewModel.kt  GameApp.kt  MainScaffold.kt
│   ├── GameMode.kt  Difficulty.kt  ChaoticEvent.kt  Scoring.kt
│   ├── AnswerOption.kt  ResultStats.kt  # componentes partilhados solo + multijogador
│   ├── (ecrãs) Start, Category, Mode, Question, Podium, Ranking,
│   │           History, Profile, Login, Register, Format,
│   │           Friends, FriendSearch, Achievements, CustomCategories
│   ├── avatar/            # símbolos portugueses em vetor + seletor
│   └── multi/             # UI e ViewModel do multijogador
└── ui/theme/                # design system sticker: cores, tipografia, separadores,
                              # diálogos, animações, navegação
```

```
servidor/                      # servidor Node da partida ao vivo
├── servidor.js                # ponto de entrada (ws + Firebase Admin)
├── PROTOCOLO.md               # fonte única do formato das mensagens
├── deploy/INSTALAR.md         # DNS, TLS (Caddy), systemd, ufw
└── __tests__/                 # testes unitários (64)
```

Outros ficheiros relevantes:

- `database.rules.json` — regras de segurança da Realtime Database (validação de tipos,
  escrita restrita ao próprio `uid`, limites de pontuação, schemas fechados por nó). As rules
  recusam `/scores` com formato multijogador de qualquer cliente — só `pol-servidor` escreve.
- `GAME_DESIGN.md` — registo exaustivo de todas as decisões de design e das fases de
  desenvolvimento (30+ fases), incluindo o que foi tentado e não resultou.
- `icon-build/`, `icon-fonte/`, `icon-referencia-brainbrawl/` — material de proveniência do
  ícone da app; não são lidos pelo build.
- `keystore.properties.example` — modelo para a assinatura de release (o ficheiro real e o
  `.jks` nunca são commitados).

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
2. Adiciona uma app **Android** com o package name `com.ratoooooo.perguntaoluso`.
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

Em **desenvolvimento ativo**. O jogo está funcional de ponta a ponta (solo, multijogador e
quizzes da comunidade, testado em emuladores múltiplos), com auditoria de segurança feita às
rules (duas rondas de pentest, incluindo teste ao vivo com partidas reais), assinatura de
release configurada (Play App Signing) e ícone próprio — mas **ainda não foi publicado na
Google Play Store**.

Por fazer, entre outros (ver `docs/vault/por-fazer.md`):
- Gerar o `.aab` de submissão e o gráfico de destaque 1024×500 da ficha da loja.
- A Play Store exige também uma página web de pedido de eliminação de conta (além do fluxo
  já implementado na app) para o formulário Data Safety.
- Desafio direto a amigos continua limitado a 1x1 (2x2/Grupo exigiriam um lobby com vários
  convites em paralelo).
- Revisão de algumas perguntas cuja resposta não foi possível confirmar (lista em
  `GAME_DESIGN.md`, Fase 17).

---

## Créditos

O desenho de regras e algumas ideias de mecânica (curva de XP e níveis, estrutura de modos de
jogo) têm origem num projeto anterior do mesmo autor, o **BrainBrawl** — usado apenas como
referência de comportamento. **Nenhum código foi reutilizado**; este projeto foi escrito de raiz.

Perguntas: elaboradas de raiz e complementadas a partir de cartas de trivia de cultura geral
portuguesa, com verificação factual documentada em `GAME_DESIGN.md`.
