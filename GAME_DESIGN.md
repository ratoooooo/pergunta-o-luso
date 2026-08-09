# Pergunta ó Luso — Design & Game Rules

Solo trivia game (Android, Kotlin, Jetpack Compose). Optional accounts (anonymous by
default, email/password sign-up available), no multiplayer. Firebase Realtime Database
+ Firebase Auth. Sticker/comic visual style.

## Firebase

- Project: `supermercado-tia-lucia` (reused existing empty project; new-project creation
  was blocked by the account's Firebase project quota).
- RTDB instance: `supermercado-tia-lucia-default-rtdb`.
- Auth: Anonymous + Email/Password providers, enabled via `firebase.json`
  (`auth.providers.anonymous`, `auth.providers.emailPassword`) + `firebase deploy --only auth`.
- Config: `app/google-services.json`.
- **`applicationId` / `namespace`: `com.ratoooooo.perguntaoluso`** (ver Fase 21). É a
  identidade permanente da app na Play Store — não pode mudar depois de publicada.
- App Android no Firebase: "Pergunta o Luso",
  `1:516301571634:android:74f3383aba01795aa2de2b`. A app antiga ("StarForge",
  `…:7f4e9ca0f89e0d7da2de2b`, package `com.starforge.app`) continua registada no projeto e
  ainda aparece no `google-services.json` — deliberado, para poder ser confirmada antes de
  remover.

### Authentication

`AuthGate` (in `MainActivity`/`AuthGate.kt`) wraps the whole app and blocks any
screen from rendering until `AuthRepository.ensureSignedIn()` resolves:

- If `FirebaseAuth.currentUser` already exists (session persisted by the SDK across
  restarts — nothing custom stored), it's used immediately.
- Otherwise `signInAnonymously()` runs before the first frame of real UI. It's fast
  enough that the player never sees a distinct loading screen — just the normal
  cream background for a moment, then Start.
- Every player always has a uid; playing never requires an account.

**Accounts are optional and opt-in** (`AuthRepository`, `LoginScreen`, `RegisterScreen`):

- From Start, `LOGIN / CRIAR CONTA` opens the login screen; from there the player can
  register or continue anonymously ("Entrar sem conta").
- **Register** (`registerWithEmail`): if the current session is anonymous, the
  email/password credential is **linked** to it via `linkWithCredential`, so the uid
  is preserved and all anonymous progress (scores + aggregated profile) carries over.
  If not anonymous, a fresh account is created. Password requires ≥8 chars and must
  match the confirmation field; the chosen `nome` is written to `/jogadores/{uid}/nome`.
- **Login** (`loginWithEmail`): signs into an existing account (switches uid to that
  account's; the throwaway anonymous session is discarded).
- **Sign out** (`signOutToAnonymous`): signs out and immediately re-establishes a fresh
  anonymous session so play still works. Start shows `LOGIN / CRIAR CONTA` only while
  anonymous; registered-player sign-out lives inside Perfil, as a smaller secondary action
  at the end of the profile screen.

### RTDB paths the app actually uses

| Path | Access | Purpose |
|------|--------|---------|
| `/categorias/{categoria}/perguntas` | read (public) | 964 seeded trivia questions across 7 categories |
| `/scores/{pushId}` | read + write (**signed-in only**) | raw record per finished game |
| `/jogadores/{uid}` | read (public), write (own uid only) | aggregated per-player profile + stats |

Score record schema (exactly the fields written):

```
uid:          String   // auth.uid of the writer — must match, enforced by rules
modo:         String   // "classico" | "caotico" | "eliminatorias"
categoria:    String
score:        Number   // points
correctCount: Number   // correct answers
total:        Number   // questions faced
timestamp:    Number
```

`database.rules.json` mirrors this exactly:
- `categorias`: public read, no write (unchanged — it's static quiz content).
- `scores`: `.read` and `.write` both require `auth != null`.
- Each score child validates it contains **only** those seven fields
  (`$other: {".validate": false}`), and specifically
  `uid: auth != null && newData.val() === auth.uid` — a signed-in player can only
  ever write a record carrying their own uid, never someone else's.
- `.indexOn: "score"` for the podium query.

Older score records (from before `uid` existed) remain readable to signed-in users;
validation only applies to new writes.

### Aggregated profile — `/jogadores/{uid}`

One node per player, accumulated across every finished game (a transaction folds each
game's result into the counters — `ProfileRepository.updateAfterGame`). This is what
the Start summary and the Ranking read; `/scores` stays as the raw per-game log.

```
nome:            String   // display name (set at register; "" for anonymous → shown as "Convidado")
atualizadoEm:    Number
jogos:           Number   // global games played
pontos:          Number   // global cumulative points
respostasCertas: Number   // global correct answers  (taxaAcertos = certas / totais)
respostasTotais: Number   // global questions faced
vitorias:        Number   // global wins (see criteria below)
recorde:         Number   // best single-game score
maxStreak:       Number   // best correct-streak ever
modos: {
  classico|caotico|eliminatorias: {
    jogos, pontos, respostasCertas, respostasTotais, vitorias, recorde
  }
}
```

Rules for `/jogadores`:
- `.read: true` (public) — the Ranking needs to read every player's aggregate. Only the
  chosen `nome` and stats are exposed; e-mail is never stored here.
- `$uid .write: auth != null && auth.uid === $uid` — a player can only ever write their
  own node, never anyone else's.
- Field-level type validation + `$other: {".validate": false}` at both the `$uid` and
  `modos/$modo` levels, so only the known fields above are accepted.

### Victory criteria (per mode) — and why

Decided and documented here because "win" isn't intrinsic to a solo quiz:

- **Clássico** and **Caótico**: win = **≥ 70 % correct** (≥ 7 of 10). Both modes always
  run 10 questions, so "completing the round" is trivial and wouldn't distinguish
  merit — a 70 % accuracy bar does.
- **Eliminatórias**: win = **survive all 20** (not eliminated). This mode already has a
  natural, meaningful win/lose condition, so it's used directly.

`GameViewModel.didWin` implements exactly this; the win increments `vitorias` (global +
per-mode) in the aggregate.

### Ranking categories (per mode) — and why

The Ranking screen is segmented by mode; each mode shows three leaderboards, all sourced
from the aggregated profiles (never the raw `/scores`, to avoid one player appearing
many times):

1. **Mais vitórias** — consistency / how often they clear the win bar.
2. **Mais pontos** — volume / dedication (cumulative points in that mode).
3. **Melhor recorde** — peak (best single-game score in that mode).

Three deliberately different axes — consistency, volume, peak — so the boards aren't
redundant. Each lists the top 5 with entries whose value is > 0.

### Verified (2026-07-23)

Auth + uid on `/scores` (earlier phase):
- Fresh install → anonymous sign-in completed silently before Start appeared (logcat
  `Notifying auth state listeners about user (6WBWb7…)`, no visible loading, no crash).
- Full game → `/scores` record carried the matching `uid`.
- `curl` write to `/scores.json` with no token → `Permission denied` (read too);
  valid token but **different uid** → denied; valid token but **missing uid** → denied;
  valid token + matching uid → succeeded (disposable record, deleted).

Accounts + profile + ranking (this phase, uid `6WBWb7…` = "Dinis"):
- Played anonymous game → `/jogadores/{uid}` aggregate created
  (jogos 1, pontos 600, recorde 600, 3/10, maxStreak 2, vitórias 0).
- **Register while anonymous → linkWithCredential**: same uid preserved, `nome:"Dinis"`
  added, Start showed the carried-over stats (600 pts / 30 % / 1 jogo). Confirmed in
  RTDB that the 2 existing `/scores` records still belong to that uid. **No progress lost.**
- Played more games; aggregate accumulated correctly to jogos 3, pontos 3200,
  recorde 1880, 14/30 (46 %), maxStreak 7, and a 7/10 game registered
  **vitórias 1** ("Vitória!" podium) — victory criteria working.
- Ranking (Clássico) listed Dinis #1 in all three boards (1 vit / 3200 pts / 1880 pts);
  Caótico tab correctly showed "Ainda sem dados.".
- Sign out → "Convidado" (fresh anon, 0 stats); log back in with the account → Dinis
  profile restored (3200 pts / 46 % / 3 jogos).
- `/jogadores` rules by `curl`: public read OK; anonymous attacker writing to another
  uid's node (full node and single `nome` field) → `Permission denied`; writing to own
  node → OK; unauthenticated write → denied. (Test node deleted afterwards.)

Note on manual QA: the emulator's default IME opened a stylus-handwriting overlay that
dropped the first character after focus changes (caused one "as palavras-passe não
coincidem" — which correctly blocked submission). Disabling
`settings put secure stylus_handwriting_enabled 0` restored a normal keyboard; the
retry with matching passwords succeeded. This is a test-harness quirk, not an app bug.

## Flow

```
Start ─┬─ JOGAR → Formato ─┬─ Solo  → Categoria → Modo → Pergunta → Pódio
       │                   └─ 1x1/2x2/Grupo → Categoria → Modo → Sala de espera
       │                                       → Encontrado! → Pergunta → Pódio
       ├─ RANKING → Ranking
       ├─ HISTÓRICO → Histórico
       ├─ (cartão de perfil) → Perfil
       └─ LOGIN / CRIAR CONTA → Login ⇄ Registar
```

`JOGAR` opens **Escolher Formato** (Solo / 1x1 / 2x2 / Grupo). **All formats now go through
the same Categoria → Modo selection** — multiplayer is no longer locked to a fixed
category/mode. For multiplayer the mode step then leads to matchmaking (a "sala de espera"),
a brief **Encontrado!** reveal of the players/teams (~2.5 s), and the game.

- Multiplayer offers **Clássico + Caótico only**; **Eliminatórias is solo-only** (its
  first-wrong-ends survival format doesn't map onto a synced multiplayer round).
- The Start screen also exposes **HISTÓRICO** and a tappable **profile card → Perfil**.

Back navigation:
- Formato / Login / Registar / Ranking: back → Start (Registar back → Login)
- Categoria: back → Start
- Modo: back → Categoria
- Pódio (solo): back (home) → Start; "JOGAR NOVAMENTE" → Categoria

The Start screen shows a profile card (avatar with initials, greeting/`nome`, and three
stat chips: pontos, taxa de acertos, jogos), the live `A JOGAR AGORA` presence chip, JOGAR,
HISTÓRICO, and LOGIN / CRIAR CONTA only for anonymous users. Registered-player sign-out is
kept out of Início and appears only in Perfil, so it does not compete visually with play
actions. JOGAR is the single gold primary action; HISTÓRICO uses Purple.

## Categories

7 categories live in RTDB. Two are hidden from the picker (data untouched):
`Património Português`, `Gastronomia Portuguesa`. Visible: Cultura Geral, Desporto,
Gentílicos, Geografia, História.

Each visible category has a fixed, distinct colour (see `ui/theme/Color.kt`) **and a fixed
icon** (`ui/theme/CategoryMeta.kt`), reused on the category picker, as a chip on the mode
screen, and in the in-game question header:

| Categoria | Cor | Ícone |
|-----------|-----|-------|
| Cultura Geral | Purple | Lightbulb |
| Desporto | Teal | SportsSoccer |
| Gentílicos | Coral | Groups |
| Geografia | Gold | Public |
| História | **Azul `#3D6EE8`** | HistoryEdu |

História's colour was changed to a **royal blue** — the previous value read as too close to
Teal (Desporto), and a plain Ink/black is used elsewhere; the blue is clearly distinct from
the other four category colours.

## Difficulty

Each question carries `dificuldade` = `facil | medio | dificil`. Used two ways:

1. **Progression ramp** — questions are shuffled *within* each difficulty tier, then
   concatenated `facil → medio → dificil`. Order still varies each game but the round
   ramps from easy to hard. (`QuestionRepository.buildProgression`, ~⅓ per tier.)
2. **Points multiplier** — `facil ×1.0`, `medio ×1.5`, `dificil ×2.0` applied to the
   base points of a correct answer.

Rationale: this uses the field on every question in every mode (not just a one-off
filter), gives a satisfying easy→hard arc, and rewards harder answers — which also
makes the Eliminatórias ramp meaningfully tougher as you survive.

## Timer & Scoring

Each question has a 15s countdown (halved to 7.5s under the Caótico
`velocidade_maxima` event). Scoring is adapted from BrainBrawl's solo `ScoreService`
+ `ChaoticEventService` (behaviour only — no code reused). See `game/Scoring.kt`.

Correct answer:
- `base = remainingSeconds * 10`
- Caótico `pergunta_dupla` doubles base
- `× difficulty multiplier`
- `+ streak bonus`: 2 in a row → +50, 3 → +75, 4+ → +100
- Caótico `roubo` → +50, `tudo_ou_nada` → +100

Wrong answer / timeout:
- 0 points (streak resets), except Caótico `tudo_ou_nada` → −50

Running total floored at 0.

## Modes

Adapted from BrainBrawl solo modes (`GAMEPLAY_RULES.md` in the BrainBrawl repo).

| Mode | Questions | Ends on first wrong | Events |
|------|-----------|---------------------|--------|
| Clássico | 10 | no | no |
| Caótico | 10 | no | yes |
| Eliminatórias | 20 | **yes** | no |

- **Clássico**: standard round, scoring above.
- **Caótico**: one deterministic event per question (`ChaoticEvent.forIndex`), shown
  as a banner. Events cycle: `pergunta_dupla`, `velocidade_maxima`, `roubo`,
  `tudo_ou_nada`.
- **Eliminatórias**: one wrong answer (or a timeout) ends the game immediately →
  "Eliminado!" podium. Loads a larger pool (20) so survival is the challenge; the
  difficulty ramp makes later questions harder.

## Answer feedback

On answering, options recolour:
- correct **and** picked → Teal
- correct but you picked wrong (or timed out) → Gold (reveals the right answer)
- your wrong pick → Coral
- the remaining options → Neutral (muted), so exactly one/​two highlights stand out

A `+X pontos` / `−X pontos` delta appears under the options.

## Podium

Shows the current game's medal (category · mode, points, `X de Y certas`) and the top 5
scores from `/scores` ordered by points (`orderByChild("score").limitToLast(5)`),
labelled with category + mode.

## Multiplayer — Duelo 1x1

A real-time 2-player duel (`game/onevone/`, `data/Match1x1Repository.kt`). **Only 1x1** —
2x2 and Group modes from BrainBrawl are deliberately not ported. Reached from Start via
`DUELO 1x1`. Fixed category **Cultura Geral**, 10 questions, Clássico-style scoring.

Behaviour is adapted from BrainBrawl's 1x1 (studied, not copied). Notably, BrainBrawl's
old RTDB handshake bug was caused by **two `FirebaseDatabase` instances with mismatched
config** looping `credentials invalid`; this app uses a single `FirebaseDatabase.getInstance()`
everywhere, so that trap doesn't apply.

### Matchmaking

Single-slot atomic pairing (`joinQueue`), which is simpler and race-free vs. a full queue:
- An RTDB **transaction** on `/matchmaking/waiting`:
  - slot empty → I become the waiting player;
  - slot holds someone else → I clear it and become **host** of a new match with them.
- The transaction guarantees exactly one of two simultaneous joiners becomes host — no
  double-create.
- Host creates `/salas1x1/{salaId}` and writes `/matchmaking/notify/{guestUid} = salaId`;
  the guest listens there, then joins. `Cancelar` clears the slot / notify.

### Question sync (lockstep)

Both clients load the **same** questions from the room (host-authored). For each index:
- `syncQuestionStart` is a transaction on `perguntaInicios/{index}`: the first device to
  arrive stamps `ServerValue.TIMESTAMP`; both read the same start time.
- The timer is anchored to that shared start using `.info/serverTimeOffset`, so both
  countdowns are identical regardless of who arrived first.
- **Lockstep advance** (stricter than BrainBrawl): a client moves to the next question
  only when **both players have answered** the current one **OR the shared 15 s timer
  expires** — guaranteeing "same question, same time, same timer".

### Scoring & validation (who validates)

Scoring reuses the solo `Scoring` engine (base `remainingSeconds*10` × difficulty +
streak). There is **no server / Cloud Functions**, so scoring is client-authoritative —
same posture as BrainBrawl. The **RTDB rules are the validation floor**: a player can only
write **their own** `jogadores/{uid}` and `pontuacoes/{uid}`, and those are **capped at the
mathematical maximum** — `pontuacao ≤ 4000` (10 × the per-question max of 400 =
15 s ×10 ×2.0 difficulty +100 streak) and `respostasCertas ≤ 10`. This blocks a tampered
client from writing an impossible score or overwriting the opponent's. It does **not**
prevent a client from claiming a plausible-but-unearned score — full anti-cheat would need
a server, which is out of scope and documented as a known limitation.

### Disconnect / abandonment

On entering the room each client arms `onDisconnect` to set its `jogadores/{uid}/estado =
"off"`. If the opponent's state becomes `off` (abrupt disconnect) or `desistiu` (explicit
leave) mid-game before finishing, the remaining player wins by **walkover** → "Adversário
desistiu!". Verified detection in ~2 s after a force-stop.

### Podium 1x1

Both final scores side by side, winner highlighted (gold + trophy); tie and walkover cases
handled. `NOVO DUELO` recreates the match ViewModel via `key(...)`; `VOLTAR AO INÍCIO`
exits.

### RTDB paths & rules (1x1)

| Path | Access |
|------|--------|
| `/matchmaking/waiting` | auth read/write (transaction slot) |
| `/matchmaking/notify/{uid}` | read own uid; write auth (host publishes room id) |
| `/salas1x1/{salaId}/meta` | create-once (host writes questions + player ids, then immutable) |
| `/salas1x1/{salaId}/jogadores/{uid}` | write own uid only; `pontuacao ≤ 4000`, `respostasCertas ≤ 10` |
| `/salas1x1/{salaId}/pontuacoes/{uid}` | write own uid only; `≤ 4000` |
| `/salas1x1/{salaId}/perguntaInicios/{index}` | auth write, numeric (sync transaction) |

Splitting host data into `meta` (create-once) is what lets the rules lock each player's
score node to its owner without granting a blanket room-root write (RTDB `.write` cascades
down, so a room-level write rule would have defeated the per-player protection).

### Verified (2026-07-24, two emulators)

- Two devices (`rato` and `Convidado`, distinct uids) matched via `DUELO 1x1` and landed on
  the **same Q1** with a shared timer.
- Full duel played to the podium: both devices showed the **same final scores**
  (`rato 595` vs `Convidado 470`) with opposite labels (Vitória / Derrota); RTDB
  `/salas1x1/.../pontuacoes` matched (595 / 470, both `estado=terminado`).
- Score-cap rules by `curl`: own within-cap write (500) OK; over-cap (99999) denied;
  another uid's `pontuacoes` denied; `jogadores.pontuacao=5000` denied;
  `respostasCertas=50` denied.
- **Disconnect**: mid-game force-stop of one device → the other showed "Adversário
  desistiu!" (walkover win) in ~2 s.
- Note: stale `/matchmaking` + `/salas1x1` state from repeated manual runs caused a ghost
  pairing once; cleared the two nodes and the clean re-run passed. No auto-cleanup of
  finished/abandoned rooms is implemented yet (documented limitation; rooms are small).

## Multiplayer — 2x2 & Grupo

A **generalized N-player** system (`game/multi/`, `data/MultiMatchRepository.kt`) handles
both 2x2 and Grupo with the same code, parametrized by `MatchFormat`. The 1x1
implementation is separate and was left untouched. Fixed category **Cultura Geral**,
10 questions, Clássico-style scoring, same "single `FirebaseDatabase.getInstance()`"
discipline.

### Decisions (documented, since the BrainBrawl source was intent-only)

- **2x2** — 4 players → **2 teams of 2** (host splits the matched members first-2 = Equipa A,
  last-2 = Equipa B). **Scoring = team total** (sum of both players' points), following
  BrainBrawl; winner = higher team total, tie possible.
- **2x2 disconnect** — if **any** player leaves mid-game, **their team loses** (the other
  team wins by walkover). Chosen over "play 1-vs-2", which would make the team total unfair.
- **Grupo** — **all-vs-all**, individual score; podium is the ranking of all players by points.
  (BrainBrawl's group rooms are admin-driven with variable size; for a matchmaking flow a fixed
  size is the simplest fair choice.) **Correcção da Fase 31:** este parágrafo dizia "4 players"
  e estava errado — `MatchFormat.GRUPO` tem `players = 10` (alargado numa fase antiga sem o
  documento acompanhar). A Fase 30 já o tinha assinalado como desactualizado e mudou o portão
  de arranque para os membros reais da sala em vez de `format.players`; a correcção ao texto é
  que ficou por fazer.
- **Grupo disconnect** — the leaver is **excluded** from the lockstep "everyone answered"
  check (so the game never hangs) and is shown **last with a "— saiu" tag**; the remaining
  players finish and are ranked normally.
- **Lockstep advance** — move to the next question when **all still-active players have
  answered** the current one **OR the shared 15 s timer expires**.

### Matchmaking (N players)

Generalizes the 1x1 single-slot pattern to N: an atomic transaction on
`/matchmakingN/{format}/pending` accumulates waiting players and, once it reaches
`format.players`, atomically claims the first N as a match — that client becomes host
(race-free, no double-create). Host creates `/multisalas/{salaId}`, assigns teams (2x2),
and notifies each other member via `/matchmakingN/{format}/notify/{uid} = salaId`.

Sync (`perguntaInicios` transaction + `.info/serverTimeOffset`) and per-player writes are
identical to 1x1.

### Podium

- **2x2**: two team cards side by side (players + team total), winner gold + trophy;
  tie / walkover handled.
- **Grupo**: ranked list (#1 gold, own row teal, leavers muted with "— saiu").

### RTDB paths & rules (2x2 / Grupo)

Same security discipline as 1x1 (host-immutable `meta` create-once; per-player score nodes
locked to owner and capped at `≤ 4000` / `respostasCertas ≤ 10`):

| Path | Access |
|------|--------|
| `/matchmakingN/{format}/pending` | auth read/write (transaction slot) |
| `/matchmakingN/{format}/notify/{uid}` | read own uid; write auth |
| `/multisalas/{salaId}/meta` | create-once (host: membros, equipas, perguntas) |
| `/multisalas/{salaId}/jogadores/{uid}` | write own uid only; caps |
| `/multisalas/{salaId}/pontuacoes/{uid}` | write own uid only; `≤ 4000` |
| `/multisalas/{salaId}/perguntaInicios/{index}` | auth write, numeric |

### Verified (2026-07-24, four emulators)

- **2x2**: 4 devices matched into Equipa A `{5554,5558}` and Equipa B `{5556,5560}`, same
  synced questions. Full game → podium consistent on all 4: Equipa A **2755**
  (rato 1370 + Convidado 1385) beat Equipa B **0**; winners "A tua equipa ganhou!", losers
  "perdeu".
- **2x2 disconnect**: mid-game force-stop of one Equipa B player → both teams' podiums
  showed team A winning + "Uma equipa ficou incompleta porque um jogador saiu.", in ~2 s.
- **Grupo**: 4 devices all-vs-all, same synced questions; podium ranking identical on all 4
  (#1 Convidado 1580, #2 rato 1565, #3/#4 0), each device's own row + place label correct.
- **Grupo disconnect**: mid-game force-stop of one player → the **remaining 3 finished
  without hanging**; the leaver appeared **#4 "— saiu"**.
- Score-cap rules by `curl` on `/multisalas`: own within-cap (500) OK; over-cap (99999)
  denied; another uid's `pontuacoes` denied; unauthenticated pending write and room read
  denied.
- Note (same as 1x1): stale `/matchmakingN` + `/multisalas` state from repeated manual runs
  can cause a ghost pairing; each test run cleared both nodes first. No auto-cleanup of
  finished/abandoned rooms yet (documented limitation).

## UX overhaul & unified multiplayer flow (2026-07-24)

### 1x1 folded into the generalized system

The standalone 1x1 (`game/onevone/`, `Match1x1Repository`) was **removed** and 1x1 is now
`MatchFormat.ONE_V_ONE` (players = 2, no teams) inside the generalized `MultiMatch`. This
removed duplicated matchmaking/sync/disconnect code; the head-to-head podium wording
(Vitória / Derrota / Empate) is special-cased for `ONE_V_ONE`, everything else is shared.
The old `/matchmaking` + `/salas1x1` paths are unused (rules left in place, harmless).

### Matchmaking key = format + category + mode

Players are grouped so that everyone in a match is playing the **exact same game**. The queue
path is `/matchmakingN/{queueKey}/...` where
`queueKey = "${format.id}__${categorySlug}__${modo}"` (`MatchFormat.queueKey`). The `$format`
wildcard in the rules matches any key, so no rule change was needed. Two players who pick
different categories or modes simply land in different queues and never match.

### "Encontrado!" reveal

When the room fills, the ViewModel enters a `MATCHED` phase showing the paired players /
teams for `MATCHED_REVEAL_MS` (2.5 s) before the first question — a moment to recognise the
opponents instead of being dropped straight into Q1.

### Timer synchronisation — findings

The visible-timer sync was investigated and tightened:
- The shared per-question start time is stamped **once, on the server** (transaction on
  `perguntaInicios/{index}` with `ServerValue.TIMESTAMP`); every client reads the same value.
- Each client renders `remaining = duration − (serverNow − sharedStart)`, where
  `serverNow = System.currentTimeMillis() + serverTimeOffset`.
- **Fix applied:** `serverTimeOffset` is now re-read (`.info/serverTimeOffset`) **at the start
  of every question**, not once per match — a stale one-time offset was the main drift source.
- Because `remaining` is derived from the shared start, a device whose room update arrives
  late still shows the correct (already-lower) value immediately, so there is no catch-up lag.
- **Residual difference** is bounded by the 100 ms tick interval plus small per-device
  network jitter on the offset read — i.e. well under a few hundred ms, not eliminable 100 %
  client-side without a continuous clock-sync channel (which would need a server). Observed
  across four emulators the countdowns move together with no visible lag.

### History & Profile

- **Histórico** (`HistoryScreen`): the player's own games, newest first — category · mode,
  correct/total, date, points. Read from `/scores` filtered by uid client-side (no compound
  RTDB query; history is small).
- **Perfil** (`ProfileScreen`): global stat grid + per-mode breakdown from `/jogadores/{uid}`,
  plus inline **name editing** (writes `/jogadores/{uid}/nome`, reusing the register path).

### Card & option consistency

Category and Mode cards now use fixed uniform heights; the in-game answer options were made
shorter (68 dp) with **centred** text. The loose category label on the Mode screen was
replaced by a proper colour+icon **chip** (`CategoryChip`).

### Verified (2026-07-24)

- Solo flow (Categoria → Modo → Pergunta → Pódio) still works unchanged.
- 1x1 via the new flow: two devices picking **1x1 → Geografia → Clássico** matched on the
  same synced Q1; full duel → **"Vitória!"** podium. **Caótico 1x1** showed the shared event
  banner ("Roubo / +50 pontos") on the synced question. "Encontrado!" reveal confirmed.
- 2x2 via the new flow: four devices picking **2x2 → Cultura Geral → Clássico** matched into
  Equipa A/B on the same Q1 (queue key `2x2__Cultura_Geral__classico`).
- History and Profile screens render correct per-uid data on device.
- (2x2/Grupo end-to-end podium + disconnect mechanics were fully verified in the previous
  phase and are unchanged code paths.)

## Visual system (Sticker)

- Background `#EAE6DD` (cream), cards `#F6F1FB` (lavender)
- 3dp `#1A1523` ink border on every coloured block
- Hard offset shadow (no blur): drawn as an ink round-rect offset down-right
  (`ui/theme/Sticker.kt`)
- Corners 18–36dp
- Block palette: Purple `#6C3CE0`, Gold `#FFC93C`, Coral `#FF6B5B`, Teal `#2FBF9F`,
  Ink `#1A1523`; Neutral `#C9BEDD` for muted answered options
- Fonts: Fredoka (titles) + Manrope (body) as variable-font assets in `res/font`
- Icons: Material Symbols Rounded (no emojis)

## Build notes

- Kotlin 2.2, AGP 9.2.1, compileSdk 36, minSdk 26, Gradle 9.4.1.
- AGP 9.2's built-in Kotlin support conflicts with the explicit
  `org.jetbrains.kotlin.android` plugin — it is intentionally omitted; only
  `org.jetbrains.kotlin.plugin.compose` is applied.
- `FontVariation` requires `@OptIn(ExperimentalTextApi::class)` (see `ui/theme/Type.kt`).

## Phase 11 — Mockup alignment + bottom nav + waiting rooms (2026-07-24)

Reference: `Pergunta o Luso - Redesign.html` (17 screens). Goal: fixed bottom navigation,
align existing screens to the mockup, and build the missing matchmaking / waiting-room
screens per format. Amigos and "Entrar numa Sala" were explicitly **out of scope**.

### Bottom navigation (`ui/theme/BottomNav.kt`, `game/MainScaffold.kt`)

- White pill, 24 dp corners, 3 dp ink border, no shadow; 4 equal sections.
- Icons: `home` (Início), `emoji_events` (Ranking), `group` (Amigos), `person` (Perfil).
- Active tab = Purple `#6C3CE0` icon + 20×3 dp rounded purple underline; inactive = Ink, no underline.
- `MainScaffold(active, …)` wraps the four main screens (Início/Ranking/Perfil, and Amigos
  placeholder) so the bar is identical everywhere. History reuses the scaffold with
  `NavTab.NONE` (reached from Início, not a nav destination — the mockup only has 4 icons).
- `group` → `FriendsScreen`, now backed by the real friends system. The screen uses tabs for
  Amigos / Recebidos / Enviados instead of stacking all three lists vertically.

### Waiting / matchmaking room (`game/multi/MultiMatchScreen.kt` → `WaitingRoom`)

Rebuilt the `SEARCHING` phase to mockup **screen 5 (Matchmaking)**, generalised per format:

- Header per format ("À Procura de Adversário / de Equipa / de Jogadores"), format icon,
  purple status card ("À procura de jogadores…", `X / N encontrados`, live `Tempo de espera`
  clock), secondary CANCELAR PROCURA, and an auto-start note.
- **Seats** fill with real player names as they enter the room:
  - **1x1 / Grupo**: flat seat list (`Tu` + opponents; empty slots are dashed
    "À procura…" / "Vaga livre"). Grupo shows a `JOGADORES (X/N)` heading.
  - **2x2**: two team columns (EQUIPA A / EQUIPA B), 2 seats each, filled seats show a
    `check_circle`, empty seats show "Vazio".
- New `Modifier.stickerDashed` (dashed rounded border, no shadow) for empty slots.
- Auto-advances to the existing **MATCHED ("Encontrado!")** reveal, then the game.

### Screen alignment

- **Mode cards** (`ModeScreen`): added a white circular icon badge (bolt / whatshot /
  favorite) on the left and a `chevron_right` on the right — matches mockup screen 2.
- **Format cards** (`FormatScreen`): added `chevron_right` — matches mockup screen 3.

### Visual verification (side-by-side vs mockup, on emulator)

- Início, Ranking, Perfil, Amigos: bottom bar present, correct tab active (purple + underline). ✓
- Mode screen: icon badges + chevrons render (screen 2). ✓ Format screen: chevrons (screen 3). ✓
- 1x1 waiting room (screen 5), 2x2 (screen 7 layout), Grupo (screen 8 layout): titles, icons,
  `X/N encontrados`, wait clock, seats, CANCELAR, lock note all render. ✓
- 1x1 end-to-end on two devices: WaitingRoom → "Encontrado!" (both players) → live game with
  scoreboard/timer/question. No regression from the `MainScaffold` refactor. ✓

### Honest divergences (mockup element → why it is not matched)

These need systems the app does not have, or were explicitly excluded — they are **not**
silent omissions.

> **Revisto na Fase 31 (2026-08-07).** Esta lista é de Julho e várias entradas ficaram para
> trás do código: as fases seguintes resolveram umas e **mudaram os factos** de outras, sem
> ninguém voltar aqui. Cada entrada passa a trazer o seu estado. As que dizem
> ~~RESOLVIDA~~ ficam registadas — e riscadas — em vez de desaparecerem, para o histórico de
> decisões continuar a ler-se de cima a baixo.

1. ~~**Room codes / invite flow**~~ — **RESOLVIDA na Fase 25.** Existem salas privadas por
   código: `CRIAR SALA POR CÓDIGO (1X1)` / `(GRUPO)` e `Entrar numa Sala por Código`
   (`CustomCategoriesScreen`, `GameViewModel.joinPrivateRoomByCode`,
   `MultiMatchRepository.createPrivateRoom`, rules de `/salas_privadas`). O matchmaking
   aleatório **não** foi substituído — coexistem. Continua sem haver o `ESTOU PRONTO` dos
   ecrãs 6/7/8: a partida arranca sozinha quando a sala enche, ou à mão pelo anfitrião no
   `INICIAR JOGO` da Fase 30.
2. ~~**Rank name**~~ (screens 1, 11, 14: e.g. "EXPLORADOR") — **RESOLVIDA na Fase 31.** Ver
   "Patentes por nível": seis patentes derivadas do nível (Grumete → Descobridor), visíveis
   no Início, no Perfil e no Ranking. O nome escolhido não é "Explorador" — a escala segue a
   hierarquia de bordo de uma nau, decidida e justificada nessa secção.
3. **Exact large live-player counters** (screen 1 "2.847 A JOGAR AGORA", "34 online"): presence
   now exists, but it counts currently connected app clients rather than a marketing-style global
   population number.
4. **Streak flame badge on Início** (screen 1 `local_fire_department 7`): streak is shown in
   Perfil as a stat, not as a header badge.
5. **Mockup-perfect achievement badges** (screen 14): achievements now exist, but use the app's
   Portuguese-symbol avatar set instead of the exact mockup artwork.
6. ~~**Ranking structure**~~ (screen 11) — **RESOLVIDA na Fase 32.** O Ranking passou a ter duas
   dimensões: **Por modo** (Clássico/Caótico/Eliminatórias, como antes) e **Por formato**
   (1x1/2x2/Grupo), esta última alimentada pelos `multiVitorias` / `multiJogos` que existiam em
   `/jogadores` desde a Fase 13 e que só o `Achievement.kt` consumia. Pastilhas de nível já
   existiam e desde a Fase 31 trazem a patente. **Divergência residual:** o mockup mistura
   Global/1x1/2x2 numa lista só; a app mantém as duas famílias separadas, e por formato mostra
   vitórias/jogos/percentagem em vez de pontos, porque pontos por formato não existem nos dados
   (ver Fase 32).
7. ~~**Per-category question counts**~~ (screen 4 "312 perguntas") — **RESOLVIDA na Fase 31.**
   Cada cartão de categoria mostra `N perguntas`, contadas por `shallow=true` sem descarregar
   as perguntas. Ver "Contagem de perguntas por categoria".
8. **Início quick-access tiles** (screen 1 "ACESSO RÁPIDO", "Amigos", "Entrar em Sala"):
   continua sem a secção "ACESSO RÁPIDO" do mockup, mas **o motivo mudou**: já não há nada
   inalcançável. Início tem JOGAR / QUIZZES DA COMUNIDADE / HISTÓRICO (+ LOGIN quando anónimo)
   e a barra inferior; Amigos está na barra inferior e **Entrar em Sala existe** desde a Fase 25,
   dentro dos Quizzes da Comunidade. O que falta é o atalho no Início, não a funcionalidade.
9. **Question image** (screen 9 "imagem da pergunta (opcional)"): questions are text-only.
   Confirmado na Fase 31 — `Question.kt` não tem campo de imagem.
10. **Grupo size** — **DESACTUALIZADA: o número no documento estava errado.** O mockup mostra
    "JOGADORES (4/8)"; este documento dizia que a app limitava o Grupo a **4**, mas
    `MatchFormat.GRUPO` tem **`players = 10`** e a sala de espera mostra `X/10`. Já tinha sido
    apanhado na Fase 30 ("este documento continuou a dizer 4"), que corrigiu o código à volta
    mas não veio cá riscar o número. Fica corrigido. **A decisão de desenho que ficou em aberto
    foi tomada na Fase 32:** o Grupo é **4 a 10** — dez lugares de capacidade, quatro de mínimo
    para arrancar. O cartão do `FormatScreen` passou a dizê-lo, e o texto vem agora do próprio
    `MatchFormat`, para não voltar a divergir.
11. **Histórico filter tabs** (screen 12 "Todos/Oficial/Personalizadas") — **as duas metades
    desta entrada envelheceram.** (a) Já **existe** um sistema de perguntas personalizadas
    (Quizzes da Comunidade, Fases 20/25), por isso o motivo dado — "no custom-questions
    system" — deixou de ser verdade. (b) O Histórico também já não filtra por **modo**: filtra
    por **formato** (Todos / Solo / 1x1 / 2x2 / Grupo), a partir do campo `formato` que
    `ScoreEntry` passou a gravar. Ou seja, nem o mockup nem a redacção anterior descrevem o que
    lá está. A divergência que resta é só esta: os separadores não são Oficial/Personalizadas.
12. **História category icon**: mockup uses `castle`; the app uses `HistoryEdu` (Material
    Symbols `castle` is not in the bundled Compose icon set) — a minor icon substitution.
13. **Wait timer** is a client-side elapsed clock (there is no server-tracked queue time);
    cosmetically it matches the mockup's "Tempo de espera".

## Phase 12 — Full UI hierarchy pass after Part A (2026-07-28)

Reference: same `Pergunta o Luso - Redesign.html`, after the Início/Amigos pass. Criterion:
one obvious primary action per screen, secondary/rare/destructive actions visually quieter, no
unexplained controls, and tabs/sections when stacked content became too dense.

### Shared button rule

`StickerButton` now derives icon/text colour from its `fillColor` via `textColorFor`. This keeps
Gold/Lavender buttons ink-on-light, but makes Purple/Coral/Teal buttons readable without each
screen overriding contrast manually.

### Screen decisions

- **Formato**: kept four selectable cards but neutralised card surfaces to Lavender, moving
  differentiation to the icon badge colour and chevron. Reason: all four are equivalent choices,
  not four competing primary CTAs.
- **Categoria**: kept full-width colour cards because colour is category identity, not action
  hierarchy. Added press/cascade motion only; no extra controls because question counts are not
  available from data.
- **Modo**: same structure as the mockup's mode list; each mode is a peer choice, so the cards
  stay neutral with coloured icon badges rather than multiple primary-coloured blocks.
- **Pergunta**: preserved coloured answer options because the colour maps to answer affordance.
  Feedback now animates into correct/wrong states; streak appears only from 2+ correct answers
  and pulses only at 5+, so it does not occupy attention before it matters.
- **Pódio**: `JOGAR NOVAMENTE` remains the single Gold primary. `VOLTAR AO INÍCIO` is Lavender
  secondary because it is useful but should not compete with replay.
- **Sala de Espera / Encontrado!**: waiting keeps the Purple status card as the main state, not
  an action. `CANCELAR PROCURA` is Lavender and the note now explains auto-start; this removes the
  earlier Coral destructive-looking CTA and clarifies why there is no "Pronto" button or room code.
- **Ranking**: replaced three stacked boards with two tab rows: mode first, metric second
  (vitórias/pontos/recorde). Reason: the app has richer ranking data than the mockup, but stacking
  every list made the screen too dense and weakened hierarchy.
- **Histórico**: added mode tabs (Todos/Clássico/Caótico/Eliminatórias). Reason: the mockup's
  Oficial/Personalizadas split has no data source here; filtering by actual modes is useful and
  avoids one long undifferentiated list.
- **Perfil**: kept the mockup's profile/stat emphasis, but changed "Por modo" from three stacked
  stat cards into tabs. Reason: the same metrics repeat per mode and stacking inflated density.
  Sign-out remains at the bottom as a smaller secondary/destructive action, not on Início.
- **Avatar**: kept the grid, added press/cascade motion and selected-pop feedback. Reason: every
  avatar is an equal choice; selection feedback should confirm the action without adding another CTA.
- **Conquistas**: added Todas/Feitas/Por fazer tabs. Reason: the app has 15 real achievements,
  more than the mockup preview; filters reduce visual load while preserving progress information.
- **Login**: Gold is reserved for `ENTRAR`. `ENTRAR SEM CONTA` is Lavender and `CRIAR CONTA` is
  Teal after an `OU` divider, matching the mockup hierarchy and separating account creation from
  sign-in.
- **Registar**: the main action is Purple `CONTINUAR`, matching the mockup's stepped registration
  direction. There is no second primary action on the screen.

### Deliberate non-changes

- No room-code, invite, or "Estou pronto" controls were added to matchmaking because this app uses
  queue-based random matchmaking and auto-starts when full.
- No fake question images, category counts, custom-question filters, or rank names were introduced;
  those require data/models the app does not currently own.

## Phase 12b — XP / level progression + "a jogar agora" presence (2026-07-27)

### XP and levels (`data/Progressao.kt`)

Curve and reward **ported from BrainBrawl** (`ProgressaoService` + `ProgressionRewardPolicy`) —
behaviour as reference, not copied code:

- **Level curve**: `xpNecessarioParaProximoNivel(n) = 300 + (n-1)*150` (300, 450, 600, …).
  The level and within-level split are **derived** from `xpTotal` by draining per-level costs
  (`Progressao.estado`). Only `xpTotal` is persisted → stored data can never drift from the curve.
- **XP earned per finished game** = `base + performance + victory`:
  - `base` = **50** (Clássico / Caótico) or **40** (Eliminatórias)
  - `performance` = `respostasCertas * 10`
  - `victory` = **100** (or **80** in Eliminatórias) when the game was won, else 0

**Divergence from BrainBrawl (chosen + documented):** BrainBrawl zeroes the victory bonus for
its SOLO mode (only multiplayer wins are rewarded). In Pergunta ó Luso **only the solo path
currently folds into the aggregated `/jogadores/{uid}` profile** — multiplayer matches don't yet
aggregate — and a solo win (accuracy ≥ 0.7, or surviving Eliminatórias) is the meaningful win
signal, so the victory bonus is granted for **any** won game. If/when multiplayer results start
aggregating into the profile, the same `Progressao.xpGanho` applies unchanged.

`xpTotal` is folded in `ProfileRepository.accumulate()` alongside jogos/pontos/vitórias, inside
the same atomic transaction. `Profile` exposes `xpTotal`, `nivel`, and `progressao` (derived).
Existing profiles start at `xpTotal = 0` → level 1 (pontos are **not** XP — a high-points veteran
is still Nv 1 until they play a game under this system).

**UI** (`ui/theme/Progression.kt`: `LevelBadge`, `XpBar`, `LevelPill`):
- **Início**: gold level badge on the profile card + an XP bar showing `x / y XP`.
- **Perfil**: level badge + a detailed `x / y XP` bar above the stats.
- **Ranking**: a compact `Nv n` pill next to every player in each list.

### Presence — "A JOGAR AGORA" (`data/PresenceRepository.kt`)

- Each client writes `/presenca/{uid} = true` with `onDisconnect().removeValue()`; a
  `.info/connected` listener **re-arms** the onDisconnect and re-writes presence on every
  reconnect (same server-side disconnect mechanism used for multiplayer desistência).
- The Início counter reads `snapshot.childrenCount` in real time (`ValueEventListener`).
- **Meaning of "a jogar agora": any player with the app open** (a live socket), not only players
  inside an active match. Distinguishing "in game" would need extra shared state; "app open" is
  the simplest honest definition. Documented per the brief.
- Rules: `/presenca` public read (only the child count is used), each uid writes only its own boolean.

### Verified on 2 emulators (2026-07-27)

- Presence counter: **1** (one app open) → **2** (second opens) → **1** (second force-stopped,
  server ran onDisconnect) → **2** again (rejoin). Cross-checked against the raw `/presenca` node
  via REST each step.
- XP: a solo Clássico game with **0/10 correct, not won** produced exactly **+50 XP** (base only) —
  `50 / 300 XP`, still level 1 — matching `Progressao.xpGanho`. Level badge, Início bar, Perfil bar,
  and Ranking `Nv` pills all render the derived level/XP.
- **Known limitation (documented, accepted):** after an unusually long idle the emulator's RTDB
  socket can drop; during that blip a client's own presence is briefly removed then re-armed, so
  the counter can momentarily read low and **self-corrects on the next presence change**. Per the
  brief the counter "não precisa de ser exato ao segundo", and steady-state up/down/rejoin are correct.

## Phase 13 — Multiplayer aggregation + avatars & achievements (2026-07-27)

### Part 1 — Multiplayer feeds the aggregated profile

Previously only Solo folded into `/jogadores/{uid}`; multiplayer games touched nothing, so a
multiplayer-only player stayed Level 1 / 0 jogos / out of the ranking. Now `MultiMatchViewModel`
calls the **same** `ProfileRepository.updateAfterGame` / `Progressao.xpGanho` path Solo uses —
once per game, on the device that played it (RTDB rules only allow writing your own uid). It fires
both on the normal podium (`showPodium`) **and** on the walkover path (`finishTeamWalkover`, i.e.
opponent desistência), guarded by an `aggregated` flag.

**"Ganhou" per format (consistent with each podium's existing criterion):**
- **1x1 / Grupo**: win = **strictly top score** (a tie for 1st is not a win).
- **2x2**: win = **team-level** (my team's total strictly greater than the other team's; a draw is not a win).
- **2x2 walkover**: the present team wins.

XP is the unchanged formula (base 50/40 + certas·10 + vitória 100/80). No new multiplayer formula.
A per-format win counter `multiVitorias/{1x1|2x2|grupo}` (and `multiJogos/{…}`) is also recorded —
needed by the multiplayer achievements.

**Verified (2 devices):** a full 1x1 (5556 beat 5554 450–0). RTDB after, vs baseline:
- loser: jogos 1→2, pontos +0, **xpTotal 50→100** (+50 base, 0 certas, no win), vitórias 0, `multiJogos.1x1=1`.
- winner: jogos 1→2, pontos 510→**960**, **xpTotal 0→170** (50 + 2·10 + 100), vitórias 0→**1**, **`multiVitorias.1x1=1`**.

### Part 2 — Avatars & achievements

**Approach: OPTION A (hand-drawn vector).** No raster image-generation tool available in this
environment writes croppable PNG sheets to disk (the visualiser renders in chat only), so Option B
wasn't viable. Symbols are drawn as Compose `Canvas` line art (`avatar/SymbolIcon.kt`) in one shared
stroke width (identical contour thickness across all icons), single tint so the same drawing serves
avatar (cream-on-colour), locked (grey), and unlocked (full colour).

**Final symbols (10), each on a cyclic palette circle** (`PortugueseSymbol`): Azulejo, Pastel de
Nata, Caravela, Farol, Sardinha, Galo de Barcelos, Os Lusíadas, Guitarra, Calçada, Coração de Viana.
Three were redrawn after a first pass read wrong (Caravela looked like a windsurfer → two square
sails + hull + Cross of Christ; Guitarra looked like a pizza-cutter → waisted figure-8 body + long
neck + headstock; Galo was a blob → plumper body + fanned tail + comb/beak/legs). All verified
recognizable side-by-side on device; Galo is the most stylised but reads as a rooster.

**Avatar:** stored at `/jogadores/{uid}/avatar`; `AvatarView` shows the symbol-in-circle, falling
back to name initials when unset. Selector grid at `AvatarPickerScreen` (Perfil → tap avatar).

**Achievements (`Achievement.kt`, 15) — all bound to existing `/jogadores` fields:**
| Achievement | Symbol | Bound to |
|---|---|---|
| Primeira Vitória | Coração de Viana | `vitorias ≥ 1` |
| Em Chamas | Galo | `maxStreak ≥ 5` (best correct-answer streak) |
| Partida Perfeita | Pastel de Nata | `partidasPerfeitas ≥ 1` |
| Mestre de Cultura Geral | Os Lusíadas | `categorias.cultura_geral.vitorias ≥ 3` |
| Mestre de Geografia | Caravela | `categorias.geografia.vitorias ≥ 3` |
| Mestre de História | Azulejo | `categorias.historia.vitorias ≥ 3` |
| Mestre de Desporto | Sardinha | `categorias.desporto.vitorias ≥ 3` |
| Mestre de Gentílicos | Calçada | `categorias.gentilicos.vitorias ≥ 3` |
| Veterano / Dedicado / Lendário | Guitarra | `jogos ≥ 10 / 50 / 100` |
| Duelista | Caravela | `multiVitorias.1x1 ≥ 1` |
| Companheiro | Galo | `multiVitorias.2x2 ≥ 1` |
| Rei do Grupo | Calçada | `multiVitorias.grupo ≥ 1` |
| Nível 5 | Farol | derived `nivel ≥ 5` |

Category masters get distinct, thematically-fitting symbols (Lusíadas=cultura, Caravela=descobrimentos/geografia,
Azulejo=história, Sardinha=arraial/desporto, Calçada=lugares/gentílicos). Locked = grey silhouette +
ink lock badge + `x / y` progress; unlocked = full-colour symbol + gold glow ring + "Desbloqueada".

**New fields (justified — each backs a specific achievement, nothing speculative):**
`partidasPerfeitas` (perfect-game counter), `categorias/{slug}/{jogos,vitorias}` (per-category
mastery), plus Part 1's `multiVitorias`/`multiJogos`. `avatar` (string). All type-validated in the
rules; only the owning uid writes. `categoriaSlug()` normalises accents ("História"→"historia").

**Honest note on bindings:** "Em Chamas (sequência de vitórias)" is bound to `maxStreak` = best run
of **consecutive correct answers**, not consecutive game wins — that's the streak the schema already
tracks; a game-win streak would need a new field, avoided per the brief.

**Verified on device:** avatar select → saved (`avatar:"azulejo"`) → shown in Perfil/Início (initials
fallback confirmed). Achievements: a 0-win profile shows 0/15 all-locked; the 1x1 winner's profile
shows **2/15** with *Primeira Vitória* + *Duelista* unlocked (gold glow) and *Em Chamas 2/5* tracking
its real `maxStreak`.

## Phase 14 — Sistema de Amigos (2026-07-27)

### Pesquisa de jogadores (reutiliza `/jogadores`, sem índice novo)

`/jogadores/{uid}/nomeBusca` = nome **trimmed + minúsculas**, escrito **na mesma
`updateChildren` que `nome`** (`ProfileRepository.setNome`), portanto nunca diverge do nome
visível. Como registo e edição de perfil passam ambos por `setNome`, não há terceiro caminho de
escrita a manter em sincronia.

Pesquisa: `orderByChild("nomeBusca").startAt(q).endAt(q + "").limitToFirst(20)`, com
`".indexOn": "nomeBusca"` em `/jogadores`. Filtra o próprio uid e perfis sem nome — jogadores
anónimos nunca têm `nomeBusca`, logo são invisíveis à pesquisa por construção. Resultados mostram
**avatar + nome + nível** (Profile completo, não só o nome), para distinguir nomes repetidos.

**Nota honesta sobre dados legados:** perfis criados antes desta fase (ex.: "Dinis", "rato") têm
`nome` mas não `nomeBusca`, por isso não aparecem na pesquisa até o dono reeditar o nome. Não fiz
backfill — exigiria escrever no nó de outros uids, o que as rules (corretamente) proíbem; um
backfill teria de ser feito por script admin.

`nomeBusca` guarda acentos (só faz lowercase, como especificado): um prefixo **com** acento tem de
ser escrito com acento para casar.

### Modelo de dados dos pedidos (`/amigos`)

```
/amigos/{uid}/pedidosEnviados/{outroUid}   { nome, ts }   // à espera do outro
/amigos/{uid}/pedidosRecebidos/{outroUid}  { nome, ts }   // à espera de mim
/amigos/{uid}/lista/{outroUid}             { nome, ts }   // aceite, escrito nos DOIS lados
```

Um pedido pendente existe em dois sítios (o `pedidosEnviados` do remetente + o `pedidosRecebidos`
do destinatário); aceitar move-o para `lista` em ambos os lados. **Cada transição é um único
`updateChildren` multi-caminho a partir da raiz**, logo os dois lados nunca podem divergir (as
rules validam cada folha independentemente). `nome` é desnormalizado para render rápido; a UI
prefere o Profile carregado (avatar + nível actuais) e usa `nome` como fallback.

Escolhi este modelo (em vez de um nó partilhado por par de uids) porque cada jogador lê **só o seu
próprio nó** — uma única `ValueEventListener` alimenta as três zonas do ecrã, sem fan-out nem
índices extra.

### Rules

| Caminho | Quem escreve |
|---|---|
| `/amigos/$uid` (leitura) | só `$uid` |
| `pedidosEnviados/$outro` | o dono; `$outro` **só pode apagar** (ao aceitar/recusar) |
| `pedidosRecebidos/$outro` | o **remetente** `$outro` cria/retira; o dono só pode apagar |
| `lista/$outro` | o dono; `$outro` só pode escrever **enquanto existir um pedido que o dono lhe enviou** (o passo "aceitar"), ou remover-se |

A condição em `lista` (`root.child('amigos').child($uid).child('pedidosEnviados').child($outro).exists()`)
é o que impede alguém de se auto-adicionar à lista de outra pessoa: só se pode **completar** uma
amizade que o dono iniciou.

### Verificado com 2 contas reais (Ana Costa / Bruno Dias, 2 emuladores)

- Registo escreve `nomeBusca` (`'ana costa'`, `'bruno dias'`) ✓
- Ana pesquisa `"bru"` → **Bruno Dias, Nível 1** com avatar; ela própria e perfis sem nome fora ✓
- Ana envia pedido → aparece em *Pedidos enviados (1)* dela e *Pedidos recebidos (1)* dele, **em
  tempo real**, com avatar + nível ✓
- Bruno aceita → **lista (1) nos dois** e pedidos a zero; RTDB confirma o mesmo `ts` nos dois lados
  (update atómico) ✓
- Recusar e cancelar: pedido some **dos dois** nós, amizade intacta ✓
- Pesquisar alguém que já é amigo mostra "Já são amigos" (sem duplicar) ✓

Ao nível das **rules** (tokens reais via REST, não só o que o telemóvel reporta):

| Tentativa | Resultado |
|---|---|
| Ana lê `/amigos/{Bruno}` | `Permission denied` ✓ |
| Não autenticado lê `/amigos/{Ana}` | `Permission denied` ✓ |
| Ana escreve-se na `lista` de terceiro sem pedido pendente | `Permission denied` ✓ |
| Ana forja um `pedidosEnviados` no nó do Bruno | `Permission denied` ✓ |
| Ana apaga um `pedidosRecebidos` de terceiro | `Permission denied` ✓ |
| Ana cria pedido legítimo (ela como remetente) | permitido ✓ |

**Fora de âmbito nesta fase:** remover amigo (as rules já permitem a remoção bidirecional, mas não
há botão), desafiar amigo para partida, e o ecrã "Entrar numa Sala" por código.

## Phase 15 — Desafio direto a um amigo (2026-07-27)

Complementa o matchmaking aleatório (não o substitui): em vez de entrar numa fila, o jogador
escolhe **quem** vai enfrentar.

### Decisões

**Formatos suportados: apenas 1x1.** Um convite 1-para-1 mapeia exactamente numa sala de 2
jogadores. 2x2 e Grupo precisam de 4 — dar-lhes suporte exigiria um *lobby* com vários convites em
paralelo (convidar 3 amigos e esperar por todos), que é precisamente a funcionalidade
"Sala de Espera com código / Entrar numa Sala" deixada de fora. Preferi não fingir suporte: o
formato vem no convite (`formato`) e o pipeline aceita qualquer `MatchFormat`, por isso alargar
depois é só permitir mais convites por sala.

**Expiração: 45 s** (`CONVITE_TTL_MS`). Curto o suficiente para não deixar o desafiante à espera,
longo o suficiente para o amigo reagir a um overlay que aparece sem aviso.

**Só amigos online podem ser desafiados** — o botão "Desafiar" só aparece se o uid estiver em
`/presenca` (reutiliza a presença da Fase 12). Um convite só é entregue enquanto a app está aberta,
por isso mostrá-lo para amigos offline seria enganador; esses aparecem com o subtítulo "Offline".

### Modelo de dados (`/convites`, mesma forma de `/amigos`)

```
/convites/{uid}/enviados/{outroUid}   { nome, formato, categoria, modo, salaId, ts, estado }
/convites/{uid}/recebidos/{outroUid}  { nome, formato, categoria, modo, salaId, ts, estado }
```
`estado`: `pendente` | `aceite` | `recusado`.

**A sala é criada primeiro.** O desafiante chama `MultiMatchRepository.createRoomDirect(...)` (novo:
igual ao `createRoom` do matchmaking mas sem tocar na fila `/matchmakingN`) e mete o `salaId` dentro
do convite. Aceitar é, literalmente, "entra nesta sala" — `MultiMatchViewModel.startExisting(salaId)`
faz `joinRoom` + `setupDisconnect` + `observeRoom`, e **daí para a frente tudo é o código já
existente**: sincronização lockstep por `perguntaInicios`, deteção de desistência, pódio, e a
agregação de perfil da Fase 13. Zero duplicação da lógica de jogo.

Fluxo completo:
1. Desafiante escolhe amigo → **mesmos ecrãs** de Categoria e Modo do matchmaking aleatório.
2. Cria a sala, escreve o convite nos dois lados (update multi-caminho atómico) e fica no ecrã
   Amigos com um banner "À espera de X… expira em Ns".
3. Convidado vê um **overlay em tempo real** por cima de qualquer ecrã (excepto durante um jogo),
   com quem desafiou + formato/categoria/modo + Aceitar/Recusar.
4. Aceitar → escreve `estado: aceite` no nó do desafiante, apaga o seu `recebidos`, entra na sala.
   O desafiante reage ao `estado` e entra também; a sala enche → "Encontrado!" → jogo.
5. Recusar → `estado: recusado`; expirar → o desafiante limpa os dois lados ao fim de 45 s. Em
   ambos os casos o convite desaparece dos dois lados e o desafiante vê o motivo.

O destinatário filtra convites com mais de 45 s (server clock via `.info/serverTimeOffset`) e tem um
ticker de 1 s, para o caso de o desafiante morrer sem limpar.

### Rules

| Caminho | Quem escreve |
|---|---|
| `/convites/$uid` (leitura) | só `$uid` — ninguém vê convites de outra pessoa |
| `enviados/$outro` | o desafiante; `$outro` só pode **responder** (`estado`) enquanto existir o `recebidos` correspondente, ou apagar |
| `recebidos/$outro` | criado/retirado pelo **desafiante** `$outro`; o dono só pode apagar (aceitar/recusar) |

`estado` é validado contra `^(pendente|aceite|recusado)$`.

### Verificado com 2 contas amigas (Ana Costa / Bruno Dias, 2 emuladores)

- Botão "Desafiar" aparece só com o amigo online; some enquanto há um desafio pendente ✓
- Convite chega em tempo real ao Bruno (estava no Início) com nome/formato/categoria/modo ✓
- Banner do desafiante com contagem decrescente (45 → 0) ✓
- **Aceitar** → ambos entram na mesma sala, jogo 1x1 completo → pódio "Vitória!" (Ana 420, Bruno 180) ✓
- **Perfil agregado atualizou exactamente como no matchmaking aleatório** (mesma lógica da Fase 13):
  Ana jogos 4→5, pontos 1185→**1605** (+420), xpTotal 240→**420** (+180 = 50 + 3·10 + 100 vitória),
  vitórias 0→**1**, `multiVitorias.1x1` → 1, `multiJogos.1x1` 1→2;
  Bruno jogos 2→3, pontos 960→**1140** (+180), xpTotal 170→**230** (+60 = 50 + 1·10) ✓
- **Recusar** → Ana vê "Bruno Dias recusou o desafio.", convite some dos dois lados ✓
- **Expirar** (sem resposta) → Ana vê "Bruno Dias não respondeu a tempo.", overlay desaparece no
  Bruno, convite limpo dos dois lados ✓
- **Cancelar** pelo desafiante → banner some, botão "Desafiar" volta ✓

Ao nível das **rules** (tokens reais):

| Tentativa | Resultado |
|---|---|
| Ana lê `/convites/{Bruno}` | `Permission denied` ✓ |
| Não autenticado lê `/convites/{Ana}` | `Permission denied` ✓ |
| Ana forja um convite "enviado" no nó do Bruno | `Permission denied` ✓ |
| Bruno marca `aceite` num convite que não recebeu | `Permission denied` ✓ |
| Ana apaga o inbox de um terceiro | `Permission denied` ✓ |
| `estado` fora do enum, mesmo no próprio nó | `Permission denied` ✓ |
| Ana cria convite legítimo (ela como desafiante) | permitido ✓ |

**Nota honesta:** "NOVO JOGO" no pódio de uma partida por convite volta ao **matchmaking
aleatório** — a sala do desafio é de uso único e voltar a desafiar exige novo convite. Convites
enquanto se está a jogar não são mostrados (o overlay é suprimido durante a partida); ficam no
inbox e aparecem no fim, se ainda não tiverem expirado.

## Phase 16 — Perguntas Verdadeiro/Falso + novas perguntas + validação (2026-07-27)

### Novo tipo: Verdadeiro/Falso — **sem alteração de schema**

O schema já suportava: `QuestionRepository` só rejeitava `opcoes.size < 2`. Uma pergunta V/F é
simplesmente `opcoes: ["Verdadeiro", "Falso"]` com `respostaCorreta` numa delas. As 964 perguntas
de 4 opções continuam intactas e a correr pelo mesmo caminho.

- `Question.isVerdadeiroFalso` deteta o tipo (2 opções == {verdadeiro, falso}).
- Escolha múltipla continua **baralhada**; V/F fica em **ordem canónica** (Verdadeiro primeiro) —
  baralhar "Verdadeiro"/"Falso" lê-se como bug.
- **UI** (`QuestionScreen` e `MultiMatchScreen`): com 2 opções os cartões passam de 68→**92 dp**
  (66→88 no multiplayer), ganham ícone `check`/`close` e tipografia de título, com Teal/Coral em
  repouso. Não há esticamento — os cartões têm altura fixa; o layout V/F é deliberado, não uma
  lista de 4 a que faltam itens.
- Pontuação, dificuldade, streak, eventos Caóticos e temporizador: **inalterados**, é o mesmo
  `Scoring`.

### Novas perguntas (Parte 2)

**Total: 964 → 1091** (+127 líquidas; 131 inseridas, 2 duplicadas descartadas na inserção e 4
removidas por mim depois — ver abaixo). **34 são V/F.**

| Categoria | Antes | Depois | Novas | V/F |
|---|---|---|---|---|
| História | 224 | 326 | +102 | 20 |
| Cultura Geral | 204 | 217 | +13 | 6 |
| Geografia | 206 | 212 | +6 | 3 |
| Desporto | 165 | 169 | +4 | 3 |
| Gentílicos | 110 | 112 | +2 | 2 |

Fonte A — **cartas Science4you** (3 fotos): cognomes e sucessões de reis, monumentos mandados
construir, batalhas (Aljubarrota 1385, Alcácer Quibir 1578, La Lys), descobrimentos (Ceuta 1415,
Brasil 1500, Madeira 1419, Tordesilhas 1494, Macau 1999), República/Estado Novo (PIDE, Sidónio
Pais 1918, Ultimatum 1890, Caetano 1968, CEE 1986) e ~16 afirmações V/F.
Fonte B — extras sugeridos no mesmo estilo, com V/F para exercitar o tipo novo (fado UNESCO,
Amália, Cabo da Roca, Açores 9 ilhas, Euro 2016, Rosa Mota, Carlos Lopes…).

Categoria e dificuldade foram atribuídas pergunta a pergunta pelo conteúdo (as cartas eram quase
todas de História, mas Camões/Saramago/Egas Moniz/Jerónimos foram para Cultura Geral, e o
terramoto de 1755 e a Expo'98 para Geografia).

**Ressalva honesta sobre as fotos:** as chaves de resposta estão impressas ao contrário e em corpo
muito pequeno; a esta resolução só parte delas era legível. Onde a chave não se lia, a resposta foi
determinada por facto histórico estabelecido, não inventada. Algumas cartas foram **descartadas por
ilegibilidade** ou por a associação pergunta↔chave ser ambígua (ex.: cartas em que a numeração das
respostas não batia com a ordem visível das perguntas).

**Auto-correções (perguntas MINHAS, removidas depois de inseridas):**
- "Como se chamam os naturais de Coimbra? → Conimbricenses" — **contradizia** a existente
  `Gentílicos #3` ("Quem nasce em Coimbra é? → Coimbrense"). Ambas as formas são legítimas; removi
  a minha para não dar respostas contraditórias entre jogos.
- "Como se chamam os naturais de Guimarães?" — duplicava `Gentílicos #27`.
- "Qual foi o primeiro Presidente da República Portuguesa?" — duplicava `História #45`.
- "Quantos anos durou a Dinastia Filipina?" — duplicava `História #182` (União Ibérica, 60 anos).

### Validação das 964 existentes (Parte 3)

**Estrutural (automática, 964/964):** 0 respostas fora das opções, 0 opções duplicadas, 0 com nº de
opções ≠ 4, 0 dificuldades inválidas, 0 campos vazios, 0 perguntas repetidas. Base sólida.

**Factual:** revi integralmente Desporto (165), Gentílicos (110), História (224), Geografia (206) e
Cultura Geral (204) — ou seja, **as 909 das cinco categorias visíveis** (as 55 de "Gastronomia
Portuguesa" e "Património Português" estão ocultas na app e ficaram de fora). **Não corrigi nada**;
segue a lista para decisão.

**A — Erradas com elevada confiança**
| # | Pergunta | Resposta atual | Porquê |
|---|---|---|---|
| Desporto #40 | "No triatlo, Portugal já conquistou medalhas olímpicas?" | Não | Vanessa Fernandes ganhou **prata** em Pequim 2008 |
| Desporto #120 | "O atleta António Areia é uma referência em que modalidade?" | Andebol | É **canoísta** (K1/K4) |
| Desporto #78 | "Em que cidade nasceu Pedro Pichardo?" | Havana | Nasceu em **Santiago de Cuba** |
| História #20 | "Quem foi a primeira rainha reinante de Portugal?" | D. Maria II | Foi **D. Maria I** (1777) |
| História #98 | "Que território foi devolvido a Portugal em 1801…?" | Olivença | Olivença foi **perdida** em 1801 e nunca devolvida |
| História #204 | "Revolta camponesa no Alentejo em 1911" | Maria da Fonte | Maria da Fonte foi **1846, no Minho** |
| História #106 | "Território administrado em conjunto por Espanha e Portugal" | Ilha de Timor | Timor foi dividido com os **Países Baixos** |
| Geografia #196 | "Qual destas cidades se encontra no litoral?" | Leiria | Leiria fica ~15 km do mar |
| Geografia #152 | "O distrito de Évora faz fronteira com quantos distritos?" | 3 | São **4** (Santarém, Portalegre, Setúbal, Beja) |

**B — Premissa falsa / mal formuladas**
- História #167 "governador do **Japão português**" — nunca existiu Japão português.
- História #93 "O **apagão** de 1755" — anacronismo (não havia eletricidade).
- História #127 "Quem foi a **escritora** de 'A Casa Grande de Romarigães'? → Aquilino Ribeiro" — género trocado.
- História #159 "Que **tratado**… → Bula Manifestis Probatum" — uma bula não é um tratado.
- Desporto #151 "**Que título** europeu… → 1947" — pergunta pede título, resposta é ano.
- Desporto #100 "Regata da Senhora da Agonia" nos **Açores** — a Senhora da Agonia é de Viana do Castelo.
- Cultura Geral #138 "nota de 5 **euros** … **antes do euro**" — contraditória.
- Cultura Geral #90 "Em que ilha dos Açores se localiza o Pico? → Pico" — circular.
- Geografia #131 "O Miradouro da Lua… → Portugal não tem este miradouro" — pergunta-armadilha (é em Angola).

**C — Ambíguas / mais de uma resposta defensável**
- Gentílicos #7 vs #16: **Bragança** aparece duas vezes com respostas diferentes (*Brigantino* / *Bragançano*). Ambas corretas; ensina respostas inconsistentes.
- História #164 "Quem liderou a revolta de 1383-85? → Nuno Álvares Pereira" — o líder político foi **D. João, Mestre de Avis**.
- História #6 "batalha decisiva na afirmação da independência → Ourique" — **São Mamede** é igualmente defensável.
- História #62 "'O Pacificador' → D. João VI" — o cognome consagrado de D. João VI é **O Clemente**.
- História #184 "ordem extinta em 1834 → Jesuítas" — em 1834 extinguiram-se **todas**; os jesuítas foram expulsos em 1759.
- História #46 / #175 / #7 (fundação do Porto em 868 = reconquista, não fundação).
- Desporto #68 "melhor marcador de Portugal no Euro 2004 → Cristiano Ronaldo" — foi **Nuno Gomes**.
- Desporto #74 "capitão na final do Euro 2016" — Ronaldo saiu lesionado, a braçadeira passou a **Nani**.
- Desporto #10 / #19 / #41 / #46 (clube com mais Voltas a Portugal; Sporting heptacampeão europeu; líder do voleibol; pista coberta mais antiga) — atribuições que não consegui confirmar.
- Cultura Geral #42 "arquiteto da Gulbenkian → Ruy Athouguia" — foi **equipa** (Athouguia, Pedro Cid, Alberto Pessoa).
- Geografia #188 "maior município dos Açores → Ponta Delgada" — depende de área vs população.

**D — Não consegui confirmar (possivelmente inventadas)**
História #44 (fundador do Banco de Portugal), #112 (comandante em 1808), #146 (república no Porto),
#166 ("Conferência de Londres"), #169 (Rui Machete/Timor), #183 ("Campanha da Primavera"),
#185 (Gaspar Frutuoso "médico" — era **padre**), #192, #197, #207 ("Acordo de Maputo");
Desporto #85 ("Joana Vasconcelos" campeã de canoagem — é **artista plástica**), #101 vs #147
(Clube dos Galitos: natação ou ciclismo?), #132 ("Taça Federação" = futebol feminino? é ténis);
Cultura Geral #108 ("Os Carrascos do Sol"), #113, #124, #164, #176, #181.

**E — Sensíveis ao tempo** (corretas hoje, envelhecem): Desporto #3 (5 ouros "até 2020"), #39
(16 títulos de hóquei "(2023)" — Portugal voltou a vencer em 2024), #62, #156.

### Testado em jogo (não inventado)

Partida solo **História · Clássico** com V/F sorteada:
- **Erro**: escolhi "Verdadeiro" em *"O 25 de Abril de 1974 foi uma revolução violenta…"* → cartão
  escolhido em **Coral**, resposta certa ("Falso") revelada em **Gold**. ✓
- **Acerto**: *"Fernão de Magalhães comandou a primeira viagem de circum-navegação"* → "Verdadeiro"
  em **Teal**, a outra em **Neutral**, `+215 pontos` e o total subiu **165 → 380**. ✓
- Partida levada até "Fim de jogo!" sem incidentes; V/F conta para pontos/acertos como qualquer outra. ✓

Nota de método: numa primeira tentativa julguei ter jogado 4 partidas sem V/F, mas o botão
"JOGAR NOVAMENTE" não estava a ser acertado e a app ficou no pódio — essas "partidas" não contam e
não foram usadas como evidência.

### Nota operacional

`/categorias` tem `.write: false`. Para semear, as rules foram **destrancadas temporariamente**,
feito backup de `/categorias`, escritas as perguntas, e **voltadas a trancar** logo a seguir
(confirmado com um PUT que devolve `Permission denied`). O mesmo procedimento foi repetido para
remover as 4 perguntas minhas que colidiam.

## Phase 17 — Correção das perguntas sinalizadas na Fase 16 (2026-07-27)

**43 perguntas corrigidas + 1 removida.** Total 1091 → **1090**. Verificação estrutural pós-correção:
0 problemas (resposta sempre nas opções, sem opções duplicadas, 2 ou 4 opções, dificuldade válida).

### A — Erradas (facto corrigido)

| Onde | Antes | Depois | Porquê |
|---|---|---|---|
| Desporto #40 | "Portugal já teve medalhas olímpicas no triatlo?" → **Não** | "Que atleta portuguesa ganhou prata no triatlo em Pequim 2008?" → **Vanessa Fernandes** | A resposta era falsa; reformulada para o facto |
| Desporto #120 | António Areia → **Andebol** | → **Canoagem** | É canoísta |
| Desporto #78 | Pichardo nasceu em **Havana** | → **Santiago de Cuba** | Cidade errada |
| História #20 | 1.ª rainha reinante → **D. Maria II** | → **D. Maria I** | Maria I reinou desde 1777 |
| História #98 | "território **devolvido** em 1801" → Olivença | "praça **ocupada por Espanha** em 1801 (Guerra das Laranjas) e nunca devolvida" → Olivença | Sentido invertido |
| História #204 | "revolta camponesa no **Alentejo em 1911**" | "revolta popular no **Minho em 1846**" | Maria da Fonte foi 1846, no Minho |
| História #106 | Timor administrado com **Espanha** | "Com que país europeu Portugal dividiu Timor?" → **Países Baixos** | Foi com os holandeses |
| Geografia #196 | "cidade no litoral" → **Leiria** | → **Figueira da Foz** (Leiria saiu das opções) | Nenhuma das opções era litoral |
| Geografia #152 | Évora faz fronteira com **3** distritos | → **4** | Santarém, Portalegre, Setúbal e Beja |

### B — Premissa falsa / mal formuladas (reformuladas)

- **História #167** "governador do *Japão português*" → "Que missionário jesuíta partiu de Portugal e chegou ao Japão em 1549?" → **Francisco Xavier** (nunca existiu Japão português).
- **História #93** "O **apagão** de 1755" → "A destruição de grande parte de Lisboa, em 1755, foi causada por" (anacronismo removido).
- **História #127** "Quem foi a **escritora**…" → "Quem escreveu…" (Aquilino Ribeiro é homem).
- **História #159** "Que **tratado**… Bula Manifestis Probatum" → "Que **documento pontifício**…".
- **Desporto #151** "**Que título** europeu…" com resposta "1947" → "**Em que ano** conquistou Portugal o seu primeiro título europeu de hóquei em patins?".
- **Desporto #100** "Regata da Senhora da Agonia **nos Açores**" → "As festas da Senhora da Agonia… realizam-se em que cidade?" → **Viana do Castelo**.
- **Cultura Geral #138** "nota de 5 **euros** … **antes do euro**" → "Que castelo é conhecido como o berço da nacionalidade?" → Castelo de Guimarães.
- **Cultura Geral #90** "Em que ilha dos Açores se localiza o **Pico**? → Pico" (circular) → "Em que ilha dos Açores se ergue a **montanha mais alta de Portugal**?" → Pico.
- **Geografia #131** pergunta-armadilha ("Portugal não tem este miradouro") → "Em que ilha dos Açores fica o miradouro da Vista do Rei, sobre a Lagoa das Sete Cidades?" → São Miguel.

### C — Ambíguas (pesquisadas antes de decidir)

- **Gentílicos #7/#16** — pesquisa: *brigantino* e *bragançano* são **ambos corretos** (a par de *bragantino* e *bragancês*). Mantive **#7 "Brigantino"** (forma mais tradicional) e **removi #16**, como pedido.
- **Desporto #68** — pesquisa: no Euro 2004 os melhores marcadores portugueses foram **Ronaldo, Maniche e Rui Costa, com 2 golos cada** (empate a três). *A minha suspeita da Fase 16 de que seria "Nuno Gomes" estava errada.* Como havia empate e duas opções eram jogadores empatados, reformulei: "Quantos golos marcou Ronaldo no Euro 2004?" → **2**.
- **Desporto #74** — Ronaldo saiu lesionado aos 25' e a braçadeira passou a **Nani**. Reformulada para perguntar quem a recebeu → Nani.
- **Desporto #10** — não há fonte clara para "clube com mais Voltas a Portugal" (Sporting surge com 13 vitórias por equipas, não FC Porto). Troquei pelo facto documentado: **recorde de vitórias individuais → Marco Chagas (4)**.
- **Desporto #19** — o feito documentado é ter sido o **primeiro clube europeu a vencer seis Europeus de Corta-Mato consecutivos**; "heptacampeão" não se confirma. Pergunta reescrita mantendo **Atletismo**.
- **Desporto #41** — **confirmado**: SC Espinho tem **18 campeonatos nacionais**, mais que qualquer clube. Resposta mantida, pergunta afinada ("mais títulos de campeão nacional").
- **História #164** — o líder político da crise de 1383-85 foi **D. João, Mestre de Avis**; Nuno Álvares foi o **comandante militar**. Pergunta reescrita para "comandou **militarmente**" → resposta mantida.
- **História #6** — sem consenso sobre qual batalha foi "decisiva" (Ourique vs São Mamede). Substituída por um facto não disputado: "Que batalha, em **1128**, deu a D. Afonso Henriques o controlo do Condado Portucalense?" → **São Mamede**.
- **História #62** — o cognome consagrado de D. João VI é **O Clemente**. Pergunta invertida: "Que cognome tinha D. João VI?" → O Clemente.
- **História #184** — em 1834 o decreto de Joaquim António de Aguiar extinguiu **todas as ordens religiosas masculinas** (os jesuítas já tinham sido expulsos em 1759). Reescrita em conformidade.
- **História #175** — existem dois Fortes de São João Baptista (Berlengas e Angra). Convertida em V/F: "O Forte de São João Baptista ergue-se na Berlenga Grande." → **Verdadeiro**.
- **História #7** — 868 é a **reconquista por Vímara Peres**, não a fundação do Porto. Pergunta reescrita.
- **Cultura Geral #42** — a sede da Gulbenkian foi projetada por **equipa de três** (Ruy Athouguia, Pedro Cid, Alberto Pessoa). Pergunta passou a "Qual destes arquitetos **integrou a equipa**…" → resposta mantida.
- **Geografia #188** — **confirmado**: Ponta Delgada é o maior concelho açoriano **em área (231,89 km²) e em população**. Resposta mantida; pergunta explicita o critério.
- **História #46** — "Guerra dos Cem Anos" como a guerra em que Portugal não participou diretamente é defensável. **Mantida sem alteração.**

### D — Não confirmadas na Fase 16 (pesquisadas agora)

**Confirmadas como CERTAS — deixadas como estavam:**
- **Cultura Geral #124** — o teto da Igreja de São Roque foi pintado (1584-90) por **Francisco Venegas** (arquitetura fingida) e Amaro do Vale (medalhão). A resposta estava correta.
- **Desporto #41** (ver acima), **Geografia #188** (ver acima).

**Confirmadas como ERRADAS — corrigidas:**
- **História #146** — quem proclamou a República no Porto (revolta de 31 de janeiro de 1891) foi **Alves da Veiga**, não Henrique de Barros Gomes.
- **História #44** — o Banco de Portugal nasceu por **decreto de 19 de novembro de 1846** (fusão do Banco de Lisboa com a Companhia Confiança Nacional); não há um "fundador" único como o indicado. Pergunta passou a ser sobre o **ano → 1846**.
- **História #185** — Gaspar Frutuoso foi **sacerdote e cronista**, não médico (autor de *Saudades da Terra*). Pergunta corrigida.
- **Desporto #85** — *correção à minha própria suspeita*: **Joana Vasconcelos é mesmo uma canoísta portuguesa** (n. 1991, olímpica em 2012) — eu tinha assumido tratar-se apenas da artista plástica homónima. O que estava errado era o **qualificador**: o título dela foi **mundial de juniores (K1 1000 m, 2009)**, não universitário. Pergunta corrigida.
- **Desporto #101/#147** — o Clube dos Galitos (Aveiro, fundado em **1904**) é multidesportivo (basquetebol, natação, remo, xadrez); não há suporte para "clube de ciclismo mais antigo do país". As duas perguntas passaram a factos verificáveis: **ano de fundação (1904)** e **cidade (Aveiro)**.
- **Desporto #132** — não existe competição portuguesa chamada "Taça Federação"; a prova a eliminar do futebol feminino é a **Taça de Portugal Feminina** (desde 2003). Corrigida.
- **Cultura Geral #108** — não existe nenhuma trilogia "Os Carrascos do Sol" de José Régio; o seu ciclo de romances é **A Velha Casa** (5 volumes, ficou incompleto). Corrigida.
- **Cultura Geral #164** — não existe álbum "Luz de Lisboa" na discografia de Carminho. Corrigida para o **álbum de estreia, "Fado" (2009)**.

**Não consegui verificar — deixadas EXATAMENTE como estavam** (não assumi erro por omissão, como pedido):
`História #112` (Duque de Lafões como comandante em 1808), `#166` ("Conferência de Londres"),
`#169` (Rui Machete e Timor), `#183` ("Campanha da Primavera", Paredes de Coura), `#192`
(Sinistrados do Ciclone de 1941), `#197` (restaurador de Alcobaça), `#207` ("Acordo de Maputo",
2006); `Desporto #46` (pista coberta mais antiga, Pombal); `Cultura Geral #113` ("Os Dias são
Árias"), `#176` ("O Canto da Terra"), `#181` ("Doce da Teixeira").
Continuam a **parecer duvidosas**, mas sem fonte que as confirme ou desminta preferi não forçar
uma correção especulativa. Ficam para decisão futura.

### E — Sensíveis ao tempo (âncora temporal explícita)

- **Desporto #39**: "Quantos títulos mundiais de hóquei em patins tem Portugal? (2023)" → "…**tinha Portugal até 2023**?" (a âncora deixou de ser um parêntese solto).
- **Desporto #62**: "Quantas Ligas dos Campeões venceram clubes portugueses?" → "**Até 2025**, quantas…".
- **Desporto #3** ("até 2020") e **#156** ("com o ouro no madison **em 2024**") já traziam âncora — mantidas.

### Nota operacional

Mesmo procedimento da Fase 16: backup de `/categorias` (1091 perguntas), `.write` destrancado,
correções aplicadas, `.write` voltado a `false` e **confirmado com um PUT que devolve
`Permission denied`**.

## Fase 18 — Hierarquia de cor em todos os ecrãs (2026-07-28)

O passo anterior (commit `fix(ui): hierarquia de cor e densidade nos ecrãs principais`) arrumou
Início, Perfil, Amigos, Formato e Modo. Esta fase aplica o **mesmo
critério aos restantes ecrãs** — Categoria, Pergunta, Pódio, Sala de espera / Encontrado!,
Ranking, Histórico, Conquistas, Avatar, Login e Registar — comparando cada um com
`Pergunta o Luso - Redesign.html` (17 ecrãs). Todos foram vistos no emulador antes de avançar.

### A regra que faltava: **um só dourado por ecrã, e nem sempre é um botão**

O contrato da fase anterior era "Dourado = ação primária do ecrã (uma só)". Ao percorrer os
ecrãs que faltavam ficou claro que o dourado tem, de facto, **dois significados legítimos**:
ação primária **e** primeiro lugar / mérito (medalha, conquista desbloqueada, #1 do ranking).
Nos ecrãs de resultado os dois colidiam — o pódio tinha cartão dourado, linha #1 dourada e
botão dourado ao mesmo tempo.

A regra passa a ser:

> **Em cada ecrã, o dourado quer dizer uma coisa só.** Nos ecrãs que celebram um resultado
> (Pódio solo, Pódio multijogador, Ranking, Conquistas) o dourado é o **vencedor/mérito** e a
> ação primária passa a **Roxo** — que é, aliás, o que o mockup faz no ecrã 10. Em todos os
> outros ecrãs o dourado é a **ação primária**.

Os separadores/filtros **nunca** são dourados: não são ação nem mérito.

### Separadores partilhados (`ui/theme/Tabs.kt`)

Havia cinco desenhos de separador diferentes na app (Ranking tinha dois, um por cima do outro;
Histórico, Perfil, Conquistas e Amigos tinham cada um o seu), e três deles pintavam o
separador escolhido de **dourado** — a cor mais forte do ecrã, gasta num filtro.

Passam todos por dois componentes:

- **`SegmentedTabs`** — calha lavanda com contorno de tinta e pastilha **roxa** no separador
  activo (mockup, ecrãs 11 e 12). Nível principal.
- **`UnderlineTabs`** — só texto com um sublinhado roxo de 22×3 dp; é o mesmo sinal de "activo"
  que a barra de navegação inferior já usava. Nível secundário.

Ter dois níveis é o que permite ao **Ranking** empilhar dois filtros (modo + lista) sem que
compitam: o modo é pastilha cheia, a lista é sublinhado. Com quatro separadores (Histórico)
`SegmentedTabs` reduz o corpo do texto para 13 sp — a 16 sp "Eliminatórias" era cortada a meio.
Amigos mantém os contadores, agora dentro do rótulo ("Recebidos 2"), e o ponto coral de aviso
passou a ser um parâmetro do componente.

### Barra de XP: gradiente frio (`XpGradient`)

Pedido explícito. Era roxa lisa — igual a tudo o resto que é roxo. Passa a
**Teal → Azul → Roxo** (`Brush.horizontalGradient`).

O critério não foi só estético: é a única barra da app que **não** usa cores de estado. A barra
do tempo, no ecrã da pergunta, é lisa e vai de Teal a Dourado a Coral conforme o tempo acaba.
Com o gradiente frio nunca se confunde "quanto tempo falta" com "quanto XP falta". O Azul só
era usado pela categoria História, que nunca aparece nos ecrãs com barra de XP. O brilho
dourado pulsante acima dos 85 % mantém-se — sendo agora a única cor quente da barra, lê-se
inequivocamente como "estás quase a subir de nível".

### Pergunta (solo e multijogador) — o defeito mais sério que encontrei

As quatro opções nasciam pintadas de **Roxo / Coral / Teal / Dourado** (`AnswerPalette`), que
são exactamente as cores que a revelação usa para dizer **Teal = certa**, **Dourado = era esta**
e **Coral = erraste**. Uma opção **ainda por responder** aparecia verde ou vermelha e parecia
já corrigida. Em Verdadeiro/Falso era pior: "Verdadeiro" nascia verde e "Falso" vermelho, o que
insinua a resposta antes de o jogador escolher.

Novo componente partilhado `game/AnswerOption.kt`, usado pelo solo **e** pelo multijogador
(a lógica estava duplicada nos dois ecrãs):

- **Em repouso**: cartão lavanda neutro com um **emblema roxo A/B/C/D** (mockup, ecrã 9). A
  letra substitui a cor como forma de distinguir as opções.
- **Depois de responder**: o cartão toma a cor do resultado e o emblema passa a creme, para se
  ler por cima de qualquer uma delas. Em V/F o emblema mostra ✓/✗ — identifica a afirmação sem
  sugerir qual está certa.
- Transição de cor (280 ms), salto da opção certa e cascata de entrada mantêm-se; o
  multijogador ganhou-as por passar a usar o mesmo componente.

A **faixa do evento Caótico** deixou de ser dourada e passa a **roxa** com o raio dourado: por
cima das opções, uma faixa dourada permanente competia com o dourado da resposta revelada.

O marcador do multijogador ("Tu / Convidado") tinha o próprio jogador em **Teal**, verde ao
lado de opções que também usam verde para "certo". Passa a **contorno roxo sobre lavanda**, que
é o sinal de "sou eu" usado agora em todo o lado.

### Pódio (solo e multijogador)

- **Cartão de resultado com a cor do desfecho**: era sempre dourado com um troféu, mesmo em
  "Eliminado!" — celebrava uma derrota. Agora Dourado = vitória, **Coral = eliminado**,
  Lavanda = fim de jogo sem vitória, e o ícone troca de troféu para bandeira quando não se venceu.
- **Três números do fim de partida** (`game/ResultStats.kt`, mockup ecrã 10): **Perguntas ·
  Precisão · XP ganho**. O XP vem da mesma `Progressao.xpGanho` que o perfil usa para acumular,
  por isso o número mostrado é literalmente o que foi escrito na base de dados. Até aqui o
  jogador só via o XP depois, no Início, já somado ao total.
- **Melhores pontuações**: linhas todas neutras com **emblema dourado só no #1** (uma linha
  inteira dourada competia com o cartão de resultado) e **top 3 em vez de 5** — com cinco a
  lista era cortada a meio da quarta linha e uma linha meio visível por cima dos botões parece
  um erro de desenho. O quadro completo vive no Ranking.
- **JOGAR NOVAMENTE / NOVO JOGO passam a Roxo**, VOLTAR AO INÍCIO fica lavanda — a regra do
  dourado por ecrã.
- No pódio multijogador o próprio jogador distingue-se por **contorno roxo**, não por fundo
  Teal: "sou eu" e "ganhei" deixam de ser o mesmo tipo de sinal. A classificação também deixou
  de ter `weight(1f)`, que com dois jogadores abria um buraco de meio ecrã.

### Sala de espera / Encontrado!

- **CANCELAR PROCURA passa a Coral**. Era lavanda — a mesma cor de uma superfície neutra —
  apesar de ser a única ação do ecrã; e Coral é precisamente o que significa cancelar/abandonar
  no resto da app (é também o que o mockup faz, ecrã 5).
- **"● Pronto" foi removido.** Não existe ready-up nenhum nesta app: a partida arranca sozinha
  quando a sala enche. Dizer "Pronto" sugeria um passo que o jogador teria de dar. Passa a
  **"Na sala"** com um visto Teal — um significado só: este lugar está ocupado. (Antes o visto
  trocava de Dourado para Teal consoante a cor do cartão, sem querer dizer nada.)
- Lugar do próprio jogador e cartão do "Encontrado!": **contorno roxo**, não fundo Teal.

### Ranking

- Dois filtros, um só tom (ver acima). **O título da lista desapareceu** — repetia à letra o
  separador escolhido três linhas acima.
- Linhas ganham **avatar e o número de posição destacado**; o próprio jogador aparece com
  **contorno roxo e "(tu)"**, porque antes era preciso ler todos os nomes para se encontrar.
- **Encaixe creme por trás do avatar**: um avatar dourado (Pastel de Nata, Galo de Barcelos)
  em cima da linha dourada do 1.º lugar desaparecia — via-se só o contorno.

### Perfil

O botão de **editar nome** era um círculo dourado — exactamente igual ao CONQUISTAS logo
abaixo. Dois "primários" no mesmo ecrã, e o mais raro dos dois era o mais visível. Passa a
círculo de contorno (mockup, ecrã 14); CONQUISTAS fica como única ação dourada.

### Amigos

O botão **Desafiar**, em cada linha, era dourado — igual ao PROCURAR JOGADORES no topo. Passa a
**Roxo**: é uma ação por linha, de segundo nível. Aceitar (Teal) e Recusar/Cancelar (Coral)
mantêm-se, porque aí a cor é semântica.

### Categoria

Ganhou a **pastilha de contexto** do mockup (ecrã 4) com o formato escolhido — Solo, 1x1, 2x2
ou Grupo. A categoria escolhe-se **depois** do formato e o ecrã não lembrava para que tipo de
partida se estava a escolher. Os cartões continuam pintados com a cor da categoria: aí a cor é
**identidade** (reaparece no chip do Modo, no cabeçalho da pergunta e no Histórico), não ação,
e o ecrã não tem nenhum botão com que possa competir.

### Conquistas e Avatar

- Filtro em roxo: neste ecrã o dourado é o anel das conquistas desbloqueadas, e um separador
  dourado dizia "conquista" onde só havia um filtro.
- No **selector de avatar**, o escolhido marcava-se com um anel dourado — que **desaparecia**
  por cima dos símbolos que já são dourados (Pastel de Nata, Galo de Barcelos). Passa a um
  **emblema de visto** no canto, que se vê em qualquer uma das cores da paleta.

### Login e Registar

- **Login**: ganhou a **marca no topo** (mockup, ecrã 16) — é por aqui que muita gente vê a app
  pela primeira vez e o ecrã não dizia em lado nenhum como se chama o jogo. ENTRAR (dourado) e
  ENTRAR SEM CONTA (branco) tinham o **mesmo ícone**; o segundo passou a `play_arrow`.
- **Campos de palavra-passe ganharam o olho de revelar** (mockup, ecrãs 16/17). Além de ser o
  esperado, resolve um problema real já registado na Fase "Accounts + profile": o teclado do
  emulador engoliu um caractere e o registo falhou com "as palavras-passe não coincidem" sem
  que se conseguisse ver porquê.
- **Registar**: a ação primária era **roxa** enquanto o ENTRAR do Login era dourado — dois
  passos do mesmo fluxo de conta com pesos diferentes. Passa a **dourada** e o rótulo passa a
  "CRIAR CONTA" (era "CONTINUAR", que sugere um segundo passo que não existe). A regra da
  palavra-passe solta deu lugar ao **cartão de requisitos** do mockup, mas **só com as regras
  que a app valida mesmo** — 8 caracteres e as duas iguais. O mockup pedia ainda maiúscula e
  número; pô-las ali seria prometer uma validação que não existe.

### Divergências do mockup que continuam de pé

O mockup pinta o ecrã do jogo (9) de **fundo escuro**. Não foi seguido: o sistema visual da app
é creme em todos os ecrãs e um único ecrã escuro leria como outra aplicação. A legibilidade que
o fundo escuro dava às opções coloridas foi obtida de outra maneira — cartões neutros com
emblema de letra. As divergências listadas na Fase 11 (códigos de sala, nomes de patente,
contagens de perguntas por categoria, etc.) mantêm-se.

### Testado no emulador (2026-07-28)

Percorridos um a um, com captura de ecrã: Início, Formato, Categoria (com pastilha "Solo"),
Modo, Pergunta em Caótico (repouso e revelação, incluindo o `+195 pontos` a subir e o distintivo
de sequência), Pódio solo (com e sem vitória), Ranking (pastilha + sublinhado, "(tu)", avatares),
Histórico (quatro separadores sem cortar palavras), Perfil, Conquistas, Avatar, Amigos, Login e
Registar (com os dois requisitos a ficarem verdes ao escrever).

Multijogador em dois emuladores (Ana Costa × Convidado): **Sala de espera 1x1** ("1 / 2
encontrados", lugar próprio com contorno roxo e "Na sala", lugar vazio tracejado, CANCELAR
coral), **Encontrado!**, jogo 1x1 completo com o marcador e as opções novas, e **pódio**
verificado dos dois lados — "Vitória!" num, "Derrota" no outro, mesmas pontuações, `+170` /
`+60` XP conforme a fórmula. A procura foi cancelada no fim de cada ensaio para não deixar
estado pendente em `/matchmakingN`.

## Fase 19 — Animações específicas por ecrã (2026-07-28)

Depois da hierarquia de cor (Fase 18), esta fase acrescenta o movimento pedido explicitamente
ecrã a ecrã, sem tocar em paleta ou tipografia. A maior parte do vocabulário de movimento já
existia em `ui/theme/Motion.kt` (`cascadeIn`, `rememberPressScale`, `rememberPulse`,
`rememberGlow`, `stickerSpring`) de fases anteriores; esta fase acrescenta um helper novo e
aplica o conjunto aos ecrãs que ainda não o tinham.

### Regra dura: nada bloqueia o jogo

Reafirmada porque esta fase toca em mais ecrãs de estado (pódio, sala de espera): todas as
entradas são visuais (opacidade/escala/posição), nunca `enabled = false` enquanto animam. Um
toque no botão "NOVO JOGO" ou numa opção de resposta funciona mesmo a meio da cascata — testado
em ambos os pódios sem perder nenhum toque em ~2,4 s de intervalo entre jogadas.

### Novo: `Modifier.bounceIn` (`ui/theme/Motion.kt`)

Entrada de impacto — escala de 0.5 a 1 com mola (`stickerSpring`, com ultrapassagem), não um
`tween` linear como o `cascadeIn`. Reservada a um único momento: o "Encontrado!" do
multijogador, onde os adversários aparecem pela primeira vez e merecem mais peso do que uma
entrada em cascata normal.

### Início — barra de XP

Já tinha o preenchimento suave (`animateFloatAsState`, 700 ms) e o brilho dourado pulsante
perto do próximo nível, da Fase 18 (pedido de mudar a cor da barra). Nada a acrescentar aqui;
confirmado no dispositivo que a barra ainda enche suavemente com o gradiente novo.

### Categoria / Formato / Modo — cartões e toque

Idem: `cascadeIn` nos cartões e `rememberPressScale` no toque já vinham da Fase 18 (a correção
de hierarquia de cor introduziu-os ao mesmo tempo que a cor do emblema). Verificado que
continuam a funcionar sem regressão.

### Pergunta (solo e multijogador)

- **Temporizador com urgência crescente**: já existia no solo (Fase 18); **faltava no
  multijogador**, que tinha uma barra lisa da versão anterior à reestruturação de cor. Agora
  usa exactamente a mesma lógica — abaixo de 25% pulsa, o período desce de 1000 ms para 220 ms
  conforme o tempo esgota.
- **Feedback de resposta em transição suave**, **pontos "a subir"** e **streak com destaque
  crescente** já existiam (`AnswerOption`, o `+X pontos` a subir no solo, o distintivo de
  streak dourado→coral com pulso a partir de 5). Confirmados no dispositivo, sem alteração.

### Pódio (solo e multijogador)

- **Revelação em cascata**: resultado → estatísticas → classificação, cada bloco com
  `cascadeIn` num índice a seguir ao anterior (0, 2, 4+) em vez de tudo aparecer composto de
  uma vez. No solo: cartão de resultado (0) → `ResultStats` (2) → cada linha do top-3 (4, 5,
  6). No multijogador: equipas/classificação (0, 1, 2…) → `ResultStats` (a seguir ao último
  índice usado).
- **Vencedor com destaque mais forte**: o troféu do cartão de resultado pulsa (escala 1→1.14,
  900 ms) quando `won`; o #1 da lista de melhores pontuações também pulsa (1→1.10); no
  multijogador o troféu da equipa vencedora e a linha #1 da classificação individual pulsam da
  mesma forma. Um só padrão de "mérito" em toda a app.
- Testado no dispositivo, dos dois lados de uma partida 1x1: cascata visível ao chegar ao
  pódio, botões clicáveis de imediato, sem crash (confirmado sem `FATAL`/`AndroidRuntime` no
  logcat de nenhum dos dois emuladores).

### Sala de Espera / Encontrado!

- **Assentos animam ao preencher**: cada lugar (`SeatRow` no 1x1/Grupo, `CompactSeat` no 2x2)
  passou a estar dentro de um `AnimatedContent` chaveado pelo uid do ocupante — quando
  "À procura..."/"Vaga livre" dá lugar a um nome real, o lugar desvanece + cresce (scale-in de
  0.85 a 1) para o novo conteúdo em vez de trocar a seco. Os lugares continuam também a entrar
  em cascata na composição inicial do ecrã.
- **Entrada de impacto no "Encontrado!"**: jogadores (1x1/Grupo) e cartões de equipa (2x2) usam
  `bounceIn` — saltam para o lugar com mola, em vez de só aparecerem. É o único uso de
  `bounceIn` na app; o momento de ver os adversários pela primeira vez pesa mais do que uma
  entrada em cascata normal.

### Conquistas — desbloqueio com celebração

- Grade passou de `items` para `itemsIndexed`: cada cartão entra em cascata.
- **Conquistas desbloqueadas** ganham um **halo dourado a pulsar** por trás do círculo do
  símbolo (`rememberGlow`, 0.15–0.5 de opacidade) — o mesmo truque da barra de XP quase cheia,
  aqui a assinalar "mérito alcançado" em vez de "quase lá". Só as desbloqueadas o têm; as
  bloqueadas ficam sem brilho, coerente com o resto da app onde o dourado é sempre mérito.
- Todos os círculos (bloqueados e desbloqueados) têm ainda um **salto de entrada com mola**
  (`Animatable` de 0.6 a 1, `stickerSpring`), escalonado pelo mesmo índice do `cascadeIn` do
  cartão — o grão fica satisfatório a compor-se sem depender só da opacidade.

### Ranking — cascata ao abrir

Já tinha `cascadeIn` por linha desde a Fase 18 (parte da correção de hierarquia de cor, que
também trocou os separadores). Confirmado sem alteração adicional.

### Avatar — "pop" ao selecionar

Já implementado na Fase 18 (`animateFloatAsState` com `stickerSpring`, escala 1→1.08 no
símbolo escolhido, ver secção "Conquistas e Avatar" acima). Confirmado no dispositivo que o
salto continua satisfatório com o emblema de visto introduzido nessa fase.

### Testado no dispositivo (2026-07-28)

Um emulador: Início (XP a encher), Categoria/Formato/Modo (cascata + toque), Pergunta solo em
Caótico (temporizador a ficar coral perto do fim), pódio solo completo com #1 dourado. Dois
emuladores (Ana Costa × Convidado), 1x1 completo: sala de espera → "Encontrado!" com entrada de
impacto → jogo com temporizador com urgência em ambos os lados → pódio multijogador com
cascata e contorno roxo em "(tu)". `logcat` de ambos os emuladores sem `FATAL` nem
`AndroidRuntime` no período do teste. Nenhum toque foi perdido durante uma animação — em
nenhum momento foi preciso esperar mais do que a duração normal da transição (≤ 320 ms de
entrada, ≤ 280 ms de feedback) para o ecrã responder.

## Fase 20 — Auditoria de segurança das rules + remoção da Roda da Sorte (2026-07-28)

Esta fase não acrescentou funcionalidade nova: reviu duas features que tinham sido escritas
noutra sessão (Quizzes da Comunidade e Roda da Sorte Diária) e que estavam por commitar, e
tratou do que estava mal.

### Vulnerabilidade em `/categorias_comunitarias` — corrigida e verificada

A regra era esta:

```
".write": "auth != null && (!data.exists()
           || data.child('criadorUid').val() === auth.uid
           || newData.child('votos').exists())"
```

A intenção era "deixar quem não é dono escrever apenas votos". Mas a condição está no nó
`$catId` **inteiro**, não restrita ao sub-caminho `votos`: `newData.child('votos').exists()`
avalia sobre o payload completo. Bastava a qualquer conta autenticada fazer um `set` na raiz
do quiz com um payload fabricado que incluísse uma chave `votos` — e passava. Consequência
concreta: **qualquer jogador podia reescrever por completo o quiz de outro** (título,
descrição, perguntas) e até o campo `criadorUid`, ou seja, também falsificar a autoria.

Correção:

- `.write` no `$catId` passa a exigir dono (`!data.exists() || criadorUid === auth.uid`).
- `votos/$uid` ganha regra própria: só o próprio uid escreve o seu voto, validado a `1..5`.
- `mediaClassificacao` e `totalVotos` ficam escrevíveis por qualquer autenticado (é o que a
  app faz a seguir a votar), mas **só esses caminhos** — já não abrem a porta ao nó inteiro.
- `criadorUid` validado contra `auth.uid`, para não se poder criar um quiz em nome de outro.
- Limites de tamanho (`titulo` ≤ 80, `descricao` ≤ 300), schema fechado nas perguntas e
  `$other: false` a rejeitar campos desconhecidos.

**Verificado com tokens reais de duas contas de teste (não só com o que o telemóvel reporta):**

| Tentativa | Resultado |
|---|---|
| Dono cria quiz | permitido ✓ |
| Outro jogador reescreve o nó inteiro com `votos` no payload (o exploit) | `Permission denied` ✓ |
| Outro jogador muda só o `titulo` | `Permission denied` ✓ |
| Outro jogador vota | permitido ✓ |
| Outro jogador forja um voto no uid de terceiro | `Permission denied` ✓ |
| Voto fora de 1–5 | `Permission denied` ✓ |
| Outro jogador atualiza `mediaClassificacao` / `totalVotos` | permitido ✓ |
| Dono edita o próprio quiz | permitido ✓ |
| Criar quiz com `criadorUid` de outra pessoa | `Permission denied` ✓ |

E, do lado oposto (rules estritas de mais partirem o cliente), foram testados os payloads
**exactamente** como `CustomCategoryRepository` os escreve — `saveCategory` a criar,
`saveCategory` a editar, `togglePublicStatus`, e os três passos do `rateCategory`: todos
passam. Rules já em produção (`firebase deploy --only database`).

### Roda da Sorte Diária — removida

Estava escrita e a compilar, mas **nunca chegou a funcionar** e não estava ligada ao menu.
Removida por inteiro (ecrã, `GameScreen.DAILY_WHEEL`, `goToDailyWheel()`, o parâmetro
`onDailyWheelClick`, `ProfileRepository.recordWheelSpin()` e o campo `ultimaRodaSpinTs`).

Razões, por ordem de gravidade:

1. **Não funcionava.** `recordWheelSpin` escrevia `ultimaRodaSpinTs` em `/jogadores/{uid}`,
   campo que não existe nas rules — e esse nó tem `$other: {".validate": false}`. Toda a
   transação falhava com `Permission denied` logo à primeira rodada.
2. **Sem anti-cheat.** Mesmo corrigindo as rules, `xpTotal` só valida `isNumber() && >= 0`:
   sem tecto, sem verificação de que o incremento é um dos prémios possíveis, e sem
   confirmação das 24 h do lado do servidor (o cliente é que escreve o timestamp que depois
   se usa para o bloquear). Um cliente adulterado pedia o XP que quisesse, as vezes que
   quisesse. Contrasta com a disciplina do resto da app, onde as pontuações estão travadas
   ao máximo matemático precisamente por isto.
3. **Economia furada.** Oito prémios equiprováveis de 100 a 1000 XP dão ≈ 406 XP por dia só
   por abrir a app, contra os ~50–150 XP de um jogo jogado. Isso inverte a curva de níveis
   (`300 + (n-1)·150`) desenhada na Fase 12: subia-se mais a rodar do que a jogar.
4. **Fora do sistema visual.** Trazia cinco cores próprias (`NeonYellow`, `NeonCyan`,
   `NeonPink`, `ElectricOrange`, `RoyalPurple`) que não existem em `ui/theme/Color.kt`,
   ignorando a hierarquia de cor das Fases 18/19, e o texto dos gomos era desenhado a preto
   fixo por cima de roxo escuro (ilegível). A estética de slot machine também não assenta
   numa app de cultura geral.

Se algum dia voltar, tem de nascer com validação no servidor e com prémios equilibrados
contra a curva de XP — não é um trabalho de UI.

### Quizzes da Comunidade — diálogos passados ao sistema sticker

O ecrã em si (cartões, pastilhas de topo) já seguia o sistema, mas os três diálogos eram
`AlertDialog` do Material3 com `OutlinedTextField` e `RadioButton` — pareciam um formulário
Android colado dentro da app.

- Novo **`ui/theme/StickerDialog.kt`**: `Dialog` com `usePlatformDefaultWidth = false` e um
  `stickerBlock` por dentro, no mesmo registo do `ChallengeOverlay` que já existia. Substitui
  os três (`PlayCategoryOptions`, `JoinCode`, `CreateCategory`).
- `StickerTextField` em vez de `OutlinedTextField` em todos os campos. O parâmetro `icon`
  passou a **opcional**: nos campos de opção de resposta não há ícone, porque estavam os
  quatro com um ✓ à esquerda — parecia que todas as opções eram a correta.
- Seletor da resposta certa: **emblema A/B/C/D** (Teal quando escolhido) em vez de
  `RadioButton`, reutilizando a linguagem do ecrã da pergunta.
- Dificuldade passa a usar o `SegmentedTabs` partilhado, em vez de três caixas próprias.
- Botões dentro do construtor de perguntas passam ao `CompactActionButton` do próprio ecrã —
  o `StickerButton` normal (22 sp, 32 dp de padding) partia "+ INCLUIR PERGUNTA" em duas
  linhas na largura do diálogo.
- Botão de guardar desativado passa a **Neutral**: estava com um Ink translúcido que o
  pintava quase a preto, fazendo do elemento mais pesado do ecrã justamente aquele em que
  não se podia carregar.
- Cascata de entrada nos cartões de quiz e na lista de perguntas; feedback de toque nas duas
  pastilhas de topo (eram das últimas ações clicáveis da app sem ele).

### Testado (2026-07-28)

Build limpa; app instalada e aberta no emulador sem `FATAL EXCEPTION` nem `AndroidRuntime`
nos logs (as únicas excepções no `logcat` do período vinham do processo do YouTube, não da
app). Diálogos de jogar categoria, entrar por código e criar categoria abertos e fechados no
dispositivo. As rules foram exercitadas por REST com tokens reais, como descrito acima.

**Por fazer, identificado mas não corrigido nesta fase:**

1. `/lobbies` tem `.read` e `.write` a `auth != null` e mais nada — sem dono, sem schema,
   sem validação. Qualquer autenticado reescreve qualquer sala. É o mesmo tipo de problema
   que acabou de ser corrigido nas categorias.
2. `CustomCategoryRepository.rateCategory` lê os votos, calcula a média em memória e escreve
   `mediaClassificacao` e `totalVotos` em dois `setValue` separados, sem transação: dois
   votos em simultâneo perdem um.
3. Nada impede o criador de votar no próprio quiz (nem no cliente nem nas rules).

## Fase 21 — Mudança de `applicationId` para `com.ratoooooo.perguntaoluso` (2026-07-28)

O pacote era `com.starforge.app`, herdado do nome de trabalho do projeto e desalinhado da
marca. Como o `applicationId` é **permanente depois da primeira publicação na Play Store**
(mudá-lo obriga a uma ficha nova, com instalações e avaliações a zero), a mudança foi feita
agora, antes de submeter.

**Identidade nova:**

- `applicationId` = `namespace` = `com.ratoooooo.perguntaoluso` (`app/build.gradle.kts`).
- `versionCode` 1 / `versionName` "1.0" inalterados — a app nunca foi publicada, por isso
  não há linha de versões a preservar.
- `rootProject.name` continua `PerguntaOLuso`; `@string/app_name` continua "Pergunta ó Luso".

**Firebase:** foi registada uma app Android nova no mesmo projeto
(`firebase apps:create ANDROID "Pergunta o Luso" --package-name com.ratoooooo.perguntaoluso`),
App ID `1:516301571634:android:74f3383aba01795aa2de2b`, e o `app/google-services.json` foi
regerado com `firebase apps:sdkconfig`. O ficheiro passou a conter **dois** blocos `client`
(o novo e o antigo) porque a app `com.starforge.app` não foi apagada; o plugin
`com.google.gms.google-services` escolhe o bloco que corresponde ao `applicationId` em build,
por isso a coexistência é inofensiva.

Nada muda do lado dos dados: mesmo projeto, mesma RTDB, mesmas rules, mesma API key. As rules
são todas indexadas por `auth.uid`, nunca pelo package — as contas e o progresso existentes
continuam válidos.

**Código:** a árvore `app/src/{main,test,androidTest}/java/com/starforge/app/` passou a
`.../com/ratoooooo/perguntaoluso/`, e 433 declarações `package`/`import` foram reescritas
em 68 ficheiros `.kt`. Documentação (`README.md`, `TECHNICAL_SUMMARY_STARFORGE.md`)
atualizada nos caminhos de pacote.

**Verificação:** `assembleDebug` com sucesso; `aapt2 dump packagename` no APK devolve
`com.ratoooooo.perguntaoluso`; instalado no emulador e confirmado com
`adb shell pm list packages | grep perguntaoluso`. Partida solo completa (Cultura Geral,
Clássico, 10 perguntas) jogada ponta a ponta: categorias e perguntas lidas da RTDB, e a
escrita confirmada na base de dados — `/scores` com `score: 200, correctCount: 1, total: 10`
e `/jogadores/{uid}` com `pontos: 200`, `xpTotal: 60`, agregados por modo e por categoria.
Sem `Permission denied` e sem crashes no logcat.

**Por resolver, deliberadamente fora do âmbito desta fase:**

1. A app Android antiga (`com.starforge.app`) continua no projeto Firebase, por pedido — só
   deve ser removida depois de confirmada. Enquanto lá estiver, permanece no
   `google-services.json` a cada `apps:sdkconfig`.
2. `TECHNICAL_SUMMARY_STARFORGE.md` documenta 4 contas de teste reais em produção
   (`teste1@starforge.test` … `teste4@starforge.test`, password partilhada). Existem mesmo no
   Firebase Auth e devem ser apagadas antes do lançamento.
3. O mesmo documento descreve `DailyWheelScreen.kt`, que não existe na árvore — a Roda da
   Sorte foi removida na Fase 20 e o resumo técnico não acompanhou.

## Fase 22 — Backdoor de auto-login, contas de teste e endurecimento das rules (2026-07-28)

Fase de segurança antes da submissão à Play Store. Três frentes: um backdoor de autenticação
no código, contas de teste reais em produção, e exposições nas rules da RTDB.

### 1. Backdoor de auto-login (removido)

`MainActivity` lia `login_email` e `login_password` dos extras do Intent que a lançava, e
`AuthGate` autenticava com eles em silêncio antes do sign-in anónimo. Como `MainActivity` é
`exported="true"` — obrigatoriamente, é a LAUNCHER — qualquer app instalada no dispositivo, ou
qualquer pessoa com adb, forçava a app a entrar numa conta arbitrária:

```
adb shell am start -n com.ratoooooo.perguntaoluso/.MainActivity -e login_email X -e login_password Y
```

Credenciais em extras de Intent aparecem ainda por cima em logs e traces do sistema em várias
ROMs. Era um atalho de QA para automatizar screenshots. Removido por completo: `AuthGate`
passou a `AuthGate(content:)` sem parâmetros e faz só `ensureSignedIn()`. O login manual
(`LoginScreen` → `GameViewModel` → `AuthRepository.loginWithEmail`) não foi tocado.

Automação de login futura tem de ficar atrás de uma flag de compilação em builds de debug
(`BuildConfig.DEBUG` + `buildConfigField`), nunca a ler credenciais de um componente exportado.

**Verificado no dispositivo:** com a app já a correr, `am start` com os dois extras é entregue
(`intent has been delivered to currently running top-most instance`) e ignorado — a sessão
continua "Convidado" anónima com 0 pontos.

### 2. Contas de teste em produção

`teste1@starforge.test` a `teste4@starforge.test` partilhavam uma password fraca [não
documentada aqui por segurança], que estava em texto simples no
`TECHNICAL_SUMMARY_STARFORGE.md`. A password foi retirada do
documento e as 4 contas foram **desativadas** no Firebase Auth (`disabled: true`) — um
sign-in devolve `USER_DISABLED`, portanto a password que circulou já não abre nada.

**Por terminar:** a eliminação definitiva das 4 contas ficou por fazer. A CLI do Firebase não
tem `auth:delete`, e a via administrativa exigia extrair o refresh token do config store da
CLI — bloqueado, e bem, por parecer exfiltração de credenciais. São 4 cliques na consola:
Authentication → Users → ⋮ → Delete account. Desativadas já não são um risco, mas continuam a
contar como contas.

Dados associados eliminados da RTDB: 4 perfis, 43 scores, 4 nós de `/amigos`, e o perfil
anónimo do teste de rename da Fase 21. `teste1` era dono do **único quiz da comunidade** do
projeto ("Memes & Cultura Pop Pt"); foi apagado com a conta, por decisão explícita — deixá-lo
tornava-o permanentemente órfão, já que `.write` exige `criadorUid === auth.uid` e o uid
deixaria de existir. `/categorias_comunitarias` ficou vazio.

### 3. Rules — exposições fechadas

| # | Caminho | Antes | Agora |
|---|---------|-------|-------|
| 6 | `/jogadores` | `.read: true` | `.read: auth != null` |
| 7 | `/categorias` | `.read: true` | `.read: auth != null` |
| 8 | `/lobbies` | `.read`/`.write` abertos, sem schema | dono por nó + schema fechado |
| 9 | `notify/$uid` | `.write: auth != null` | só o anfitrião real da sala anunciada |
| 10 | `/multisalas/$salaId` | `.read: auth != null` | só membros da sala |
| 11 | agregados de votos | `.write: auth != null` | só quem votou, com transação no cliente |
| 12 | `jogadores/$uid/nome` | sem limite | `length <= 40` |
| — | `/matchmaking`, `/salas1x1` | abertos a autenticados | **removidos** (código morto) |

**`/categorias` obrigou a uma correção no código.** `CategoryRepository.loadCategories` faz um
GET REST direto (`categorias.json?shallow=true`) para não descarregar as 964 perguntas
aninhadas, e não enviava credencial nenhuma — fechar a leitura partia o ecrã de categorias.
Passou a obter o ID token do utilizador e a enviá-lo em `auth=`. Sem isto o jogo ficava
inutilizável logo no segundo ecrã.

**`/lobbies` — dono por nó com escrita por ascendente.** `findOrCreateLobby` corre uma
transação sobre `lobbies/$formato` inteiro (lê todas as salas, escreve na escolhida), por isso
o `.write` tem de ser concedido nesse nível — regras `.write` por sala nunca chegariam a ser
avaliadas. O controlo de dono é feito por `.validate`, que ao contrário do `.write` corre em
todos os descendentes mesmo quando a escrita entra por cima: `membros/$uid` valida
`auth.uid === $uid || data.exists()`, ou seja, só te podes inscrever a ti próprio, e entradas
já existentes passam intactas para a transação poder reescrever o nó. `hostUid` tem de constar
dos `membros` dos dados resultantes. `$other: false` fecha o schema.

**Votos com transação.** `rateCategory` lia os votos, calculava a média em memória e escrevia
`mediaClassificacao` e `totalVotos` em dois `setValue` — dois votos simultâneos perdiam um. O
voto passou a entrar num `runTransaction` sobre o nó `votos`, e os agregados são derivados do
snapshot contra o qual o voto fez commit. Os agregados vão num `updateChildren` multi-caminho:
a RTDB valida cada folha em separado, por isso não é preciso escrita no nó do quiz, que
continua reservado ao dono.

**`/multisalas` — leitura só por membros.** O `.read` testa
`data.child('meta').child('membrosNomes').child(auth.uid).exists()`, e `meta` passou a exigir
`membrosNomes` (sem ela a sala seria ilegível) e `hostUid === auth.uid` na criação.

### Testes — 43 verificações com tokens reais por REST

Utilizadores anónimos criados por `accounts:signUp`, e cada regra atacada por fora antes de se
confirmar o caminho legítimo. Todas passaram. As mais relevantes:

- `/jogadores` e `/categorias` sem sessão: `401 Permission denied`. Com sessão: `200`.
- B lê `multisalas/{sala}/meta/perguntas` de uma sala onde não é membro: negado — era assim que
  se liam as `respostaCorreta` de qualquer partida alheia.
- A cria sala com `hostUid` de outro, ou sem `membrosNomes`: negado.
- A notifica B para uma sala onde B não é membro, ou para uma sala inexistente, ou C notifica
  em nome do anfitrião: negado. O anfitrião real a notificar um membro real: permitido.
- Lobby com membro injetado, com anfitrião não-membro, com campo desconhecido, ou com `estado`
  fora do enum: todos negados. B a apagar o nó de formato inteiro: negado.
- B a pôr a média de um quiz a 5 sem ter votado: negado. Depois de votar: permitido. B a votar
  em nome de A, ou a reescrever o quiz de A: negados.
- Escritas em `/matchmaking/waiting` e `/salas1x1`: negadas (regras removidas).

Regressão no dispositivo depois do deploy: partida solo completa (Cultura Geral, Clássico),
categorias e perguntas carregadas, **745 pontos / 3 certas** escritos em `/scores` e no perfil,
sem `Permission denied` no logcat.

### Por resolver, identificado mas não corrigido nesta fase

1. **As respostas continuam a ser validadas no cliente.** Fechar a leitura de `meta.perguntas`
   aos não-membros era o que dava para fazer com rules. Esconder `respostaCorreta` dos próprios
   jogadores é impossível nesta arquitetura: a pontuação é calculada no dispositivo
   (`writeAnswer` recebe `correct: Boolean` já decidido), por isso o cliente *tem* de ler a
   resposta. Validação real exige Cloud Functions — o cliente enviaria a escolha e a função
   devolveria certo/errado e escreveria a pontuação. Os tetos de pontuação nas rules continuam
   a ser o único travão a batota.
2. **`/lobbies`**: `.validate` não corre em apagamentos, por isso um autenticado ainda consegue
   apagar a sala de outro (não o nó de formato inteiro, que ficou protegido). Fechar isto
   implica mudar `findOrCreateLobby` para transacionar por sala em vez de sobre o formato.
3. **`matchmakingN/$format/pending`** continua `.read`/`.write` a `auth != null` sem schema —
   fora do âmbito pedido, mas é a última fila de matchmaking aberta.
4. **`loadAllProfiles` e `loadMyScores`** continuam a descarregar `/jogadores` e `/scores`
   inteiros para filtrar no cliente. Não é segurança, é escala e custo (ver levantamento de
   pré-lançamento). Falta `.indexOn` em `pontos`/`uid` e queries no servidor.
5. **25 salas em `/multisalas`** e filas em `/matchmakingN` de sessões de QA antigas, sem TTL
   nem limpeza. A RTDB não expira nada sozinha.
6. **6 contas anónimas** criadas durante os testes desta fase ficaram no Auth, sem dados
   associados — mesmo tipo de resíduo que qualquer instalação nova produz.

## Fase 23 — Eliminação de conta self-service (2026-07-29)

Bloqueador de submissão identificado no levantamento de pré-lançamento: a Play Store exige
que qualquer app que permita criar conta ofereça eliminação **dentro da app**, sem passar por
suporte. Não existia nada — o Perfil só tinha "Terminar sessão".

### UI

Botão **Eliminar conta** no fim do Perfil, Coral cheio, separado de "Terminar sessão" por um
`HorizontalDivider` e 28 dp de espaço. É o único elemento do ecrã em Coral cheio: terminar
sessão é recuperável, isto não é, e as duas não podem parecer a mesma classe de ação. Aparece
também para jogadores anónimos — o perfil, o histórico e os quizzes deles são dados pessoais
na mesma, e a Play não distingue.

O diálogo (`StickerDialog`) enumera exactamente o que vai desaparecer e exige que se **escreva
`ELIMINAR`** para desbloquear o botão, que fica Neutral até lá. Um segundo botão
"tem a certeza?" aceita-se por reflexo; escrever a palavra obriga a ler.

### Purga (`data/AccountDeletionRepository.kt`)

Todos os caminhos são recolhidos primeiro e escritos numa **única `updateChildren` a partir da
raiz**. A RTDB valida cada folha em separado, por isso ou a limpeza inteira entra ou não entra
nenhuma — nunca há um estado com o perfil apagado e as arestas de amizade a apontar para ele.

| Item | Caminho |
|---|---|
| Perfil agregado | `jogadores/{uid}` |
| Presença | `presenca/{uid}` |
| Histórico | `scores/{k}` para cada registo com `uid` igual (filtrado no cliente — não há índice por `uid`, mesmo padrão do `loadMyScores`) |
| Amigos, meu lado | `amigos/{uid}/{lista,pedidosEnviados,pedidosRecebidos}/{outro}` |
| Amigos, lado do outro | `amigos/{outro}/{lista,pedidosEnviados,pedidosRecebidos}/{uid}` |
| Convites, meu lado | `convites/{uid}/{enviados,recebidos}/{outro}` |
| Convites, lado do outro | `convites/{outro}/{enviados,recebidos}/{uid}` |
| Quizzes criados | `categorias_comunitarias/{id}` onde `criadorUid === uid` |

**Dois detalhes que as rules impuseram ao desenho:**

1. **Não dá para apagar `/amigos/{uid}` nem `/convites/{uid}` inteiros.** Nenhum dos dois tem
   `.write` ao seu próprio nível — as rules só concedem escrita nos sub-caminhos. Um
   `"amigos/$uid" to null` seria negado. A limpeza é feita aresta a aresta e a RTDB deita fora
   o pai quando o último filho sai.
2. **O lado do outro jogador limpa-se sem o ler.** `/amigos/{outro}` só é legível pelo dono,
   por isso não há como enumerá-lo — e não é preciso: as **minhas** listas já nomeiam todas as
   contrapartes, e as rules deixam cada parte remover-se das estruturas da outra
   (`amigos/$uid/lista/$outro` aceita `auth.uid === $outro && !newData.exists()`). Apagar um
   caminho inexistente é inócuo, por isso apagam-se às cegas as três arestas por contraparte.
   Sem isto o amigo ficava com uma entrada morta: um nome na lista a apontar para um uid sem
   perfil.

### Quizzes da comunidade: eliminados, não anonimizados

Anonimizar preservaria conteúdo que outros possam ter gostado, mas exigia mexer na regra de
propriedade — `.write` em `categorias_comunitarias/$catId` exige `criadorUid === auth.uid`, e
com o uid extinto o quiz ficaria **permanentemente não-editável e não-moderável**. É exactamente
o problema que levou a apagar o quiz da conta de teste na Fase 22. Mantida a mesma decisão, por
consistência e para não acumular conteúdo sem dono num backlog de moderação que não existe.

### Ordem e reautenticação

`purge()` corre **antes** de `FirebaseUser.delete()`: depois do delete o uid deixa de poder
escrever seja o que for, e tudo o que ficasse para trás ficaria inalcançável para sempre.

O Firebase recusa `delete()` com `FirebaseAuthRecentLoginRequiredException` quando a sessão é
antiga. Esse caso é apanhado e o diálogo **fica aberto**, agora com um campo de palavra-passe,
em vez de fechar sem explicação; `AuthRepository.reauthenticateWithPassword` corre antes de nova
tentativa. A purga é idempotente, por isso repeti-la na retentativa é inofensivo.

**Janela conhecida:** se a reautenticação for pedida e o utilizador desistir aí, os dados já
saíram mas a conta continua a existir, vazia. É recuperável (basta repetir), mas é um estado
possível. Fechá-lo exigiria reautenticar *antes* da purga, o que obrigaria a pedir a
palavra-passe a toda a gente, incluindo a quem não precisa.

### Testado no dispositivo com um cenário completo

Vítima anónima "Vitima Teste" (`8v6C8y…`) montada com **todas** as formas de referência que
existem, três contrapartes criadas por REST com tokens reais:

- amizade aceite com P1 (escrita nos dois lados, pela app)
- pedido recebido pendente de P2
- pedido enviado pendente para P3 (pela app)
- desafio pendente de P1 em `/convites`
- 1 partida jogada (80 pontos) → registo em `/scores` + perfil agregado
- 1 quiz próprio criado na app ("Quiz da Vitima", `criadorUid` = vítima)

Antes: 8 estruturas da vítima presentes + 4 referências do lado de terceiros. Depois de
`ELIMINAR`: **todas a zero, incluindo as quatro de terceiros** — `P1.lista`,
`P1.convites.enviados`, `P2.pedidosEnviados` e `P3.pedidosRecebidos` deixaram de conter a
vítima. `auth_get_users` para o uid devolve `{"users":[]}`. Sem `Permission denied` e sem
`FATAL` no logcat.

A proteção do diálogo foi atacada antes: botão premido **com o campo vazio** e depois com
`"apagar"` — nos dois casos o perfil continuou intacto na base de dados. Só com `ELIMINAR`
escrito é que a eliminação correu.

### Por resolver

1. **O ramo de reautenticação não foi exercitado.** Só é alcançável por contas
   e-mail/palavra-passe com sessão antiga, e o teste correu com uma conta anónima — criar uma
   conta com palavra-passe para o teste está fora do que posso fazer. O código trata o
   `FirebaseAuthRecentLoginRequiredException`, mas isso é análise, não observação.
2. **A Play Store exige também um URL web de pedido de eliminação**, além do fluxo na app, para
   o formulário Data Safety. Continua por fazer — não é trabalho de app.
3. **`/scores/$scoreId` tem `.write: "auth != null"`**: qualquer autenticado apaga o registo de
   qualquer outro. É o que permite esta purga funcionar, mas é largo de mais. Fechar implica
   validar `data.child('uid').val() === auth.uid` no apagamento, o que a Fase 22 não tocou.
4. Continua sem haver **backfill** para contas eliminadas antes desta fase — os órfãos de
   sessões antigas de QA ficam onde estão.

## Fase 24 — Regra de `/scores`, assinatura de release e páginas legais (2026-07-29)

### 1. `/scores/$scoreId` — buraco fechado

`.write` era `auth != null`. Como o `.validate` **não corre em apagamentos**, nada travava um
autenticado a apagar o registo de pontuação de qualquer outro jogador. Foi descoberto na Fase 23,
precisamente porque era isso que fazia a purga de conta funcionar.

```
".write": "auth != null && ((!data.exists() && newData.child('uid').val() === auth.uid)
                         || (data.exists()  && data.child('uid').val()    === auth.uid))"
```

Criar exige que o registo traga o próprio uid; alterar ou apagar exige que o registo **já** seja
meu. A eliminação de conta continua a funcionar — só apaga os próprios registos.

**Efeito colateral aceite:** 12 dos 43 registos são de versões antigas e não têm campo `uid`.
Deixam de ser apagáveis por ninguém. Não são atribuíveis a nenhuma conta, por isso ficam só de
leitura — e já antes desta fase a purga não os conseguia identificar.

Testado com tokens reais, 8/8: A cria o seu (permitido), A cria com uid de B (negado), **A apaga
o de B (negado)**, A altera o de B (negado), sem sessão apaga (negado), **A apaga o seu
(permitido)**, B apaga o seu (permitido).

### 2. Assinatura de release

**Play App Signing**, decidido explicitamente. A Google guarda a chave definitiva; localmente só
existe a **chave de upload**. Se a de upload se perder, pede-se reset à Google e não se perde a
app — com assinatura própria tradicional, perder a chave significa perder para sempre a
capacidade de atualizar, e obriga a publicar ficha nova. É também o que o formato `.aab` exige.

- `app/build.gradle.kts`: `signingConfigs { create("release") }` lê de `keystore.properties`
  (gitignored) ou, em alternativa, das variáveis `POL_STORE_FILE`, `POL_STORE_PASSWORD`,
  `POL_KEY_ALIAS`, `POL_KEY_PASSWORD`.
- Sem chave configurada, o release compila na mesma e sai **por assinar** — deliberadamente, em
  vez de cair no certificado de debug: um APK assinado em debug instala por `adb` e é recusado
  pela Play Store, o que daria um falso positivo tardio.
- `keystore.properties.example` documenta o formato. O ficheiro real e o `.jks` nunca são
  commitados (`.gitignore` linhas 95–97).
- `isMinifyEnabled = true` + `isShrinkResources = true`.

**ProGuard/R8** (`app/proguard-rules.pro`): a app **não** usa desserialização por reflexão da
RTDB — todos os `getValue()` são sobre primitivos e as escritas são sempre `Map`, por isso a
forma clássica de uma app Firebase rebentar só em release não se aplica aqui. As regras são
conservadoras: `-keepclassmembers` nos modelos de `data.**` como seguro caso se venha a usar
`getValue(Foo::class.java)`, atributos que o SDK da RTDB precisa, e `SourceFile`/`LineNumberTable`
para que um stack trace de produção continue desofuscável pelo `mapping.txt`.

Resultado: `assembleRelease` com sucesso, **2,36 MB** contra 18,38 MB do debug (−87 %).

**Por terminar:** o keystore não foi gerado. Gerar exige escrever uma palavra-passe, o que não
faço — e não devia ser eu a escolhê-la. O comando fica no README/resumo para ser corrido
manualmente. Sem ele não é possível instalar o APK de release e confirmar que o R8 não parte
nada em runtime; a análise acima é análise, não observação.

### 3. Páginas legais publicadas

Escritas a partir do que a app recolhe **hoje**, verificado no código: dependências são só
`firebase-database-ktx` e `firebase-auth-ktx` (sem analytics, sem publicidade, sem Crashlytics),
e as permissões são `INTERNET`, `ACCESS_NETWORK_STATE` e `READ_GSERVICES`.

- <https://ratoooooo.github.io/pergunta-o-luso/privacidade.html>
- <https://ratoooooo.github.io/pergunta-o-luso/eliminar-conta.html>

Servidas por GitHub Pages a partir de `/docs` no branch **`gh-pages`**, criado a partir de
`origin/main` num worktree isolado. Isto foi deliberado: o `main` local tinha um commit por
enviar (`ebc8984`) e renomeações já no index, e um commit normal teria arrastado ambos.
`.nojekyll` porque são páginas HTML puras.

A política é explícita sobre o que fica visível a outros jogadores — nome, avatar, nível, stats,
estado online e conteúdo dos quizzes — e sobre o email **nunca** ser mostrado nem guardado na
base de dados de jogo. Contacto: `dinisrato07@gmail.com`.

### Descoberto por acaso, não corrigido

**`/salas_privadas` não tem regra nenhuma** em `database.rules.json`. O RTDB nega por omissão,
logo `createPrivateRoom` (`MultiMatchRepository:498`) falha sempre: **criar sala privada por
código está partido em produção**. Pré-existente, sem relação com esta fase, mas ia com a
submissão.

## Fase 25 — Salas privadas repostas a funcionar + moderação dos Quizzes (2026-07-29)

### Parte 1 — `/salas_privadas` estava partido por QUATRO razões, não uma

O ponto de partida era "falta a regra". Ao ir ver o código apareceram mais três, duas delas
**regressões introduzidas por mim na Fase 22** — o endurecimento das rules partiu uma
funcionalidade que ninguém testou porque já estava partida pela primeira razão.

| # | Defeito | Origem |
|---|---------|--------|
| 1 | `/salas_privadas` sem regra nenhuma → RTDB nega por omissão | pré-existente |
| 2 | Schema de `/lobbies` sem `id` nem `codigo`, com `$other: false` a rejeitá-los → a criação do lobby falhava | **Fase 22** |
| 3 | Quem entra pelo código não ficava em `meta.membrosNomes`, e a regra de leitura da Fase 22 exige constar dela → entrava no lobby e não conseguia LER a sala | **Fase 22** |
| 4 | `room.perguntas.size == QUESTION_COUNT` como condição de arranque → qualquer quiz sem exactamente 10 perguntas ficava eternamente em "À procura de adversário" | pré-existente |

**Rules novas.** `/salas_privadas` é uma tabela `codigo -> {lobbyId, format}`. Não há `.read` na
colecção, só em `$codigo`: dá para consultar um código que se conheça, nunca para listar os
existentes. Create-once, para ninguém repontar um código em uso. E quem regista o código tem de
ser o anfitrião real do lobby referido.

**`meta/membrosNomes/$uid`** passou a ser escrivível pelo próprio, uma só vez, e **só se já
constar dos `membros` do lobby com o mesmo id** (numa sala privada o id da multisala é o do
lobby). Sem essa condição qualquer autenticado se acrescentava a qualquer sala e passava a ler
as `respostaCorreta` alheias — precisamente o que a Fase 22 tinha fechado.

**Código.** A ordem passou a ser lobby → código → sala, porque a regra do código exige que o
lobby já exista. O código ganhou **retentativa**: só há 9000 códigos de 4 dígitos e, sendo agora
create-once, uma colisão falha em vez de repontar silenciosamente a sala de outra pessoa. O
arranque passou a usar `room.perguntas.isNotEmpty()` e o número **real** de perguntas da sala
(`totalPerguntas`) no avanço e no `total` gravado em `/scores` — antes uma partida de 7 perguntas
teria terminado cedo e gravado `total: 10`. As perguntas são limitadas a `MAX_PERGUNTAS_SALA`
(10) porque as rules travam `respostasCertas <= 10`: um quiz maior fazia a gravação do resultado
ser recusada **depois** de já se ter jogado.

**Testado por REST com tokens reais (18/18)** e **em 2 emuladores**: anfitrião cria sala por
código (5735), convidado entra pelo código, ambos passam a constar de `membros` e de
`membrosNomes`, o jogo arranca ("Pergunta 1 de 1" nos dois) e chega ao pódio com as pontuações
gravadas. Sem `Permission denied` em nenhum dos caminhos desta fase.

Negado nos testes: registar código para o lobby de outro; repor um código já usado; listar todos
os códigos; consultar sem sessão; ler a sala antes de entrar; inscrever-se em `membrosNomes` sem
estar no lobby; inscrever outra pessoa; ler `meta/perguntas` de fora.

### Parte 2 — Moderação mínima viável dos Quizzes da Comunidade

**Isto não é um sistema de moderação.** Não há painel de administração, não há fila de revisão,
não há moderadores. A revisão é manual, na consola do Firebase. O que existe é o mínimo que a
Play Store exige por haver conteúdo gerado por utilizadores visível publicamente, e serve para
tirar spam evidente de circulação depressa — não para arbitrar disputas.

**Denúncias** (`/denuncias/{quizId}/{uid} = {motivo, ts}`): botão de bandeira em cada quiz,
visível a toda a gente **excepto ao autor**. Uma denúncia por pessoa por quiz, irreversível
(`!data.exists()` nas rules). Não há `.read` na colecção nem em `$quizId` — cada pessoa só lê a
**sua** denúncia, o que serve para a app mostrar "Já denunciaste" em vez de deixar carregar num
botão que ia falhar. Ninguém consegue ver quem denunciou o quê.

**Auto-ocultação a 3 denúncias** (`DENUNCIAS_PARA_OCULTAR`). Três é um compromisso: mais baixo
deixa uma ou duas pessoas silenciarem conteúdo legítimo; mais alto deixa spam à vista tempo de
mais numa app sem moderadores. O quiz **não é apagado** — passa a `publica = false`, o autor
continua a vê-lo em "As Minhas" e pode voltar a publicá-lo, e o nó fica na base de dados para
revisão manual. Contador incrementado por transacção, como no `rateCategory`.

**Filtro de linguagem** (`ProfanityFilter`): lista estática verificada antes de gravar, no
repositório e não só no ecrã, para nenhum caminho a contornar por engano. Comparação com
fronteiras de palavra sobre texto normalizado (minúsculas, sem acentos) — nunca `contains`.

Os testes unitários apanharam um problema meu que teria sido mau em produção: a primeira lista
bloqueava **`cabra`** e portanto recusava "cabra-cega", o jogo. Removidos por causarem falsos
positivos numa app de cultura geral portuguesa: `cabra` (o animal, o jogo), `burro` (o animal),
`puto` (em Portugal significa miúdo e não é ofensivo), `broche` (a peça de joalharia), `corno`
(o do animal, o corne inglês), `idiota` ("O Idiota" de Dostoiévski) e `bicha` (também é fila).
Recusar uma pergunta legítima sem o autor perceber porquê é pior do que deixar passar um
palavrão — para esse ainda há a denúncia.

**Limitação conhecida do contador.** As rules da RTDB não têm `numChildren()`, por isso é
impossível validar que `totalDenuncias` bate certo com o número real de denúncias. A mitigação é
o incremento estar limitado a `+1` por escrita e exigir uma denúncia registada de quem escreve;
mesmo assim, alguém determinado consegue chamá-lo repetidamente e forçar a ocultação de um quiz.
O impacto é de disponibilidade, não de perda de dados: o autor vê-o e republica-o. Fechar isto
como deve ser exige Cloud Functions.

**Nota de implementação que custou tempo:** a primeira versão do `.validate` do contador era
`newData.val() === data.val() + 1 || (!data.exists() && newData.val() === 1)` e negava tudo,
incluindo o dono. Nas rules da RTDB, aritmética sobre `null` (o `data.val()` de um nó
inexistente) invalida a **expressão inteira**, mesmo do outro lado de um `||`. A versão que
funciona usa ternário para nunca avaliar a soma nesse caso:
`data.exists() ? newData.val() === data.val() + 1 : newData.val() === 1`.

**Testado por REST (15/15)** e **em 2 contas no dispositivo**: B denuncia o quiz de A pelo botão
(contador a 1, ainda pública); B a tentar de novo vê "Já denunciaste este quiz"; o autor não tem
botão de denúncia no seu próprio quiz; ao chegar a 3 o quiz passa a `publica = false` e some de
"Explorar Públicas" para toda a gente, continuando em "As Minhas (1)" para o autor. O filtro foi
exercitado no dispositivo com o título "Quiz de merda": bloqueado com a mensagem *"Não podes usar
a palavra «merda»"* e **nada foi gravado**.

### Por resolver

1. **O código de sala não é um segredo forte.** `/lobbies` tem `.read: auth != null` para o
   matchmaking aleatório funcionar, e o `codigo` está lá dentro. Quem enumerar lobbies vê códigos.
   A protecção real do conteúdo é a pertença ao lobby, não o segredo do código. Fechar isto
   implicaria tirar `codigo` do lobby e repensar a listagem de salas abertas.
2. **`/salas_privadas` não tem expiração.** Os códigos acumulam-se e, com 9000 combinações, a
   probabilidade de colisão cresce com o tempo. Há retentativa, mas sem limpeza acaba por esgotar.
3. **Revisão de denúncias é manual na consola.** Sem painel, sem notificação, sem histórico de
   decisões.
4. **O filtro é uma lista estática.** Contorna-se trivialmente e não apanha ofensas escritas sem
   palavrões.

## Fase 26 — Primeiro build de release assinado, e o R8 verificado em runtime (2026-07-30)

A Fase 24 deixou a configuração de assinatura pronta mas o keystore por gerar, e por isso o R8
tinha sido **analisado, nunca observado**. Com o `upload-keystore.jks` e o `keystore.properties`
já criados, esta fase fecha esse buraco.

### Assinatura

`keystore.properties` confirmado com as quatro chaves preenchidas (`storeFile`, `storePassword`,
`keyAlias`, `keyPassword`), `storeFile` a resolver para um ficheiro existente e alias `upload`.
As passwords não são registadas em lado nenhum — que estejam corretas prova-se por o build
assinar, não por as ler.

`assembleRelease` produz agora **`app-release.apk`** (e não `app-release-unsigned.apk` como na
Fase 24). `apksigner verify` confirma: assinado com APK Signature Scheme v2, RSA 2048,
`O=perguntaOLuso, L=Ribamar, ST=Portugal, C=PT`, SHA-256 `381d64ef…a510`.

**2,26 MB** contra 18,09 MB do debug — menos 87 %.

O `mapping.txt` do R8 fica em `app/build/outputs/mapping/release/`. **Tem de ser guardado a cada
release publicado**: sem ele, um stack trace de produção é ilegível.

### O R8 partiu mesmo alguma coisa — e a análise da Fase 24 estava incompleta

O primeiro arranque do release registou:

```
W ComponentDiscovery: Could not instantiate com.google.firebase.auth.ktx.FirebaseAuthLegacyRegistrar
W ComponentDiscovery: Caused by: java.lang.NoSuchMethodException: ...FirebaseAuthLegacyRegistrar.<init> []
```

O SDK do Firebase descobre componentes lendo nomes de classe do AndroidManifest e instanciando-os
por **reflexão**, com o construtor sem argumentos. O R8 não vê essas chamadas e remove o
construtor. A Fase 24 concluiu "risco baixo porque não há desserialização por reflexão da RTDB" —
correto quanto aos *modelos de dados*, mas passou ao lado deste segundo uso de reflexão, na
descoberta de componentes.

**Impacto funcional neste caso: nenhum.** O `FirebaseAuthLegacyRegistrar` é um shim de
compatibilidade do ktx que só regista a versão da biblioteca; foi a única classe afetada, e o
`FirebaseAuthRegistrar` e o `DatabaseRegistrar` carregaram bem. Mas carregaram **por acaso**, não
por desenho — é o mesmo mecanismo. Regra acrescentada para o tornar determinístico:

```
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
```

Depois disto: **zero** avisos `ComponentDiscovery`.

### Verificado em runtime, no APK de release assinado

Debug desinstalado primeiro (assinaturas diferentes impedem o update). Duas partidas solo
completas, uma antes e outra depois da correção das regras:

| | 1.ª (antes da regra) | 2.ª (depois) |
|---|---|---|
| Categorias carregam | sim | sim |
| Pontuação no pódio | 180 · 1/10 | 490 · 3/10 |
| Escrito em `/scores` | 180, correctCount 1, total 10, 8 campos | 490, correctCount 3, total 10 |
| `/jogadores` agregado | pontos 180, xpTotal 60 | pontos 490, xpTotal **80** |
| Avisos `ComponentDiscovery` | 1 classe | **0** |

O XP bate certo com `Progressao.xpGanho` (50 base + 3×10 acertos = 80), o que confirma que a
lógica de progressão sobreviveu à ofuscação. Sem `FATAL EXCEPTION`, sem `ClassNotFoundException`,
sem `Permission denied`. As linhas `AndroidRuntime` no logcat são do processo `uiautomator`
(uid 2000), a ferramenta de teste, não da app.

Que as **categorias carreguem** é a prova mais relevante: `CategoryRepository.loadCategories`
obtém o ID token pelo SDK e faz um GET REST à mão — o caminho mais dependente de reflexão em
toda a app.

### Segredos fora do Git

`git status` mostra apenas `keystore.properties.example` (o modelo, esse sim para commitar).
`git check-ignore -v` confirma que `upload-keystore.jks` e `keystore.properties` são ignorados
pelas linhas 95–96 do `.gitignore`.

### Por fazer

1. **O `.aab` de submissão não foi gerado**, por indicação expressa. `bundleRelease` quando for
   para avançar.
2. **Guardar o `mapping.txt`** de cada build publicado, fora do repositório.
3. A assinatura é só **v2**. Chega para `minSdk 26`, e com Play App Signing a Google reassina de
   qualquer forma.

## Fase 27 — Buraco no `hostUid` de `/lobbies`, e a regressão que a correção causou (2026-07-30)

Duas coisas nesta fase: uma credencial em texto simples e um buraco nas rules. A segunda deu uma
volta que vale a pena registar por inteiro, porque a primeira correção estava certa em isolamento
e **partiu o jogo em produção**.

### Credencial removida do relatório

O `RELATORIO_ALTERACOES.md` documentava a conta de teste `luso.jogador@example.com` **com a
palavra-passe em texto simples**. Substituída por `[não documentada aqui por segurança]`. A conta
existe mesmo no Firebase Auth e deve ser desativada, como as `teste1-4@starforge.test` na Fase 22.

### O buraco: qualquer um podia nomear outra pessoa como anfitrião

A regra em vigor era:

```
newData.isString() && (!newData.parent().child('membros').exists()
                    || newData.parent().child('estado').val() === 'cancelled'
                    || newData.parent().child('membros').child(newData.val()).exists())
```

As duas primeiras cláusulas eram fugas. Confirmado com tokens reais: **A criava um lobby com
`hostUid = B` e sem membros — permitido.** Idem com `estado: cancelled`. Só com `membros`
preenchido é que era negado.

**O impacto não é teórico.** O jogo só arranca quando `lobby.hostUid == myUid`
(`MultiMatchViewModel`). Um lobby forjado com um anfitrião que nunca lá está enche-se de
jogadores reais e **nunca começa** — e o `findOrCreateLobby` encaminha gente para lá por
corresponder à categoria e ao modo. Bastavam alguns lobbies fantasma semeados por uma conta
anónima para travar o matchmaking aleatório. Negação de serviço, não exposição de dados.

### A correção errada, e o que ela partiu

Primeira tentativa: exigir que o `hostUid` fosse o próprio `auth.uid` **ou** alguém em `membros`.
Passou 8/8 nos testes por REST — criar sala, reatribuir anfitrião ao sair, apagar o nó vazio,
tudo verde. E mesmo assim partiu a app: **"Firebase Database error: Permission denied" ao
escolher o modo**, para toda a gente.

Razão: o `findOrCreateLobby` corre uma transação sobre `lobbies/$formato` **inteiro** — não
escreve só a tua sala, reescreve todas as do formato, e a RTDB revalida o `hostUid` de cada uma.
Havia **6 lobbies órfãos** de sessões de QA antigas (sem membros, `estado: cancelled`, anfitriões
em contas já desativadas). Falhavam a validação, e uma folha inválida chumba a transação toda.

Era exactamente por isto que a cláusula `cancelled` existia. Removi-a a olhar só para o caso do
`leaveLobby`, sem ver que a transação toca em dados alheios.

### A correção certa

Uma terceira condição — **"o valor não mudou"**:

```
newData.isString() && (newData.val() === auth.uid
                    || newData.parent().child('membros').child(newData.val()).exists()
                    || data.val() === newData.val())
```

Carregar um valor inalterado é sempre seguro. Forjar exige *criar* ou *alterar* o `hostUid`, e aí
`data.val()` difere e as duas primeiras condições travam. É mais apertado que a versão
`cancelled`, que deixava criar salas fantasma à vontade.

**9/9 com tokens reais**, os dois lados:

| Tentativa | Resultado |
|---|---|
| forjar `hostUid=B` sem membros / com `cancelled` / com membros | negado |
| carregar órfão inalterado numa transação | permitido |
| mudar o `hostUid` de um órfão para terceiro | negado |
| criar a própria sala / outro junta-se / reatribuir ao sair | permitido |

Removidos 8 lobbies órfãos da base de dados.

### A lição

A primeira correção passou 8/8 porque testei a regra **isoladamente**, com lobbies que eu próprio
criava. O caminho real — uma transação sobre o nó do formato inteiro, com dados alheios lá dentro
— não estava coberto por nenhum teste. Foi o teste manual do dono do projeto que a apanhou, em
minutos. Regras que são revalidadas por transações de ascendente precisam de ser testadas **com
lixo pré-existente na base de dados**, não com um nó limpo.

## Fase 28 — Três defeitos de jogo reportados a jogar manualmente (2026-07-30)

Todos apanhados por teste manual, nenhum por análise. Vale a pena o registo: as três correções
das fases anteriores passaram nos testes por REST e mesmo assim estes escaparam, porque são
defeitos de **sequência** — só aparecem ao repetir ações.

### 1. Duplo toque saltava a pergunta seguinte

Havia guarda contra toque durante a revelação (`enabled = !isAnswered` e `if (isAnswered) return`),
por isso um duplo toque rápido não passava. O que passava era outro: o feedback dura 1000 ms e a
pergunta seguinte abre no mesmo sítio do ecrã. Um segundo toque que caísse nesse instante
respondia-a **de imediato** — o jogador via uma pergunta a ser saltada sem ter escolhido nada.

Correção: janela de carência de `INPUT_GRACE_MS` (350 ms) ao abrir cada pergunta, em que os
toques são ignorados. 350 ms é acima de um duplo toque acidental (~150–250 ms) e curto para quem
responde depressa de propósito. Bloqueia nos dois níveis:

- **visual** — `AnswerOption` ganhou `aceitaToques`, que desliga o `clickable` **sem** mexer nas
  cores (ao contrário de `isAnswered`, que pinta a revelação);
- **lógico** — `selectAnswer` recusa com `!aceitaToques` nos dois ViewModels, para um toque em
  trânsito não passar à frente da UI.

Aplicado ao solo e ao multijogador, que partilham o mesmo componente.

### 2. Segunda partida consecutiva não arrancava, e a saída não sincronizava

Causa única, dois sintomas. O `MultiMatchHost` faz `key(restart) { viewModel() }` — mas
`viewModel()` resolve pelo `ViewModelStore` da Activity: a `key()` muda a identidade da
**composição**, não a do ViewModel. "NOVO JOGO" reutiliza sempre a mesma instância, e o `start()`
só repunha o `_uiState`, deixando as variáveis privadas sujas da partida anterior:

| Variável | Consequência |
|---|---|
| `gameStarted` ficava `true` | `observeRoom` só arranca com `!gameStarted` — a segunda partida ficava eternamente em "À procura de adversário" |
| `finished` ficava `true` | `leave()` só escreve na RTDB com `!finished` — **a saída deixava de ser publicada**, e do outro lado o jogador continuava "presente" |
| `openLobbiesJob` | nunca era cancelado em lado nenhum, acumulando listeners a cada partida |

É o mesmo padrão do bug do `friendsJob`: subscrição/estado preso numa sessão anterior.

Correção: `resetMatchState()` no topo de `start()` e `startExisting()`, que cancela os quatro
jobs e limpa `salaId`, `currentLobbyId`, `gameStarted`, `finished`, `aggregated`, `streak` e
`maxStreak`. `openLobbiesJob` passou a ser cancelado no `leave()` e no `onCleared()`.

**Corrida corrigida ao mesmo tempo:** o `leave()` lia `salaId` *dentro* da corrotina, mas o host
faz `vm.leave(); restart++` — o `resetMatchState()` do `start()` seguinte limpava `salaId` antes
de a corrotina correr, e a saída era escrita a null, ou seja, nunca. Os identificadores passaram
a ser lidos e limpos de forma **síncrona**, antes do `launch`.

### 3. Walkover não existia fora do 2x2

Depois de corrigido o ponto 2, a saída passou a ser escrita (`estado=off, desistiu=true`) mas o
jogador que ficava **continuava a responder sozinho** até à décima pergunta, com o adversário
ainda no marcador. A deteção estava dentro de `if (format.teamBased)` — só 2x2. O 1x1 autónomo
tinha-a; perdeu-se quando foi dobrado no `MultiMatch`, e o `GAME_DESIGN.md` continuou a
documentar o comportamento antigo.

Novo `finishSoloWalkover()`, com condição genérica em vez de específica do 1x1: **se sobrar um só
jogador activo numa sala que tinha mais do que um**, a partida fecha. No Grupo, sair um de quatro
deixa três activos e o jogo segue — que é o comportamento documentado na Fase 13.

### Verificado nos emuladores

Três partidas 1x1 seguidas entre os mesmos dois jogadores, **sem reiniciar a app**:

| | |
|---|---|
| Partida 1 | arrancou, lockstep sincronizado nas 10 perguntas, pódio Vitória/Derrota |
| Partida 2 (após "NOVO JOGO") | **arrancou** — era exactamente aqui que ficava presa |
| Partida 3, com saída a meio | saída escrita na RTDB, e o outro lado mostrou "Adversário desistiu!" **à primeira verificação** |

O estado de salas teve de ser limpo antes: `/lobbies` tinha 13 salas de QA antigas, várias
`waiting` já cheias, que baralhavam o matchmaking durante o teste.

### Por fazer

A varredura sistemática (todos os formatos × modos × 2 categorias × 2 repetições) **não foi
executada**. 2x2 e Grupo não foram testados com partidas consecutivas nem com saída a meio — a
correção do ponto 2 é na camada partilhada e deve valer para os três formatos, mas isso é
dedução, não observação.

## Fase 29 — Ícone próprio e primeiras correções de responsividade (2026-07-30)

### Ícone da app — o placeholder saiu

Era o placeholder do Android Studio: uma estrela branca de 9 pontos sobre `#6650a4`, o roxo por
omissão do Material. Estava sinalizado como bloqueador desde o primeiro levantamento.

**Origem da arte.** O projeto original (`BrainBrawl`, `pt.perguntaoluso.app`) tinha dois conjuntos
de ícone. O `AndroidManifest.xml` de lá aponta para `@mipmap/avatar_14` — uma caravela desenhada à
mão com contorno grosso — e **não** para o `ic_launcher` (caravela azul e amarela, *flat*), que
ficou no projeto sem uso. Ambos foram copiados para `icon-referencia-brainbrawl/`, em pastas
`ATUAL-avatar_14/` e `OBSOLETO-caravela/`. Detalhe que engana: o export 512×512 desse ícone sai
com **fundo verde aos quadrados** — é o `avatar_14_background.xml`, nunca editado, ainda o
placeholder do Android Studio achatado para dentro da imagem.

**O que foi integrado.** A arte final veio de um redesenho a 4096×4096 na paleta Sticker. O
recorte por *flood fill* vazou (removeu 99,6 % da imagem), porque encadear vizinho-a-vizinho
atravessa gradientes; passou a ser por classificação de cor, que funciona porque não há
sobreposição nenhuma — fundo em luminância 51–67 e saturação ≤19, contorno em 0–24, cores da arte
em 126+ com saturação 200+. Depois só se guardou a maior componente ligada, o que limpou o halo.

| | |
|---|---|
| `ic_launcher_foreground.png` | 5 densidades (108→432 px), arte a 68 % da moldura — a zona segura do ícone adaptativo |
| `ic_launcher_monochrome.png` | 5 densidades. **A app nunca teve** camada `monochrome`; sem ela não há ícone temático no Android 13+ |
| `ic_launcher_background.xml` | Purple `#6C3CE0` liso |
| `ic_launcher.xml` / `_round.xml` | adaptive icon com as três camadas |
| `ic_launcher_foreground.xml` | **apagado** — era o vetor da estrela placeholder |

Fundo roxo e não dourado: sobre dourado a bandeira do mastro desaparecia e o casco Coral ficava
quente-sobre-quente. O 512×512 da loja ficou em `icon-build/`, fora do `res/`.

Verificado na gaveta de aplicações do emulador, e a camada `monochrome` simulada com tinta sobre
fundo claro — lê como linha, que é o que um ícone temático deve ser.

**Continua por fazer:** o gráfico de destaque 1024×500, obrigatório na ficha da Play Store, que
não se deriva do ícone.

### Responsividade — diagnóstico e correção parcial

Reportado ao testar noutros dispositivos. Diagnóstico: **zero** ocorrências de `WindowSizeClass`,
`BoxWithConstraints` ou `LocalConfiguration` em todo o código. Não havia lógica responsiva
nenhuma — o layout assume uma única altura e largura.

**Corrigido e verificado a 720×1280 com densidade 320 e `font_scale` a 1.3:**

Quatro ecrãs não tinham scroll — Início, Formato, Modo e Pergunta. Nessa configuração o conteúdo
era cortado **sem forma de lá chegar**: o botão JOGAR ficava inacessível. Depois da correção
chega-se a JOGAR, QUIZZES DA COMUNIDADE e HISTÓRICO.

O scroll no `MainScaffold` é **opt-in** (`scrollable: Boolean = false`), de propósito: os ecrãs
que já usam `LazyColumn` — Ranking, Histórico, Amigos, Conquistas, Quizzes — rebentam se forem
medidos dentro de um scroll, porque recebem altura máxima infinita. Só o Início o liga.

**Melhorado, mas ainda mau.** Os três chips do cartão de perfil não tinham `weight`: num ecrã
estreito o terceiro ("jogos") era empurrado para fora e **desaparecia**. Com `weight(1f)` os três
aparecem — mas o texto passa a partir-se letra a letra ("30 / 25 / po / nto / s"). Trocou-se um
defeito por outro menos grave, não se resolveu.

**Por fazer, e é trabalho a sério:** o cartão de perfil precisa de *reflow* — em ecrãs estreitos o
avatar devia passar para cima e os chips ocupar a largura toda, em vez de disputarem espaço lado a
lado. Isso é layout adaptativo com `BoxWithConstraints`, não um remendo. Tablet não foi testado;
*landscape* não se aplica, o manifest força `portrait`.

## Fase 30 — Grupo estava injogável: sala de espera sem scroll e portão de arranque errado (2026-07-30)

Reportado como "o 2x2 e o Grupo dão os mesmos problemas de há pouco". Não eram os mesmos. O 2x2
já estava bom; o Grupo tinha **dois defeitos próprios**, encadeados, e nenhum deles tinha a ver
com o ciclo de vida corrigido na Fase 28.

Ponto de partida que explica os dois: **`MatchFormat.GRUPO` tem `players = 10`**, não 4. Foi
expandido numa fase antiga e este documento continuou a dizer 4 (ver a divergência #10 da Fase 11
e a secção "Multiplayer — 2x2 & Grupo", ambas desactualizadas).

### 1. A sala de espera não tinha scroll, e o Grupo desenha 10 lugares

A `WaitingRoom` era uma `Column` simples:

```
ScreenHeader · cartão de estado · outras salas · lugares · Spacer(weight(1f)) · botões
```

Com 10 lugares o conteúdo ultrapassava o ecrã, o `Spacer(weight(1f))` colapsava a zero, e
**"INICIAR JOGO" e "CANCELAR PROCURA" ficavam desenhados abaixo da área visível** — sem scroll
para lá chegar. Não dava para começar a partida **nem para sair da sala**: o jogador ficava preso
até fechar a app.

Confirmado no emulador: o despejo da hierarquia mostrava os 4 jogadores, duas "Vaga livre", e
mais nada. Sem botão nenhum.

Correção: tudo acima dos botões passou a estar num `Column` com `weight(1f)` e
`verticalScroll`; os botões ficam **fixos em baixo**, fora da zona deslizável. É a disposição
certa para um ecrã cuja acção principal está no fundo, e resolve para qualquer número de
jogadores e qualquer altura de ecrã — não só para o caso dos 10.

### 2. O arranque manual criava a sala e depois recusava-se a entrar nela

Com os botões finalmente visíveis, carregar em "INICIAR JOGO" **não começava o jogo**. E sem erro
nenhum: a base de dados mostrava o lobby a `started` e a multisala criada. O `startLobbyRoom`
tinha corrido bem — o que falhava era o passo seguinte.

O portão de entrada no jogo, em `observeRoom`, era:

```kotlin
if (!gameStarted && room.perguntas.isNotEmpty() && room.jogadores.size >= format.players)
```

O arranque manual cria a sala com **os jogadores que lá estão** (quatro, no teste), e este portão
exigia `format.players` = **10**. A sala existia, o lobby ficava `started`, e ninguém entrava.

O auto-arranque aos 60 s tinha exactamente o mesmo destino: disparava, criava a sala, e o jogo
continuava sem começar. Ou seja, **o Grupo só era jogável com 10 pessoas simultâneas** — na
prática, nunca.

Correção: o portão passou a usar os membros reais da sala, que o anfitrião fixa ao criá-la:

```kotlin
val esperados = room.membros.size.takeIf { it > 0 } ?: format.players
if (!gameStarted && room.perguntas.isNotEmpty() && room.jogadores.size >= esperados)
```

Para o matchmaking cheio dá exactamente o mesmo número de antes; para uma sala arrancada à mão,
dá o número certo. Serve também as salas privadas por código, que já tinham este problema
latente.

### Verificado nos emuladores (4 dispositivos)

| Cenário | Resultado |
|---|---|
| **2x2** · Cultura Geral · Clássico | 4 jogadores, arrancou sozinho, lockstep sincronizado, pódio consistente nos 4 ("a tua equipa ganhou/perdeu" do lado certo) |
| **2x2**, segunda partida seguida · Geografia · Caótico | arrancou e sincronizou — regressão do ciclo de vida da Fase 28 continua fechada |
| **Grupo** · Cultura Geral · Clássico, 4 de 10 | botões visíveis, arranque manual funcionou, os 4 a jogar em sincronia |

Zero `Permission denied` e zero `FATAL EXCEPTION` nos quatro emuladores, em todos os ensaios.

### Nota de método

Os três ensaios anteriores a este falharam por causa do harness de teste, não da app: toques em
coordenadas fixas com esperas fixas adiantavam-se ao ecrã, e uma vez três `back` seguidos
fecharam a app e os toques seguintes foram para o *launcher*. Passou a usar-se um helper que toca
**por texto**, com despejo fresco da hierarquia e retentativa até o elemento aparecer. Com ele os
testes passaram à primeira. Fica registado porque custou várias tentativas a perceber que o
problema era a medição, não o medido.

### Por fazer

1. **Grupo até ao pódio** e uma segunda partida seguida — só foi verificado o arranque e as
   primeiras perguntas.
2. **Saída a meio** em 2x2 e Grupo. Em 1x1 está verificada (Fase 28); nos outros dois é dedução,
   porque a correção é na camada partilhada.
3. **O auto-arranque dos 60 s** sem carregar no botão.
4. **Decisão de desenho por tomar:** `players = 10` no Grupo exige dez pessoas na mesma categoria
   e modo ao mesmo tempo, ou depende sempre do arranque manual do anfitrião. Se a intenção era 4,
   é mudar um número em `MatchFormat`. Se são mesmo 10, o formato precisa de uma base de
   jogadores que a app ainda não tem.

## Fase 31 — Patentes, contagem de perguntas, som/háptico, e revisão das divergências (2026-08-07)

Quatro trabalhos independentes. Dois deles (patentes e contagem de perguntas) fecham
divergências que estavam abertas desde a Fase 11; o terceiro dá som à app pela primeira vez; o
quarto é uma passagem de pente fino pela lista de divergências, que tinha envelhecido mal.

### 1. Patentes por nível (`data/Patente.kt`)

Os níveis numéricos existiam desde a Fase 12b mas não diziam nada — "Nv 7" é um contador, não um
estatuto. A patente é **derivada** do nível, tal como o nível é derivado do `xpTotal`: nada de
novo é guardado e nada pode dessincronizar-se.

**Tema escolhido: a hierarquia de bordo de uma nau, e depois o mar aberto.** As quatro primeiras
são postos reais e pela ordem certa; as duas últimas vêm dos Descobrimentos. Assenta no resto do
jogo — o ícone da app é uma caravela (Fase 29) e Caravela e Farol já são dois dos dez símbolos de
avatar. Seis patentes chegam para haver degraus sem virar tabela de patentes militares.

| Patente | Níveis | XP acumulado | ≈ partidas (a 120 XP) |
|---|---|---|---|
| Grumete | 1–4 | 0 | 0 |
| Marinheiro | 5–9 | 2 100 | 18 |
| Piloto | 10–14 | 8 100 | 68 |
| Capitão | 15–19 | 17 850 | 149 |
| Navegador | 20–24 | 31 350 | 261 |
| Descobridor | 25+ | 48 600 | 405 |

O XP acumulado para chegar ao nível n é `75*(n-1)*(n+2)` — a soma dos custos `300 + (k-1)*150` da
Fase 12b. A primeira subida (nível 5) chega em menos de vinte partidas, para a mecânica se dar a
conhecer cedo, e **coincide de propósito com a conquista "Nível 5"** que já existia: o mesmo
momento passa a valer duas coisas. Descobridor fica deliberadamente longe.

**Onde aparece.** Nos três sítios onde o nível já aparecia:

- **Ranking** — a pastilha passa de `Nv 5` a `Nv 5 · Marinheiro`.
- **Início** e **Perfil** — no canto esquerdo da linha de rótulos da barra de XP, que estava
  vazio (o `x / y XP` está encostado à direita e o número do nível já está no emblema ao lado).

**Porque não ficou colada ao número no Início:** a alternativa era pô-la ao lado do nome do
jogador, e esse é exactamente o sítio que a Fase 29 deixou registado como apertado — o cartão de
perfil já espreme os três chips em ecrãs estreitos. Um elemento novo a disputar essa largura
reproduzia o mesmo defeito. Na linha da barra de XP não disputa nada.

**Tipografia a 13 sp na patente.** A 16 sp, `GRUMETE` + `510 / 750 XP` não cabem lado a lado num
ecrã de 720 px com `font_scale` a 1.3 — e não foi dedução: **apareceu cortado, "GRUM…", num teste
nessa configuração**, com o `overflow = Ellipsis` a fazer o seu trabalho. A 13 sp cabe. É o mesmo
recurso que o `SegmentedTabs` já usa com quatro separadores (Fase 18).

**Limitação conhecida:** nessa configuração extrema (720 px + `font_scale` 1.3), os nomes mais
longos — Descobridor, Marinheiro — ainda podem reticenciar no **Início**. Degrada com reticências,
nunca parte o ecrã. Resolver a sério é o *reflow* do cartão de perfil que a Fase 29 já identificou
como trabalho por fazer. Na **pastilha do Ranking** o problema não existe: foi medido.

**Medição real, não estimativa.** Compilei uma vez uma versão com a pastilha do Ranking a mostrar
sempre `Nv 25 · Descobridor` — o pior caso — e medi as `bounds` pelo despejo da hierarquia:
**294 px**, a acabar em x=533 com a pontuação a começar em 565, na configuração estreita. Cabe com
folga e sem reticências. (Uma extrapolação linear a partir de `Nv 4 · Grumete` dava 324 px, ou
seja, era pessimista — daí ter medido em vez de confiar na conta.) A alteração temporária foi
revertida a seguir.

**Testes** (`app/src/test/.../PatenteTest.kt`, 5/5): cada fronteira, níveis inválidos (caem em
Grumete em vez de rebentar), a próxima patente, a ordenação das fronteiras, e — o mais útil — a
verificação de que o **XP acumulado da tabela acima bate certo com a `Progressao` real**. Esse
último apanhou dois números que eu tinha escrito mal na tabela (Capitão dizia 18 900 e 158
partidas; é 17 850 e 149).

**No dispositivo:** Início, Perfil e Ranking a mostrar `GRUMETE` ao nível 4; e, depois da partida
perfeita da secção 3, o mesmo perfil a passar a **`Nv 5 · Marinheiro`** no Ranking — a primeira
travessia de fronteira, com XP mesmo ganho, não forjado.

### 2. Contagem de perguntas por categoria

Divergência #7 da Fase 11 (mockup, ecrã 4: "312 perguntas"), fechada. Cada cartão do picker mostra
`N perguntas` por baixo do nome, em corpo mais pequeno e com a tinta a 75 % — informa sem disputar
leitura com a categoria.

**O custo era o ponto todo.** `/categorias/{cat}/perguntas` são as perguntas completas, com
enunciado, opções e resposta: ler o nó para lhe chamar `size` traria ~1 MB só para escrever um
número, e o `QuestionRepository` volta a descarregar a categoria escolhida logo a seguir. Com
`shallow=true` o RTDB devolve só as chaves — o mesmo recurso que `loadCategories` já usava desde a
Fase 22 para não puxar as perguntas ao listar os nomes. Uma chamada por categoria, em paralelo
(`shallow` não conta netos numa só ida).

Detalhes que o desenho impôs:

- **Corre fora do `try` das categorias e depois destas já estarem no estado.** O ecrã abre com os
  cartões e o número entra quando chegar; uma falha a contar não pode deixar o jogador sem
  categorias por causa de um rótulo.
- **Uma categoria que falhe fica fora do mapa**, não a zero. O ecrã trata a ausência como "ainda
  não sei" e não escreve nada — "0 perguntas" seria uma mentira plausível sobre uma categoria que
  está lá.
- **As contagens sobrevivem ao `sessionOnly()`.** São conteúdo estático (`/categorias` tem
  `.write: false`) e o `loadCategories` corre a cada ida ao picker; sem isto eram cinco pedidos de
  rede repetidos por visita.
- O caminho é percent-encoded à mão (`URLEncoder` escreve espaços como `+`, que num caminho está
  errado), e o parser aceita array **e** objecto — o RTDB devolve chaves numéricas sequenciais
  como array, que é o caso aqui, mas um `push id` futuro daria objecto.

**No dispositivo:** Cultura Geral 217 · Desporto 169 · Gentílicos 111 · Geografia 212 · História
326. Somam 1035 que, com as 55 das duas categorias ocultas, dão **1090** — exactamente o total que
a Fase 17 deixou registado. Verificado também a 720×1280 com `font_scale` 1.3: as duas linhas
cabem dentro da altura fixa de 84 dp do cartão, sem corte.

### 3. Som e háptico (`audio/SoundEffects.kt`)

A app não tinha áudio nenhum. Havia um `SoundEffects.kt` **por commitar**, escrito noutra sessão e
já ligado ao ecrã da pergunta, com quatro WAV em `res/raw`. Foi reescrito por inteiro. Os
problemas, por ordem de gravidade — é o mesmo padrão da Roda da Sorte da Fase 20, código que
compila mas que não devia entrar assim:

1. **Mexia no volume do telemóvel.** Antes de *cada* som corria
   `setStreamVolume(STREAM_MUSIC, 85 %)` — punha o volume de multimédia do jogador a 85 % e não o
   repunha. O pedido era precisamente o contrário: respeitar o volume do sistema.
2. **Bloqueava a thread do jogo.** `MediaPlayer.create()` por som abre e descodifica o recurso na
   thread que chama, que aqui era a main thread, no instante em que o jogador responde.
3. **Bips de sistema como alternativa.** Caía num `ToneGenerator` genérico.
4. **O tempo esgotado ficava mudo.** O som era disparado no `onClick` de cada opção, portanto não
   havia toque, não havia som — logo no caso em que o jogador não estava a olhar para o ecrã.
5. Não sabia o que era o modo silencioso, e faltavam metade dos sons pedidos.

**Agora:** `SoundPool` (descodifica uma vez ao arrancar, `play()` não bloqueia, aguenta sons
sobrepostos), `USAGE_GAME`/`STREAM_MUSIC` — portanto segue o volume de multimédia do jogador, e a
zero não se ouve nada. **Nunca** escreve no volume do sistema. Não pede foco de áudio: são sons de
0,4–1,1 s e pedir foco baixaria a música que o jogador tenha por trás. Um som pedido antes de
estar carregado é descartado em silêncio — nada espera por áudio.

**Os seis sons são sintetizados de raiz**, não são de stock: sinusoide com 2.ª e 3.ª harmónicas
fracas, ataque de 6 ms e decaimento exponencial (timbre de marimba/kalimba, arredondado, a
condizer com o registo sticker), tudo em pentatónica de Dó maior para qualquer par que se
sobreponha continuar consonante. Certo sobe, errado desce e é mais fraco (assinala sem
repreender), vitória é um arpejo a subir, derrota o mesmo ao contrário, conquista é uma cascata
aguda e a subida de nível é a escada completa. 22,05 kHz mono — a harmónica mais aguda anda pelos
4,7 kHz, muito abaixo do limite de Nyquist — o que dá **184 KB** para os seis.

**O som segue o estado, não o toque.** `LaunchedEffect(isAnswered, …)` no solo e no multijogador,
por isso o **tempo esgotado também soa**. Foi confirmado no logcat: numa partida em que deixei
uma pergunta esgotar, saiu `ERRADO` sem ter havido toque nenhum.

**Silêncio e vibração**, verificados nos três modos de campainha, um a um:

| Modo | Som | Vibração |
|---|---|---|
| NORMAL | sim | sim |
| VIBRATE | **não** | sim |
| SILENT | **não** | **não** |

E com `haptic_feedback_enabled = 0` nas definições: som sim, **vibração não**. Chamar o `Vibrator`
diretamente ignora essa preferência do sistema, por isso é lida à mão antes de vibrar.

**`VIBRATE` foi acrescentada ao `AndroidManifest.xml`** — sem a declarar, `vibrate()` lança
`SecurityException` e a vibração nunca funcionaria. É permissão de nível *normal*: concedida na
instalação, sem pedido em runtime, sem acesso a dados. Confirmada como `granted=true` no
`dumpsys` do dispositivo.

**Subida de nível e conquistas** são detectadas por **comparação**, sem campos novos: o perfil é
fotografado antes de agregar e relido depois, e o que mudou é o que se celebra (nível maior;
conquistas que passaram de bloqueadas a desbloqueadas). Sem perfil anterior — primeira partida da
sessão — a lista sai vazia de propósito, senão um veterano a abrir a app ouvia a fanfarra de
todas as conquistas que já tinha há semanas.

No pódio os três sons são **encadeados, não sobrepostos**: resultado, +950 ms subida de nível,
+1150 ms conquista. Juntos eram um amontoado onde não se percebia que tinham acontecido três
coisas.

**Verificado no dispositivo, com registo temporário no logcat** (retirado a seguir):

- Partida de 10 perguntas com 2 certas: dez sons de resposta pela ordem certa, **incluindo o da
  pergunta deixada a esgotar**, e `DERROTA` no pódio — caso que antes ficava completamente mudo,
  porque só a vitória tinha som.
- Partida perfeita (10/10, 2410 pontos, +250 XP): `VITORIA` → 1,6 s → `SUBIU_NIVEL` → 1,2 s →
  `CONQUISTA`, uma vez cada um, na ordem e com o espaçamento desenhados.
- A RTDB confirma que a festa foi merecida e não um falso positivo: o perfil passou de
  `xpTotal` 1860 para **2180** (nível **4 → 5**) e ganhou `partidasPerfeitas: 1` — ou seja,
  "Partida Perfeita" e "Nível 5" desbloquearam mesmo naquela partida.
- Todas as vibrações reportaram sucesso.

**Release:** `assembleRelease` passa e os seis WAV **sobrevivem ao `shrinkResources`** — confirmado
por `unzip -l` ao APK assinado, os seis lá estão com os tamanhos exactos (os nomes ficam
ofuscados). O APK passa de 2,26 MB (Fase 26) para **2,57 MB**.

### 4. Divergências da Fase 11 revistas

A lista tinha 13 entradas de Julho e nunca foi revisitada. Estão agora anotadas uma a uma na
própria secção da Fase 11. Resumo do que mudou:

| # | Estado |
|---|---|
| 1 Códigos de sala / entrar em sala | **Resolvida na Fase 25** e nunca riscada |
| 2 Nomes de patente | **Resolvida nesta fase** |
| 7 Contagem de perguntas | **Resolvida nesta fase** |
| 10 Tamanho do Grupo | **O documento estava errado**: dizia 4, `MatchFormat.GRUPO` tem `players = 10` |
| 11 Separadores do Histórico | **Duplamente desactualizada**: já existe sistema de perguntas personalizadas, e o Histórico filtra por **formato**, não por modo |
| 3, 4, 5, 8, 9, 12, 13 | De pé (8 com o motivo mudado: já não há nada inalcançável, falta o atalho) |
| **6 Ranking por formato** | **De pé — não está implementado** |

Sobre a #10, a mesma correcção foi aplicada à secção "Multiplayer — 2x2 & Grupo", que também dizia
"4 players". A Fase 30 já tinha assinalado ambas como desactualizadas e corrigiu o código à volta,
mas não veio cá corrigir o texto.

**Sobre a #6, explicitamente, porque é fácil de confundir:** não existe ranking por formato em
lado nenhum. O `RankingScreen` segmenta por `GameMode.entries` (Clássico/Caótico/Eliminatórias) ×
três métricas. `multiVitorias` e `multiJogos` por formato existem em `/jogadores` desde a Fase 13,
mas o **único** consumidor é o `Achievement.kt` (Duelista / Companheiro / Rei do Grupo). O que
**é** filtrado por formato é o **Histórico** (Todos / Solo / 1x1 / 2x2 / Grupo), a partir do campo
`formato` do `ScoreEntry` — é provavelmente daí que vem a confusão. Os dados para os quadros por
formato já lá estão; falta o ecrã.

### Encontrado pelo caminho, não corrigido

1. **O `FormatScreen` diz "Quatro jogadores, todos contra todos" no cartão do Grupo**, e
   `players = 10`. É a mesma desactualização da #10, agora **na interface** e não só no documento.
   Fica por corrigir junto com a decisão de desenho que a Fase 30 deixou em aberto (4 ou 10?) —
   não faz sentido acertar o texto antes de decidir o número.
2. **A página de privacidade lista as permissões da app** e passa a estar incompleta: falta lá a
   `VIBRATE`. É uma linha em `privacidade.html`, no branch `gh-pages`.
3. **O `tick.wav` foi removido** com o resto dos sons antigos: existia em `res/raw` e o `playTick`
   que o usava não era chamado de lado nenhum.
4. **O áudio não foi exercitado em runtime no APK de release.** A verificação foi ao nível do
   recurso (os seis WAV estão no APK depois do `shrinkResources`), que é o modo realista de o R8
   partir isto; observar o som a tocar exigiria voltar a pôr registo no logcat numa build de
   release. Fica dito, à conta da lição da Fase 26: isto é análise, não observação.

## Fase 32 — Ranking por formato, Grupo de 4 a 10, e a permissão VIBRATE na política (2026-08-08)

### 1. Ranking por formato

Fecha a divergência #6 da Fase 11, a única que a revisão da Fase 31 confirmou como ainda por
fazer. Os dados estavam em `/jogadores` desde a Fase 13 (`multiVitorias` / `multiJogos` por
formato) e só o `Achievement.kt` os lia.

**Estrutura: três níveis, dois pesos visuais.** O ecrã já tinha pastilha (modo) + sublinhado
(lista) desde a Fase 18, e a dimensão nova precisava de um sítio sem inventar um terceiro estilo
de separador:

| Nível | Componente | Conteúdo |
|---|---|---|
| Dimensão | `UnderlineTabs` | Por modo · Por formato |
| Valor | `SegmentedTabs` (pastilha roxa) | Clássico/Caótico/Eliminatórias **ou** 1x1/2x2/Grupo |
| Tabela | `UnderlineTabs` | as três listas da dimensão |

A pastilha — o elemento mais pesado — **fica onde já estava**, no meio, a escolher o modo ou o
formato. A dimensão entra por cima em sublinhado porque é uma troca rara, de duas opções, e não
deve pesar mais do que a escolha que o jogador faz logo a seguir. O ecrã de sempre mantém-se
reconhecível e ganha uma linha leve no topo. Trocar de dimensão repõe os dois separadores de
baixo no primeiro: as duas dimensões têm três chaves cada, mas "Eliminatórias" e "Grupo" não são
a mesma coisa e herdar o índice dava ao jogador um quadro que ele não pediu.

**Limitação assumida, sem campos novos.** Por formato existem **duas** contagens e mais nenhuma:
`multiVitorias` e `multiJogos`. **Não há pontos nem recorde por formato** — o `ProfileRepository`
nunca os escreveu (só `bump` nesses dois contadores, mais os agregados globais e por modo). Por
isso "Mais pontos" e "Melhor recorde" **não são deriváveis** aqui, e não foi inventado nenhum
campo para os fabricar. As três tabelas por formato são:

| Tabela | Fonte | Eixo |
|---|---|---|
| Mais vitórias | `multiVitorias[formato]` | pico |
| Mais jogos | `multiJogos[formato]` | volume |
| % vitórias | `multiVitorias / multiJogos` | consistência |

São os mesmos três eixos das tabelas por modo (pico, volume, consistência), obtidos com o que
existe. **Acrescentar "Mais pontos" por formato é uma linha no `accumulate`** (`bump` de
`multiPontos/{formato}`), mas só passaria a contar a partir daí: as partidas já jogadas não têm
esse dado e a tabela nasceria a mentir sobre quem joga há mais tempo. Fica como decisão futura,
não como omissão.

**A percentagem exige 3 jogos** (`MIN_JOGOS_PARA_PERCENTAGEM`). Sem mínimo, quem ganhou o único
jogo que fez aparecia em 1.º com 100 % à frente de quem ganhou 8 em 10 — e não é hipótese
teórica: nos dados de teste havia **dois** perfis com 1 vitória em 1 jogo. O ecrã explica o corte
numa linha por cima da tabela, **também quando a tabela está cheia**: cinco nomes parecem uma
lista completa, e quem não se encontra nela não teria como saber porquê.

Tudo sai de `/jogadores`, nunca de `/scores` — uma linha por jogador, como o resto do Ranking.

**Verificado no dispositivo** contra os valores reais da base de dados: 1x1 · Mais vitórias
(Teste Um 8, Ana Costa 2, Convidado 2), 1x1 · Mais jogos (10, 9, 5, 3), 1x1 · % vitórias
(80 %, 66 %, 40 %, 22 % — com os dois perfis de 1/1 correctamente **fora**), 2x2 · Mais vitórias
(Teste Dois 1, Teste Um 1) e Grupo com "Ainda sem dados." mais a nota do mínimo. O separador
por modo ficou exactamente como estava.

### 2. Grupo: mínimo 4, máximo 10

`MatchFormat` ganhou **`minPlayers`** ao lado de `players`. `players` continua a ser a
**capacidade** (lugares na sala, arranque automático ao encher); `minPlayers` é quanto é preciso
para **poder** começar.

| Formato | `minPlayers` | `players` | Porquê |
|---|---|---|---|
| 1x1 | 2 | 2 | um duelo precisa dos dois |
| 2x2 | 4 | 4 | o anfitrião divide em duas equipas de dois; com três, uma ficava com um jogador |
| Grupo | **4** | **10** | esperar por dez na mesma categoria e modo tornava o formato injogável (Fase 30) |

**Ponto 4 — arranque manual.** O botão "INICIAR JOGO" passa de `joinedCount >= 2` para
`joinedCount >= format.minPlayers`. **Isto corrigiu um defeito que não era o alvo:** com `>= 2`,
um **2x2 podia ser arrancado com 2 ou 3 jogadores**, e aí a divisão em equipas deixava uma
equipa com um jogador só. Com `minPlayers = 4` no 2x2 essa janela fecha-se.

A guarda foi repetida no `MultiMatchViewModel.forceStartGame`, e não só no ecrã: o temporizador
de arranque automático dispara de uma corrotina que pode chegar **depois** de alguém sair da
sala, e nesse instante a contagem já não é a que estava no ecrã quando o relógio começou.

**Ponto 5 — auto-arranque: mantido, e armado só a partir do mínimo.** O temporizador de 60 s já
existia e reinicia a cada entrada; **essa reposição é a janela de graça** — cada jogador novo
compra mais 60 s à sala, por isso uma sala a encher continua a esperar e só uma sala parada
fecha sozinha. Manteve-se em vez de exigir sempre acção do anfitrião porque sem ele um Grupo com
4 fica refém da atenção de uma pessoa: se o anfitrião se distrai, ninguém joga — que é
exactamente o beco da Fase 30. Como agora só arma a partir de 4, deixou de poder fechar uma sala
de 2 ou 3.

**Ponto 6 — matchmaking: nenhuma alteração era necessária, e a razão interessa.** O enunciado
falava da fila `/matchmakingN`, que **é código morto**: `MultiMatchRepository.createRoom` — a
única função que escreve em `pending`/`notify` — não é chamada de lado nenhum. O matchmaking
aleatório em uso é o de **lobbies** (`findOrCreateLobby` sobre `/lobbies/{formato}`), e esse já
faz o que era pedido: um jogador entra em qualquer lobby aberto com `membrosCount < players`
(ou seja, até 10), a sala arranca sozinha ao encher, e entre o mínimo e a capacidade quem manda
é o anfitrião ou o temporizador. O "período extra para tentar encher mais antes de fechar" é o
mesmo relógio de 60 s do ponto 5. **Nenhum limiar do repositório foi mexido** — só o gate de
arranque, que é uma decisão de jogo e não de fila.

**Ponto 7 — texto do FormatScreen.** "Quatro jogadores, todos contra todos" passa a
"**4 a 10 jogadores**, todos contra todos", e é gerado por `MatchFormat.GRUPO.sizeLabel` em vez
de estar escrito à mão — foi assim que se desencontrou do código da primeira vez. A sala de
espera passa a mostrar `JOGADORES (x/10) · MÍNIMO 4`, e o rodapé diz coisas diferentes conforme
o estado: quantos faltam, que já se pode começar mas a sala continua a encher, ou que se espera
pelo anfitrião.

**Testes unitários** (`MatchFormatTest`, 6/6) prendem as invariantes que só se descobririam com
gente real numa sala: mínimo nunca acima da capacidade, mínimo ≥ 2, formatos por equipas com
tamanho fixo e par, só o Grupo flexível, e o rótulo de tamanho.

**Verificado em 4 emuladores:**

| Cenário | Resultado |
|---|---|
| Grupo com **3** | Sem botão "INICIAR JOGO". `JOGADORES (3/10) · MÍNIMO 4` e "Faltam 1 para poder começar". O relógio passou de 60 s **sem arrancar** — antes desta fase teria arrancado a 2. |
| Grupo com **4** | Botão aparece ("INICIAR JOGO (4 JOGADORES) · Auto: 54s") e o rodapé passa a "Já podes começar. A sala continua a aceitar jogadores até 10." |
| Grupo, arranque manual a 4 | Os 4 entraram na **mesma Pergunta 1 de 10**; a RTDB mostra o lobby `started` com 4 membros e `salaId`. |
| **2x2** (regressão) | Arranca sozinho aos 4, Equipa A/B atribuídas, os 4 na mesma Q1. |
| **1x1** (regressão) | Arranca sozinho aos 2, ambos na mesma Q1. |

**Não verificado:** salas com 5 a 10 jogadores. Só há quatro emuladores. A capacidade continua a
ser o `< format.players` de sempre, que não foi tocado nesta fase, e foi observado que a sala
fica em `waiting` a aceitar mais depois de chegar aos 4 — mas 5+ em simultâneo é dedução, não
observação.

### 3. `privacidade.html` — permissão VIBRATE declarada

A página listava só Internet e estado da ligação. A `VIBRATE` entrou no manifesto na Fase 31 e a
política ficou incompleta — apontado nessa fase como por fazer, feito agora.

A lista passa a enumerar as três permissões e explica para que serve a vibração (retorno tátil
dos efeitos de jogo), que respeita o modo de silêncio e a definição de retorno tátil do sistema,
e que não dá acesso a dados. **A data do topo foi actualizada** para 8 de agosto de 2026: a
própria página promete, no fim, que "se esta política mudar de forma significativa, a data no
topo é atualizada".

Feito num **worktree isolado** sobre `gh-pages`, como na Fase 24 e pela mesma razão — o `main`
local tem trabalho por commitar e um commit normal arrastava-o. O HTML foi validado (todas as
tags fecham) e o texto renderizado conferido.

**Commit `0a14609` está só local.** Não foi feito `push`: a `gh-pages` é servida ao público pelo
GitHub Pages e publicar é decisão do dono do projeto.

```bash
git push origin gh-pages
```

### Por fazer

1. **"Mais pontos" por formato** exige um contador novo (`multiPontos/{formato}`) e só contaria
   do momento da mudança em diante — ver a secção 1.
2. **Grupo com 5 a 10 jogadores** por observar.
3. O temporizador de arranque automático vive na **composição** da sala de espera e só corre
   para o anfitrião: se ele puser a app em segundo plano, o relógio pára. É anterior a esta fase
   e continua assim.
4. **`MultiMatchRepository.createRoom` e a fila `/matchmakingN`** são código morto confirmado.
   Apagá-los (e as rules de `matchmakingN`) é limpeza segura, mas fica fora do âmbito pedido.

## Fase 33 — Porque é que os sons não se ouviam (2026-08-08)

Reportado como "o som de correto e incorreto ainda não funcionam". Eram **duas** causas
independentes, nenhuma delas no caminho de código que a Fase 31 tinha verificado.

### 1. Os emuladores estavam a correr com `-no-audio`

Causa principal, e nada tinha a ver com a app:

```
qemu-system-aarch64 -avd BrainBrawl_1 -no-snapshot-load -no-audio
```

Os quatro emuladores tinham sido lançados com `-no-audio`. Dentro do Android tudo parece
funcionar — o `AudioFlinger` cria a track, o mixer corre, os volumes estão normais, o
`SoundPool.play()` devolve um id de stream válido — e o QEMU deita fora o áudio à saída. É
invisível de dentro do sistema.

**Isto expõe um erro de método na Fase 31.** O que lá foi verificado foi o *caminho de código*:
que `tocar()` era chamado nos momentos certos e que o `SoundPool` aceitava a reprodução. Isso foi
registado como "verificado no dispositivo", o que sugeria mais do que aquilo que estava provado.
Um `play()` que devolve um id não é som audível. A flag do emulador nunca foi olhada.

**A evidência que passou a ser usada** é a thread de saída do `media.audio_flinger`: sai de
`Standby: yes` para `Standby: no` quando alguma coisa está mesmo a ser renderizada. É o sinal
mais próximo do altifalante que se consegue observar sem ouvir.

Nota: o número de `createTrack_l` no logcat **não serve** para isto. O `SoundPool` cria a sua
`AudioTrack` uma vez e reutiliza-a, por isso a mensagem aparece na primeira reprodução e nunca
mais — contar ocorrências dá zero em reproduções que estão a funcionar perfeitamente.

Para voltar a ter som num emulador, basta relançá-lo sem a flag:

```bash
$ANDROID_HOME/emulator/emulator -avd BrainBrawl_1 -no-snapshot-load
```

### 2. O modo de vibração calava o jogo — e esse era um defeito real

Encontrado ao investigar o primeiro. `podeTocarSom()` exigia `RINGER_MODE_NORMAL`:

```kotlin
return audio.ringerMode == AudioManager.RINGER_MODE_NORMAL   // errado
```

No Android o modo de campainha manda nos **toques e notificações**, não no áudio de
**multimédia**. Um telemóvel em vibração continua a tocar Spotify, YouTube e jogos — e é assim
que muita gente traz o telemóvel o dia inteiro. Com esta regra o jogo ficava mudo nesse estado,
que é exactamente a queixa "os sons não funcionam", agora num telemóvel a sério em vez de num
emulador.

Passa a calar **só** em silêncio:

```kotlin
return audio.ringerMode != AudioManager.RINGER_MODE_SILENT
```

O silêncio continua respeitado, que era o pedido explícito da Fase 31. O volume de multimédia
nunca precisou de verificação nenhuma: o `SoundPool` está em `STREAM_MUSIC` e a zero não se ouve
nada. A **vibração fica como estava** — continua bloqueada em silêncio e continua a respeitar a
definição de retorno tátil do sistema, por isso a descrição na página de privacidade mantém-se
correcta.

### Verificado no emulador já com áudio, a partir de standby assente

| Modo de campainha | Antes do toque | Depois do toque |
|---|---|---|
| VIBRAÇÃO | `Standby: yes` | **`Standby: no`** — passou a ouvir-se |
| SILÊNCIO | `Standby: yes` | `Standby: yes` — continua calado |
| NORMAL | `Standby: yes` | `Standby: no` |

Cada medição parte de standby assente (a saída volta a standby ao fim de 3 s), por isso o
"depois" é consequência daquele toque e não sobra do anterior.

### Por fazer

1. **Os outros três emuladores continuam com `-no-audio`.** Só o `BrainBrawl_1` foi relançado.
   Para testar som em multijogador é preciso relançar os restantes da mesma maneira.
2. **Audibilidade continua por confirmar por um humano.** Tudo o que se pode observar por
   ferramentas diz que o áudio é renderizado; ouvir é que não dá.
