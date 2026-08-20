# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Read this first

**Start at [`docs/vault/00-indice.md`](docs/vault/00-indice.md)** — a one-screen index of the
project's current state, linking to short per-topic notes. It exists specifically so you don't
have to read `GAME_DESIGN.md`.

`GAME_DESIGN.md` (~3000 lines, 33 phases) is the **chronological archive**, not the entry point.
Its sections before "Phase 11" are the *original* design and were superseded without being
rewritten — they describe a standalone 1x1 that no longer exists, RTDB paths that were removed,
and a 4-player Grupo that is now 4–10. Read it only for *how a decision was reached*.

Before touching security rules or the data model, read
[`docs/vault/seguranca/historico-vulnerabilidades.md`](docs/vault/seguranca/historico-vulnerabilidades.md).

## Commands

```bash
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:assembleRelease        # signed release (unsigned if no keystore — deliberate)
./gradlew testDebugUnitTest           # all unit tests
./gradlew testDebugUnitTest --tests "*PatenteTest*"   # a single test class
```

Unit test results land in `app/build/test-results/testDebugUnitTest/*.xml` — parse those rather
than trusting a `BUILD SUCCESSFUL` line, which can pass with zero tests actually run (Gradle
caches; add `--rerun-tasks` to force).

There is **no linter or formatter configured** (no ktlint/detekt/spotless). Don't invent one.

Deploy RTDB rules: `npx -y firebase-tools@latest deploy --only database`

## Architecture

MVVM, but with an unusual split worth knowing up front:

- **`GameViewModel`** owns everything solo plus all navigation (`GameScreen` enum → `GameApp.kt`
  renders by `when`). There is no navigation library; screen state is a field on `GameUiState`.
- **`MultiMatchViewModel`** (`game/multi/`) owns *only* the live multiplayer match. It is a
  separate ViewModel with its own state, entered via `MultiMatchHost`.
- Repositories in `data/` are Compose-free and each own one RTDB subtree.

`GameUiState.sessionOnly()` is the "reset for a new game" helper — it deliberately preserves a
short whitelist (auth, profile, friends, presence, category counts). Anything new that must
survive navigation has to be added there explicitly.

**Anything `GameViewModel` needs to reflect live must be a listener (`Flow` + `callbackFlow`),
not a one-shot read** — `_uiState` has no way to learn about writes from elsewhere unless something
is actively watching. `observeFriends`/`friendsJob`, `PresenceRepository.observeCount`, and
`ProfileRepository.observe` (own profile, since 10 Aug 2026) all follow this. The profile bug this
fixed: `MultiMatchViewModel` is a *separate* ViewModel that aggregates the profile by writing
straight to the RTDB — it has no way to tell `GameViewModel` anything changed, so Início showed
stale data until the player happened to visit Perfil (which forces a reread). Same shape as the
Phase 14 `friendsJob` bug. Whenever the uid changes (login, register, sign-out, account deletion),
every one of these listeners has to be re-pointed at the new uid — that's the part that's easy to
miss.

### Backend model — the constraint that shapes everything

**Firebase Auth + Realtime Database only. No server, no Cloud Functions.** Consequences:

- **Scoring is client-authoritative.** The device decides correctness and computes points. The
  RTDB rules are the *only* validation floor, via numeric ceilings pinned to the mathematical
  maximum (e.g. `pontuacao ≤ 4000`). They block impossible values, not implausible ones. Hiding
  `respostaCorreta` from players is impossible in this architecture.
- `/jogadores/{uid}` is the **aggregate** profile, folded in by transaction after each game.
  Ranking, Profile and Achievements read from it — **never** from `/scores`, which is the raw
  per-game log and would list a player many times.
- Level, patente and achievement state are all **derived** from stored fields, never persisted.
  Only `xpTotal` is stored. That's why they can't drift.
- The **daily streak** (`diasSeguidos` + friends on `/jogadores/{uid}`) is the one place that
  stores a date. It is the civil day in **Europe/Lisbon**, as `"YYYY-MM-DD"` — not a timestamp,
  not the device timezone. It is computed once outside the profile transaction and passed in via
  `GameResult.hoje`, because the transaction handler can retry across midnight. Don't confuse it
  with `maxStreak`, which is correct answers inside one game. See
  [`docs/vault/funcionalidades/streak-diario.md`](docs/vault/funcionalidades/streak-diario.md).

### RTDB rules gotchas

These caused real production breakage; see the security note for the full stories.

- `.validate` **does not run on deletes**. A permissive `.write` with a strict `.validate` lets
  anyone delete.
- `.write` cascades to descendants; `.validate` runs on all of them. Ownership on `/lobbies` is
  therefore enforced by `.validate`, because matchmaking transacts over the whole format node.
- Arithmetic on `null` invalidates the *entire* expression, even across `||`. Counters need a
  ternary: `data.exists() ? newData.val() === data.val() + 1 : newData.val() === 1`.
- There is no `numChildren()`.
- `/jogadores/$uid` has `$other: {".validate": false}` — **a new profile field is rejected until
  it is declared in the rules**. And because the aggregation transaction rewrites the whole node,
  one invalid leaf (e.g. an empty string in a date field with a regex) rejects the entire update,
  losing the player's points and XP for that game — not just the new field.
- **A rule revalidated by an ancestor transaction must be tested against pre-existing junk in the
  database, not a clean node.** A correct-in-isolation fix once passed 8/8 REST tests and still
  broke matchmaking for everyone, because 6 orphaned QA lobbies failed the new validation and one
  invalid leaf fails the whole transaction.

Test rules by REST with real tokens from two accounts, **both directions**: the attack must be
denied *and* the legitimate path must still pass.

### Solo modes

`GameMode` carries `vidas: Int` (0 = the mode doesn't eliminate). Eliminatórias has 3 lives and
**no question limit** — `questionCount = 20` is only the initial batch, refilled in the
background near the end and recycled reshuffled if the refill fails. Its "win" is a milestone
(`ELIMINATORIAS_MARCO_VITORIA`), not survival, because the run always ends in elimination.

### Multiplayer

One generalized N-player system serves 1x1, 2x2 and Grupo, parametrized by `MatchFormat`.

`MatchFormat` carries **two** numbers: `players` (capacity — auto-starts when full) and
`minPlayers` (enough to be *allowed* to start). Only Grupo differs (4 min, 10 capacity). Anything
user-facing about sizes must come from `MatchFormat.sizeLabel`, not hardcoded text — that string
silently disagreed with the code for several phases.

Matchmaking runs on `/lobbies/{format}` (`findOrCreateLobby`). **`/matchmakingN` and
`MultiMatchRepository.createRoom` are confirmed dead code** — the rules are still deployed but
nothing calls them.

Question timing is lockstep: the server stamps each question's start once
(`perguntaInicios/{index}`), and `serverTimeOffset` is re-read **per question**, not per match.

### Visual system

Sticker style (`ui/theme/Sticker.kt`). The load-bearing rule:

> **Per screen, gold means exactly one thing.** On result screens (podium, ranking, achievements)
> gold is merit and the primary action becomes purple. Everywhere else gold is the primary
> action. Tabs are never gold.

The XP bar is **solid Purple everywhere** (`XpFill`) — it used to be a gradient; don't
reintroduce one. Community Quizzes are hidden behind `FeatureFlags.QUIZZES_COMUNIDADE_VISIVEIS`
(the code is all still there and working — flip the flag to restore it).

`SegmentedTabs` = primary filter level (purple pill), `UnderlineTabs` = secondary. "This is me" is
always a purple outline, never a teal fill — "me" and "won" must not be the same signal.
Answer options are neutral with A/B/C/D badges at rest; colour only appears on reveal, because
colouring them earlier implied the answer.

## Working conventions

- **Documentation and code drift here.** Numbers shown in UI text should be derived from code.
  When migrating or summarising docs, flag stale information rather than silently "fixing" it.
- **Verify on device; don't deduce.** Several phases were spent on things that "should work".
  When claiming something is verified, state what was actually observed — a `SoundPool.play()`
  returning a stream id is not proof of audible sound (the emulators here run with `-no-audio`).
- Distinguish *verified* from *deduced* in reports.
- `/categorias` has `.write: false`, but **`firebase database:set` authenticates as admin and
  rules do not apply to it** — seed with the rules locked the whole time rather than unlocking
  them (the older documented procedure). Still back up first, and still confirm afterwards with
  an unauthenticated PUT that returns `Permission denied`.
- **The Firebase CLI cannot delete Auth accounts** — only `auth:export` / `auth:import`. Deleting
  accounts needs the Admin SDK with a service account key; `qa/apagar-contas-teste.js` is ready
  for that. Do not try to extract the CLI's refresh token.
- Stale `/lobbies` and `/multisalas` state from earlier QA causes ghost matchmaking. Clear them
  before testing multiplayer.
- The Portuguese/UI text of this project is in **European Portuguese**; commit messages and docs
  in the repo are Portuguese too.

## Files never committed

`app/google-services.json`, `upload-keystore.jks`, `keystore.properties` — all gitignored.
`keystore.properties.example` is the committed template. Release signing reads either the
properties file or `POL_STORE_FILE` / `POL_STORE_PASSWORD` / `POL_KEY_ALIAS` / `POL_KEY_PASSWORD`.

The `gh-pages` branch serves the legal pages from its own `/docs`; on `main`, `docs/` holds the
vault. Don't mix them.
