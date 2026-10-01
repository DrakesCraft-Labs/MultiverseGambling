# Development

**English** · [Español](Desarrollo-es)

## Build and test

```bash
mvn package      # compiles, runs the 197 tests and writes target/MultiverseGambling-1.0.4.jar
mvn test         # only the tests
mvn -q compile   # only the compiler
```

The build needs Java 21. Tests are JUnit 5 and cover the `engine`, `i18n` and `world`
packages, so they run in a couple of seconds with no server.

## Requirements of the toolchain

| Piece | Version |
|---|---|
| Java | 21 (`maven.compiler.release`) |
| Paper API | `1.21.11-R0.1-SNAPSHOT`, `provided` |
| Vault API | `1.7.1`, `provided` |
| JUnit | Jupiter 5.10.2, `test` |
| Filtering | Only `plugin.yml` is filtered; the other resources keep their placeholders |

## Project layout

```
src/main/java/com/chagui68/multiversegambling
├── engine/    plain Java tables and rules (no Bukkit); where the maths lives
├── economy/   EconomyProvider (sBank | Vault | internal), EconomyProviders, Wager, Pot, EconomyManager
├── fair/      FairnessService: server secret, client seeds, nonces
├── i18n/      Language, LanguageStore
├── world/     CasinoLayout (pure geometry), CasinoWorldManager (blocks)
├── game/      Game, GameMeta, AbstractSoloGame, AbstractGroupGame, GameRegistry
├── session/   SessionManager, SoloSession, TimedSession
├── gui/       Gui, GuiListener, HubGui, BetSelectorGui, StatsGui
├── games/solo/  12 games
├── games/group/ 9 games
├── stats/     PlayerStats, StatsStore
├── config/    MultiverseGamblingConfig, Messages
└── command/, listener/, util/
```

## The four classes that keep it honest

- **`Wager`** — a stake that can be settled once. `pay()`, `refund()` and `lose()` are idempotent
  by design, so a double payout is impossible.
- **`Pot`** — the shared pot of group games: one wager per player, refunds before the round,
  winnable by the others after it.
- **`SessionManager`** — one scheduler for the whole plugin; `shutdownAll()` settles every open
  game on shutdown.
- **`AbstractGroupGame`** — the round cycle (waiting → betting → in game) in one place, so no
  game can skip the charge or the refund. Games implement `onRoundStart()` and `tickRound()`.

## Adding a game

1. Write the prize table in `engine/` **with its tests** if the game needs one. That is where
   return invariants are checked.
2. Extend `AbstractSoloGame` (about 60 lines) or `AbstractGroupGame` (about 80) and declare its
   `GameMeta`.
3. For a solo game write `start(Player, double)`; for a group game write `onRoundStart()` and
   `tickRound()`.
4. Register the instance in `MultiverseGamblingPlugin.registerGames()`.

You get for free: permissions, balance checks, the bet selector, statistics, announcements, the
"play again" button, an arena in the casino world and a translatable name through
`catalog.<game-id>` in the language files.

## Conventions

- English is the language of the code, config, comments and documentation; Spanish lives in
  `lang/es.yml` and the `README.es.md` / `-es` wiki pages.
- Money never touches the animation RNG (`Rng`); it goes through `FairnessService`.
- Everything the player reads starts from a key in `lang/*.yml` (or a `catalog.<id>` block for
  game names).
- Prize tables stay in `engine/`, tested, and out of the Bukkit classes.

## What the tests guarantee

197 tests: return invariants of every table, crash expected value by formula and by 400,000
simulations, "nothing overpays" checks (including a deliberately cheating wheel that the
detector must catch), uniformity and determinism of the provably fair rolls, blackjack and dice
poker rules, simulated distributions, casino world geometry, item payouts that average exactly the money payout and
language resolution.

When you change a table run `mvn test` before committing: an accidental return above 1 fails
the build on purpose.
