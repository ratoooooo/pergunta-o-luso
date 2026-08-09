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

### RTDB rules gotchas

These caused real production breakage; see the security note for the full stories.

- `.validate` **does not run on deletes**. A permissive `.write` with a strict `.validate` lets
  anyone delete.
- `.write` cascades to descendants; `.validate` runs on all of them. Ownership on `/lobbies` is
  therefore enforced by `.validate`, because matchmaking transacts over the whole format node.
- Arithmetic on `null` invalidates the *entire* expression, even across `||`. Counters need a
  ternary: `data.exists() ? newData.val() === data.val() + 1 : newData.val() === 1`.
- There is no `numChildren()`.
- **A rule revalidated by an ancestor transaction must be tested against pre-existing junk in the
  database, not a clean node.** A correct-in-isolation fix once passed 8/8 REST tests and still
  broke matchmaking for everyone, because 6 orphaned QA lobbies failed the new validation and one
  invalid leaf fails the whole transaction.

Test rules by REST with real tokens from two accounts, **both directions**: the attack must be
denied *and* the legitimate path must still pass.

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
- `/categorias` has `.write: false`. Seeding requires temporarily unlocking rules, backing up,
  writing, then re-locking **and confirming with a PUT that returns `Permission denied`**.
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
