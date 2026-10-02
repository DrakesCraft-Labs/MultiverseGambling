# World

**English** · [Español](Mundo-Casino-es)

The plugin keeps all of its structures in a **separate world**, so the playable map stays
clean and nothing has to be pasted by hand.

```
/mvgam world          → travel to the casino
/mvgam world build    → rebuild the whole thing in the background (mvgam_admin)
/mvgam world info     → what exists, what is built and what is missing (mvgam_admin)
```

## What it creates

By default a **flat, 500 × 500 block world** named `mvgam_casino`, with a world border
centred on spawn and always at noon. No creature ever appears in it and only administrators
(`mvgam_admin`) can break or place blocks; fire, explosions and decaying leaves cannot change it:

| Piece | Details |
|---|---|
| **Plaza** | Radius 36 marble plaza: a dark centre, gold rings, eight spokes, a checkered quartz band, a lit fountain with a quartz pillar, eight grand lamps, flower planters and a hedge of flowering azalea. A giant golden coin spins over the fountain under the name of the plugin, and a welcome board faces spawn |
| **Pavilions** | One per registered game, 41 × 41 blocks: a dark stage with a gold ring in the middle, a black and white marble floor, walls with stained glass in the colour of the pavilion, a gate with a quartz arch and hanging lanterns on **every** side, four corner towers, quartz bleachers along the walls and invisible light blocks over the platform. The name of the game floats over it with its icon turning underneath, and is written over every gate |
| **Boulevards** | 7 blocks wide, dark stone with white kerbs and a dashed middle line, one along every row and column of the grid plus a ring road round the whole casino. Lamp posts on both sides and avenues of cherry, birch and oak trees |
| **Gardens** | The cells of the grid no game uses become roundabouts round a cherry tree |
| **Outskirts** | A belt of trees between the ring road and the border, a hedge along the border and grass and flowers on the lawns |
| **Spawn** | On the plaza, looking at the fountain and the welcome board |

The world is flat with `minecraft:plains` ground (bedrock, two dirt layers, grass) and
structures generation is off, so nothing of the vanilla world interferes. Mobs, weather,
fire spread and raids are switched off in the casino world.

If the world cannot be created — a folder without write permission, a name clash, a setting
the server rejects — the plugin reports it in the console and starts anyway: every game keeps
working from its menu, and `/mvgam world build` can be retried once the cause is fixed. Run
`/mvgam world info` to see which of those states the plugin is in; it answers from the console
too, which is where a failed start is usually being watched.

## How the build works

The whole design is planned first and then written **a few chunks per tick** (at most about
18 ms of every tick), so the server keeps running while the casino goes up; the first build
takes a few seconds. Every column from the ground up is compared with the design and only the
blocks that differ are changed, which also wipes whatever an older version left behind.

The world remembers a **signature of the design** (its version, the size, the games and their
boards). When the plugin is updated with a new design, a game is added or a board changes
size, the casino is **rebuilt on its own** on the next start. A rebuild clears the surface of
the casino world, so keep your own builds in other worlds.

## How the layout works

Pavilions sit on a square grid filled **from the middle outwards**, so the first registered
games are the closest to the plaza and the newest ones extend outwards. The centre cell is
reserved for the plaza, which means a grid of `n` columns holds `n² − 1` pavilions.

With the default values (plaza radius 36, pavilion radius 20, spacing 84, margin 16) the grid
of 22 games needs **408 of the 500 blocks**, so the default world fits everything and keeps a
green belt round the edge. The three cells left over are gardens.

Every pavilion has a gate on each side; the **main gate faces the plaza**, and the shows turn
to it, so a player walking in from spawn sees every show face on.

## Configuration

```yaml
world:
  enabled: true
  name: 'mvgam_casino'
  size: 500
  build-structures: true
  teleport-on-join: false
  always-day: true
  time: 13000
```

| Key | Notes |
|---|---|
| `enabled` | With `false` the plugin never creates the world and `/mvgam world` answers that it is disabled |
| `name` | Point it at a world you already have to reuse it: the plugin will build the casino there (and clear its surface) |
| `size` | 200 to 2000. If the layout does not fit, the world **grows in steps of 50** (up to 2000) instead of failing |
| `build-structures` | With `false` the world is created empty and you build it yourself |
| `teleport-on-join` | Sends every player to the casino when they join the server |
| `always-day` | Keeps the world at noon (`6000`) whatever `time` says; on by default |
| `time` | Hour the world is frozen at when `always-day` is off: `6000` noon, `13000` dusk, `18000` midnight; `-1` keeps the day cycle |

## In-world shows

The pavilions are not decoration: when a round is played and the casino world is ready, the
game **stages the round on its pavilion with display entities** (block, item and text
displays) instead of only counting numbers in the action bar. Every moving piece is handed to
the client with an interpolation time, so the motion is smooth at any frame rate, and the
pieces are fully lit, so a show looks the same at noon and at midnight.

| Game | Show |
|---|---|
| **Roulette** | A big roulette wheel leaning back like a table, with its numbers on the pockets, chasing bulbs round the rim and a ball thrown against the spin that spirals in and drops into the winning pocket |
| **Lucky wheel** | A wheel of fortune standing up, its multipliers written on it, that slows down tile by tile under a golden pointer |
| **Colour roulette** (group) | The real wheel of 18 red, 18 black and one green pocket, with its ball |
| **Jackpot** (group) | A wheel of fortune with a slice per player, **sized by the stake** like the draw itself, each slice with the name of its player |
| **Raffle** (group) | The same wheel, sliced by tickets, stopping on the first prize |
| **Slots** | A red cabinet with a lit marquee: the lever is pulled, three drums really roll, slow down and stop left to right; a pair or a triple lights up the payline |
| **Plinko** | A standing wall of pegs with the multiplier written under every bucket; the ball hops from peg to peg along the real bounces, lighting each peg it hits |
| **Crash** | A rocket that takes off from the corner of a chart and climbs along the curve of the multiplier, leaving a trail that turns from green to red, the multiplier counting in big letters; it flies off in gold when you cash out and blows apart when it crashes |
| **Dice** | A scoreboard with a bar from 0 to 100 split in the winning and the losing zone, a die that tumbles along it and a counter that spins until it stops on the roll |
| **Dice poker** (group) | Five dice thrown on a felt table, bouncing and settling one by one with their pips facing you |
| **Race** (group) | Real horses in armour dyed in the colour of their lane, galloping on a stepped track from the starting gates to a chequered line |
| **Russian roulette** (group) | A giant revolver: the cylinder spins on every pull, stops under the hammer, and either fires with a flash at the muzzle or clicks |
| **Hot bomb** (group) | A huge TNT that throbs faster as its fuse burns down, with sparks running along the fuse and a small TNT floating over the head of whoever holds the bomb |
| **Coin flip** | A coin tossed from a pedestal, flipping over and over and landing on its edge with the side that came up |
| **Duel** (group) | The same coin, with the heads of both players on either side: the winner lights up, the loser drops |
| **Blackjack** | A card table: the cards fly in from the shoe onto a board, the hole card turns over when the dealer plays and a banner announces the result |
| **High low** | The same card table: the cards already played on top, the current one below, turning into the next |

The scenery is **built when the round starts, left standing for a moment after the result
and taken down afterwards**, so a pavilion always goes back to its plain stage: even a player
disconnecting or the server stopping mid spin leaves nothing behind (the plug-in clears any
show still standing on shutdown, and the next build removes anything a crash left).
Only one round of a game is staged at a time: a second player spinning the same game while a
show is running keeps the action bar animation instead of painting over it. Menus are still
used for everything that is a decision rather than a result (betting, picking a spot,
cashing out).

## Playing on the blocks

Four games do not show a result: their round is a **sequence of picks**, so they are played
on the blocks of their stage. Clicking the stage of those four pavilions opens the game (the
bet selector, or the waiting room of the bomb board) and from then on the tiles are the
input, exactly like the menu buttons they replace. The walls, the bleachers and the gates
stay ordinary blocks.

| Game | Board | Playing it |
|---|---|---|
| **Mines** | A 5x5 grid of tiles, a cashier and a red and a green block to choose the mine count | Click tiles to reveal them; the gold block cashes out |
| **Towers** | One row of tiles per floor, the floors already climbed turning green | Click a tile of the lit floor; the gold block cashes out |
| **Scratch card** | A 3x3 card | Three clicks scratch three tiles |
| **Bomb board** (group) | The shared 9x4 board | The turn passes player to player and the current one is stood on the board |

The resting board is part of the world build. Every round puts its tiles back into the exact
block data they had, so a board never keeps the leftovers of a finished game. The mine count,
the bet and everything else that is a decision are still menus; the same `world.animations`
switches drive the boards, and with `enabled: false` the four games go back to their menus.

```yaml
world:
  animations:
    enabled: true           # stage the rounds on the pavilions
    teleport-players: true  # move the player (or the whole room) to the pavilion to watch
    view-distance: 14       # blocks between the stage centre and the watcher
```

| Key | Notes |
|---|---|
| `enabled` | With `false` every game keeps its classic action bar animation and the four block boards fall back to their menus |
| `teleport-players` | With `false` only players already in the casino world get a show; a solo game puts its watcher straight in front of the show, a group game fans the room out on an arc in front of it |
| `view-distance` | How far from the middle of the stage the spectators stand |

Games decide where to show their round, and a show never decides money: the result is
already drawn by the provably fair generator when the scenery is built. Adding a show to
another game is extending `ArenaShow`, drawing it in the local frame of the stage (the
audience on `+z`) and calling it from the round.

## Good to know

- **The main world is never touched.** The plugin refuses to build in the server's main world
  and logs a warning instead.
- **Rebuilding runs in the background.** `/mvgam world build` answers straight away and tells
  you when the casino is ready; `/mvgam world info` shows `building` meanwhile.
- **The floating names follow the default language**, so a Spanish server gets Spanish names
  over the pavilions (`catalog.<game-id>.name` with `language.default`); `/mvgam reload`
  refreshes them.
- **New games get a pavilion for free.** Register a game and the next start places it on the
  grid and rebuilds the casino without you touching any coordinate.
- **It is testable geometry.** `CasinoLayout`, `StageFrame`, `CrashCurve`, `PlinkoBoard` and
  `WheelMath` are pure classes with no Bukkit, and the suite checks that 22 games fit in 500
  blocks, that pavilions never overlap or cover spawn, that every pavilion sits on two
  boulevards connected to the plaza, that shows turn to the main gate without being mirrored
  and that the rocket never leaves its chart.

## Restoring a hand built casino

If you would rather design the casino yourself:

```yaml
world:
  enabled: false
```

Nothing is created or modified, and `/mvgam world` just tells players the world is disabled
while `/mvgam play <id>` keeps working from anywhere.
