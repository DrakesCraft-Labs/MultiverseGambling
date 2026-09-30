# MultiverseGambling

**English** · [Español](README.es.md) · [Wiki](wiki/Home.md)

Chance and betting engine for **Paper 1.21.11** with **21 minigames**: 12 solo games
against the house and 9 group games with automatic rounds, all of them inside a
dedicated **casino world** and playable in **English or Spanish**.

This is not a loose pile of commands: it is an engine where every coin goes through the
same path, every money roll comes from **provably fair** randomness and every prize table
is **pinned by tests**.

```
mvn package      →  target/MultiverseGambling-1.0.0.jar
```

---

## Contents

- [Why this design](#why-this-design)
- [Installation](#installation)
- [The casino world](#the-casino-world)
- [Languages](#languages)
- [The catalogue](#the-catalogue)
- [Real returns](#real-returns)
- [Provably fair](#provably-fair)
- [Commands](#commands)
- [Permissions](#permissions)
- [Configuration](#configuration)
- [Architecture](#architecture)
- [Adding a new game](#adding-a-new-game)
- [What the tests cover](#what-the-tests-cover)

---

## Why this design

A server casino always breaks in the same three ways: a prize table that pays too much,
a payout applied twice, or a roll a player can predict. MultiverseGambling is built so
those three failures are **impossible by construction** instead of avoided by care.

1. **The mathematics of chance live in `com.chagui68.multiversegambling.engine`, with no
   Bukkit.** They are plain Java classes (roulette, mines, crash, plinko, blackjack, dice
   poker, slots, scratch cards, prize wheel, provably fair roll). They are tested in
   milliseconds and the plugin can only *use* those formulas, never reinterpret them.

2. **Every coin goes through `EconomyManager` and `Wager`.** A bet is taken out of the
   wallet when the game starts and wrapped in an object that **can be settled only once**.
   Paying the same round twice stops being possible, not just unlikely.

3. **Randomness that decides money is always `FairnessService`.** The plain `Rng` is only
   used to paint animations. Anybody can recompute a roll afterwards.

The tests have already caught real bugs during development: the provably fair generator
returned half of its rolls negative, Plinko paid nine times too much for forgetting the
bucket divisor, the roulette wheel computed its edge wrong, coin flip had no edge at all
and "classic" roulette offered betting on green at 2x, which is a scam. Every one of them
is now covered by a test that stops them from coming back.

---

## Installation

**Requirements:** Paper 1.21.11, Java 21. Vault is optional but recommended.

The plugin builds against `paper-api:1.21.11-R0.1-SNAPSHOT` and declares
`api-version: '1.21'`, so it also loads on any 1.21.x server. It uses no NMS and no
internal modules, only the public API.

```bash
mvn package
cp target/MultiverseGambling-1.0.0.jar ~/server/plugins/
```

Without Vault the plugin starts its own wallet in
`plugins/MultiverseGambling/balances.json`, with a configurable welcome balance. With
Vault it uses the server economy and duplicates nothing. Control it with
`economy.provider: auto | vault | internal`.

Data files it creates:

| File | Contents |
|---|---|
| `balances.json` | Internal wallet (only without Vault) |
| `stats.json` | Per player statistics and rankings |
| `fairness.json` | Client seeds and the server secret for the audit |
| `languages.json` | The language each player picked |

---

## The casino world

The plugin can build and take care of a **separate world** that holds every structure, so
nothing has to be pasted by hand and the playable world stays clean.

```
/mvgam world          → teleports you to the casino
/mvgam world build    → rebuilds the plaza, the roads and every arena (admin)
```

By default it creates a **flat 500 × 500 block world** named `mvgam_casino` with a
centred world border, and lays out, on first use:

- a **central plaza** (radius 30) as a paved disc with a kerb, a gold monument, four
  lamps and a welcome sign, holding the spawn point;
- **one arena per game** (radius 12), on a square grid filled from the middle outwards,
  so the most played games sit closest to the plaza and the newest ones extend outwards;
- **three block wide roads** linking the plaza with every arena;
- each arena gets its own colour palette, a fence with a single entrance opening that
  always faces the plaza, four corner lamps and a sign with the game name.

Everything is driven by `world:` in [config.yml](src/main/resources/config.yml):

```yaml
world:
  enabled: true            # create/load the world on start
  name: 'mvgam_casino'
  size: 500                # side of the square, in blocks (200-2000)
  build-structures: true   # plaza, arenas and roads on first use
  teleport-on-join: false  # send every player here when they join
```

Notes worth knowing:

- The **geometry is a pure class** (`CasinoLayout`) with no Bukkit, so it is unit tested:
  the suite checks that 21 games fit in 500 blocks, that no two arenas overlap, that
  nothing covers spawn and that the entrance of every arena faces the plaza.
- If `world.size` is too small for the grid, the plugin **grows the world** in steps of 50
  blocks (up to 2000) instead of failing to build.
- Point `world.name` at an existing world to reuse it, or set `enabled: false` and build
  the casino manually: `/mvgam world` then simply tells you the world is disabled.
- The plugin refuses to build the structures in the server's main world, so it never
  overwrites the spawn of a survival map.
- Sign text uses the catalogue of the default language, so a Spanish server gets Spanish
  arena signs.

The full reference is in the [Casino World](wiki/Casino-World.md) wiki page.

---

## Languages

The plugin translates **itself inside the game**. Every player picks what they read, and
admin commands answer in the language of whoever ran them.

```
/mvgam language es      → this player now reads Spanish
/mvgam language en      → back to English
/mvgam language         → shows the current language and the available ones
/mvgam language reset   → follow my Minecraft client again
```

How it works:

- Every language is a file in `plugins/MultiverseGambling/lang/`, one code per file:
  `en.yml`, `es.yml`. Both are written out on first start, and a legacy `messages.yml` is
  migrated into `lang/en.yml` automatically.
- The choice is stored **per player** in `languages.json`, so it survives restarts, and
  the lookup order is: the player's chosen language → their Minecraft client locale (when
  `language.follow-client` is `true`) → `language.default` → English.
- The console and the world signs use `language.default`.
- Adding a language is dropping a file: copy `lang/en.yml` to `lang/<code>.yml`,
  translate it, and the code immediately shows up in `/mvgam language`. Unknown codes are
  rejected with the list of available ones.
- Loose input is accepted: `es`, `ES`, `es_es`, `es-AR`, `spanish` and `español` all mean
  Spanish.
- A missing key renders as `&c[missing message: <key>]` instead of throwing, so a partial
  translation degrades gracefully.
- Game **names and descriptions** are written in the code in English and can be overridden
  per language with a `catalog.<game-id>.name` / `catalog.<game-id>.description` block;
  `lang/es.yml` ships the whole Spanish catalogue as an example.

```yaml
language:
  default: en          # console, players with no preference, world signs
  follow-client: true  # a player who never picked follows their Minecraft client
```

Details, including how to add a third language, are in the [Languages](wiki/Languages.md) wiki page.

---

## The catalogue

### Solo (12)

| Game | Id | Rules | Pays |
|---|---|---|---|
| **Classic Roulette** | `roulette` | Red, black, even/odd, 1-18/19-36, dozens, columns or an exact number. The 0 is green and pays **only** the straight bet, like a real wheel. | 2x / 3x / 36x |
| **Slots** | `slots` | Three reels, seven weighted symbols. Three of a kind pay the table; cherries pay something with two. | up to 600x |
| **Crash** | `crash` | The curve climbs on its own and you have to cash out before it bursts. The crash point comes from a single provably fair roll. | 1.00x and up |
| **Mines** | `mines` | 25 tiles with 1 to 24 mines. Every safe pick raises the multiplier; you cash out whenever you want. | grows with difficulty |
| **Towers** | `towers` | 9 floors, 4 tiles and 1 bomb per floor. Pick a safe tile to climb, cash out before you fall. | grows per floor |
| **Blackjack** | `blackjack` | Six deck shoe. Natural pays 3:2, a push returns the bet, doubling allowed. The dealer can hit a soft 17 (configurable). | up to 2.5x |
| **High-Low** | `high-low` | Guess whether the next card is higher or lower and chain correct calls. Each step is priced from the ranks that really remain. | chainable |
| **Dice** | `dice` | Target from 0.01 to 99.99, betting over or under. Fair payout, trimmed. | up to ~99x |
| **Plinko** | `plinko` | The ball falls through the pyramid. The buckets come from the real binomial distribution, not from an invented table. | up to hundreds of x |
| **Scratch** | `scratch` | Scratch 3 of 9 tiles. Three of a kind pay the symbol prize, two give part of the stake back. | up to 50x |
| **Lucky Wheel** | `lucky-wheel` | 12 equally likely segments, most of them empty and a couple of big hits. | up to 4x |
| **Coin Flip** | `coin-flip` | Pick heads or tails. Fair payout, trimmed (not a fixed 2x: that would give the house no edge). | ~1.96x |

### Group (9)

All of them run in automatic rounds: whoever wants joins, bets during the window, the
round plays itself and the next one starts without anybody typing a command.

| Game | Id | Rules | Players |
|---|---|---|---|
| **Color Roulette** | `color-roulette` | Everybody bets red, black or green. Green is a single pocket, so it pays ~36x with the same edge as red. | 2-24 |
| **Jackpot** | `jackpot` | Everybody puts money in and the tickets are proportional to what was staked. One winner takes it all. | 2-24 |
| **Hot Bomb** | `hot-bomb` | The TNT passes from hand to hand with a drawn fuse. Whoever it catches is out and their money goes to the pot. | 2-12 |
| **Bomb Board** | `bomb-board` | Shared board with hidden bombs; players take turns revealing tiles. The last one standing collects. | 2-12 |
| **Russian Roulette** | `russian-roulette` | In turns, each player pulls the trigger with 1 bullet in 6 chambers. The survivor takes the pot. | 2-8 |
| **Horse Race** | `race` | 8 horses with published odds. Backing the favourite pays little; the outsider pays a lot. | 2-24 |
| **Duel 1v1** | `duel` | You challenge somebody for a stake; both put the same in and a coin decides. If they do not accept in time you get your money back. | 2 |
| **Raffle** | `raffle` | Tickets at a fixed price and a draw of **three prizes**: 70%, 20% and 10% of the pot. | 2-24 |
| **Dice Poker** | `dice-poker` | Five dice each; the best hand wins. Ties split the pot. | 2-16 |

Game ids are stable and also accept the localised name, so `/mvgam play ruleta` works on a
Spanish server and `/mvgam play roulette` on an English one.

---

## Real returns

These numbers are not estimates: they come out of the formulas and are pinned by the test
suite. `mvn test` checks them again on every build.

| Game | Return | Note |
|---|---|---|
| Classic Roulette (European) | **97.30%** | Every bet shares the return: 36/37 |
| Classic Roulette (American) | **94.74%** | The 00 doubles the edge |
| Color Roulette | **97.30%** | Same on red, black and green |
| Slots | **94.75%** | Hand calibrated table, verified |
| Crash | **98.00%** | Same expected value at any target |
| Mines / Towers | **98.00%** | The multiplier is the exact inverse of the probability |
| High-Low | **~98.15%** | Per step, pushes included |
| Dice / Plinko | **98.00%** | Plinko drops a little more because of the 500x cap |
| Coin Flip | **98.00%** | Fair payout, trimmed |
| Scratch | **92.15%** | The most generous card on small prizes |
| Lucky Wheel | **95.00%** | It is the average of its 12 segments |
| Horse Race | **98.00%** | Each horse separately |
| Jackpot, Raffle, Dice Poker, Duel, Bomb Board, Hot Bomb, Russian Roulette | **100% − commission** | Player against player: no commission by default |

Every game against the house uses `game.house-edge` (2% by default) except those whose
edge is fixed by the wheel itself, like roulette.

---

## Provably fair

On start the plugin generates a secret and **publishes its hash**:

```
/mvgam verify
```

Every roll is derived from `HMAC-SHA256(secret, playerSeed:nonce:cursor)`. When the secret
rotates the previous one is revealed and anybody can recompute the rolls to check that the
house did not touch them. The client seed is yours and can be changed with
`/mvgam verify <text>`; mixing it with the server secret is what stops the server from
choosing the result after seeing your bet.

Rolls inside group games are attributed to the fixed casino identity (zero UUID), so a hot
bomb round or a horse race can be audited from start to finish.

---

## Commands

| Command | What it does |
|---|---|
| `/mvgam` | Opens the main menu with the two tabs |
| `/mvgam games [solo\|group]` | Lists the catalogue with its betting limits |
| `/mvgam play <game>` | Plays a game by id or by name |
| `/mvgam action <action>` | Entry point for the **chat buttons** (shoot, horse 3, accept...) |
| `/mvgam balance [player]` | Shows the balance |
| `/mvgam stats [player]` | Statistics: games, real return, favourite game |
| `/mvgam top [profit\|wagered\|prize]` | Server ranking |
| `/mvgam verify [seed]` | Fairness audit and client seed change |
| `/mvgam world [build]` | Teleports to the casino world, or rebuilds it |
| `/mvgam language [code\|reset]` | Switches the language this player reads |
| `/mvgam info` | Active economy, games and current secret |
| `/mvgam give \| take \| set` | Balance administration |
| `/mvgam cancel <player>` | Closes somebody's game and refunds their money |
| `/mvgam reload` | Reloads config and messages without touching running games |

The command is `/mvgam` and it has **no aliases**: each subcommand has exactly one spelling,
so tab completion and the messages can never disagree.

Tags, permissions and the world it creates carry the `mvgam_` prefix, so a server that also
runs other plugins never mixes an identifier: `mvgam_play`, `mvgam_top`, `mvgam_admin` and
the `mvgam_casino` world.

## Permissions

| Permission | Default | Allows |
|---|---|---|
| `mvgam_play` | everyone | Entering the casino, playing the public games, picking a language |
| `mvgam_top` | everyone | Viewing the server ranking |
| `mvgam_admin` | operators | Reload, build the casino world, give/take balance, cancel games |

---

## Configuration

Everything lives in `plugins/MultiverseGambling/config.yml`, in English and commented:
economy (`provider`, `currency`, `format`, `starting-balance`), house edge and bet limits,
fairness, the save interval, `language`, `world`, the shared `group` values and one entry
per game with its own options. See the
[Configuration](wiki/Configuration.md)
wiki page for every key, and read the header of the file before touching a prize table: the
return of slots, plinko, scratch and the lucky wheel is pinned by tests, so run `mvn test`
after changing weights or payouts.

---

## Architecture

```
com.chagui68.multiversegambling
├── engine/        ← plain Java, no Bukkit, covered by tests
│   ├── ProvablyFair, Rng, WeightedTable
│   ├── RouletteTable, ColorWheel, PrizeWheel, SlotsTable, ScratchCardTable
│   ├── MinesTable, CrashTable, DiceTable, PlinkoTable
│   ├── Card.Deck, BlackjackHand, DicePoker, HorseOdds
├── economy/       ← EconomyProvider (Vault | internal), Wager, Pot, EconomyManager
├── fair/          ← FairnessService: server secret, seeds, nonces
├── i18n/          ← Language, LanguageStore (per player choice)
├── world/         ← CasinoLayout (pure geometry), CasinoWorldManager (blocks)
├── game/          ← Game, GameMeta, AbstractSoloGame, AbstractGroupGame, GameRegistry
├── session/       ← SessionManager (one clock), SoloSession, TimedSession
├── gui/           ← Gui, GuiListener, HubGui, BetSelectorGui, StatsGui
├── games/solo/    ← the 12 solo games
├── games/group/   ← the 9 group games
├── stats/         ← PlayerStats, StatsStore
├── config/        ← MultiverseGamblingConfig, Messages (locale aware)
├── command/  listener/  util/
```

Four pieces deserve an explanation:

**`Wager`** is a bet already taken. `pay()`, `refund()` and `lose()` mark the bet as
settled, so a second call does nothing. It is the difference between "remember not to pay
twice" and "paying twice cannot happen".

**`Pot`** is the shared pot of the group games. It keeps one `Wager` per player, so the
money is out of the wallets since the betting window. When somebody disconnects mid round
they do not get their money back: their bet stays in the pot and anyone else can win it. If
they leave before the round starts they are refunded in full. There is no path where money
stops halfway.

**`SessionManager`** is a single scheduler for the whole plugin instead of one task per
player. On shutdown `shutdownAll()` closes every game and refunds the ones that were half
way. No orphan inventories, no dangling tasks.

**`AbstractGroupGame`** carries the whole round cycle (waiting → betting → in game) only
once, so no game can skip the charge or the refund. A group game just writes
`onRoundStart()`, `tickRound()` and calls `endRound()` when it finishes; all the money
bookkeeping is guaranteed by the base class.

---

## Adding a new game

A solo game is around 60 lines: extend `AbstractSoloGame`, declare its `GameMeta` and write
`start(Player, double)`. It already inherits permissions, balance checks, the bet selector,
statistics tickets, global announcements and the "play again" button. Its display name and
description come from `displayName(sender)` / `displayDescription(sender)`, which read
`catalog.<game-id>.*` when the language file has them.

A group game is around 80: extend `AbstractGroupGame` and write `onRoundStart()` and
`tickRound()`. The lobby, the betting window, the pot, the disconnections and the payout
are handled by the base class.

In both cases, if the game needs a new prize table, **that table goes in the `engine`
package with its tests**, not inside the game. Finally register the instance in
`MultiverseGamblingPlugin.registerGames()`: it is the only extra line. A new game also gets
its own arena in the casino world automatically, and its name is translated by dropping a
`catalog.<game-id>` block into each `lang/*.yml`.

---

## What the tests cover

**107 tests.** They do not check that the code does what it says, but that it **cannot be
exploited**:

- **Return invariants.** The Mines multiplier times the probability of surviving is exactly
  `1 − edge` for the 24 difficulties and every number of revealed tiles. On the roulette
  wheel *every* bet shares the same return.
- **Crash expected value.** Cashing out at any target has the same expected value, checked
  by formula and with 400,000 simulations.
- **Nothing can overpay.** The Plinko table never returns more than it takes, even without
  the cap; slots are calibrated at 94.75%; the scratch card at 92.15%; and a helper test
  builds a wheel that gives money away on purpose to check that the cheat detector works.
- **Fairness uniformity.** 100,000 rolls spread over 10 buckets, each at 10%. Plus
  determinism: the same seed and nonce always give the same result.
- **Rule correctness.** Blackjack aces as 11 or 1, the 8 dice poker categories with their
  tie breakers, the 5 star categories, the straights that must not count, the 0 that pays
  no outside bet.
- **Simulated distributions.** The Plinko ball follows the binomial, each horse wins as
  often as its strength, each wheel colour comes up as often as its pockets and the jackpot
  winner is drawn with the probability they deserve.
- **Casino world geometry.** 21 games fit in 500 blocks, arenas never overlap or cover
  spawn, entrances face the plaza, every arena has a road, too small a world is rejected and
  a single game still gets a ring arena.
- **Language resolution.** `es`, `ES`, `es_es`, `es-AR`, `spanish` and `español` all resolve
  to Spanish, unknown codes fall back to English and the shipped codes are stable.

```bash
mvn test
```

---

Built by **Chagui68** — [MultiverseGambling](https://github.com/DrakesCraft-Labs/MultiverseGambling).
