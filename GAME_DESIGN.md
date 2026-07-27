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
  anonymous session so play still works. Start shows `TERMINAR SESSÃO` when registered,
  `LOGIN / CRIAR CONTA` when anonymous.

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
stat chips: pontos, taxa de acertos, jogos) plus JOGAR, RANKING, and either
LOGIN / CRIAR CONTA (anonymous) or TERMINAR SESSÃO (registered).

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
- **Grupo** — **4 players, all-vs-all**, individual score; podium is the ranking of all
  players by points. (BrainBrawl's group rooms are admin-driven with variable size; for a
  matchmaking flow a fixed 4 is the simplest fair choice.)
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
- `group` → `FriendsScreen`, an "Em breve" placeholder (the friends system is a later phase).

### Waiting / matchmaking room (`game/multi/MultiMatchScreen.kt` → `WaitingRoom`)

Rebuilt the `SEARCHING` phase to mockup **screen 5 (Matchmaking)**, generalised per format:

- Header per format ("À Procura de Adversário / de Equipa / de Jogadores"), format icon,
  purple status card ("À procura de jogadores…", `X / N encontrados`, live `Tempo de espera`
  clock), CANCELAR, and a "Navegação bloqueada durante a procura." note.
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
silent omissions:

1. **Room codes / invite flow** (screens 6/7/8 "CÓDIGO DA SALA", "Copiar código",
   "Convidar…", "ESTOU PRONTO"; screen 15 "Entrar numa Sala"): not built. Multiplayer is
   **random matchmaking** (queue-based), not private invite rooms — so the waiting room
   follows screen 5, not 6/7/8, and the match **auto-starts when the room fills** (no manual
   ready-up). The invite/room-code system belongs to the excluded "Entrar numa Sala" + Amigos phase.
2. **XP bar / Level / rank name** (screens 1, 11, 14: "870/1500", "NÍVEL 5 · EXPLORADOR",
   "Nv 12"): no XP/levelling system exists. Profile shows raw stats instead
   (jogos, pontos, acertos, vitórias, recorde, streak).
3. **Live-player counters** (screen 1 "2.847 A JOGAR AGORA", "34 online"): no presence system.
4. **Streak flame badge on Início** (screen 1 `local_fire_department 7`): streak is shown in
   Perfil as a stat, not as a header badge.
5. **Achievements / Conquistas** (screen 14 badges): no achievements system → not built.
6. **Ranking structure** (screen 11): mockup is one "Ranking Global" list segmented by
   **format** (Global/1x1/2x2) with player **levels**. The app segments by **mode**
   (Clássico/Caótico/Eliminatórias) with three sub-lists (vitórias/pontos/recorde) and no
   levels — a deliberate earlier-phase design; levels do not exist.
7. **Per-category question counts** (screen 4 "312 perguntas"): the count is not surfaced from
   data, so category cards show icon + name only.
8. **Início quick-access tiles** (screen 1 "ACESSO RÁPIDO", "Amigos", "Entrar em Sala"): not
   built. Início uses JOGAR / HISTÓRICO / LOGIN + the bottom bar; Amigos = "Em breve",
   Entrar em Sala = excluded phase.
9. **Question image** (screen 9 "imagem da pergunta (opcional)"): questions are text-only.
10. **Grupo size**: mockup shows "JOGADORES (4/8)"; the app caps Grupo at **4** (earlier-phase
    decision) — the waiting room shows `X/4`.
11. **Histórico filter tabs** (screen 12 "Todos/Oficial/Personalizadas"): no custom-questions
    system → History is a flat list.
12. **História category icon**: mockup uses `castle`; the app uses `HistoryEdu` (Material
    Symbols `castle` is not in the bundled Compose icon set) — a minor icon substitution.
13. **Wait timer** is a client-side elapsed clock (there is no server-tracked queue time);
    cosmetically it matches the mockup's "Tempo de espera".

## Phase 12 — XP / level progression + "a jogar agora" presence (2026-07-27)

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
