# World

**English** · [Español](Mundo-Casino-es)

The plugin can keep all of its structures in a **separate world**, so the playable map stays
clean and nothing has to be pasted by hand.

```
/mvgam world          → travel to the casino
/mvgam world build    → rebuild the whole thing (mvgam_admin)
```

## What it creates

By default a **flat, 500 × 500 block world** named `mvgam_casino`, with a world border
centred on spawn:

| Piece | Details |
|---|---|
| **Plaza** | Radius 30 paved disc around spawn: polished deepslate, smooth quartz and a gold centre, a polished blackstone kerb, a gold monument with a sea lantern, four diagonal lamps and a welcome sign |
| **Arenas** | One per registered game, radius 12: a polished blackstone border, a gold medallion, a 15 colour palette, an oak fence with a single 3 block opening, four corner lamps and a sign with the game name |
| **Roads** | 3 blocks wide, polished diorite, linking the plaza with every arena |
| **Spawn** | In front of the plaza, looking at the monument |

The world is flat with `minecraft:plains` ground (bedrock, two dirt layers, grass) and
structures generation is off, so nothing of the vanilla world interferes.

## How the layout works

Arenas sit on a square grid filled **from the middle outwards**, so the first registered games
are the closest to the plaza and the newest ones extend outwards. The centre cell is reserved
for the plaza, which means a grid of `n` columns holds `n² − 1` arenas.

With the default values (plaza radius 30, arena radius 12, spacing 110, margin 8) the grid of
21 games needs **480 of the 500 blocks**, so the default world fits everything with room to
spare.

The entrance of every arena always faces the plaza: it is the side the road arrives at, so a
player walking down a road sees the sign of the game before entering.

## Configuration

```yaml
world:
  enabled: true
  name: 'mvgam_casino'
  size: 500
  build-structures: true
  teleport-on-join: false
```

| Key | Notes |
|---|---|
| `enabled` | With `false` the plugin never creates the world and `/mvgam world` answers that it is disabled |
| `name` | Point it at a world you already have to reuse it: the plugin will build the arena grid there |
| `size` | 200 to 2000. If the layout does not fit, the world **grows in steps of 50** (up to 2000) instead of failing |
| `build-structures` | With `false` the world is created empty and you build it yourself |
| `teleport-on-join` | Sends every player to the casino when they join the server |

## In-world shows

The arenas are not decoration: when a round is played and the casino world is ready, the
game paints the result on its own arena instead of only counting numbers in the action bar.

| Game | Show |
|---|---|
| **Roulette** | A round table of coloured pockets inside a golden rim, with a ball that hurtles around it and settles on the winning number |
| **Lucky wheel** | The same table, with a sector per tile: grey pays nothing, gold is the top prize |
| **Colour roulette** (group) | The real wheel of 18 red, 18 black and one green pocket; the ball lands on the colour that came up |
| **Jackpot** (group) | A wheel of tickets, one colour per player, stopping on the ticket that took the pot |
| **Raffle** (group) | The same drum of tickets, stopping on the first prize |
| **Slots** | A three reel cabinet whose reels flash through the symbol table and stop left to right, leaving the combination on the payline |
| **Plinko** | A pyramid of pegs on the floor with the ball taking the real bounces and the bucket it lands in left lit |
| **Crash** | A tower that climbs one block per doubling of the multiplier, gold when you cash out, scorched when it bursts |
| **Dice** | A number line from 0 to 100 with the target in red and a marker that slides up to the roll, leaving the stretch it covered green or red |
| **Dice poker** (group) | The winning hand as five dice with raised pips, each die stopping in turn |
| **Race** (group) | A lane per horse running away from the watcher towards a golden finish line |
| **Russian roulette** (group) | The revolver cylinder drawn on the floor, loaded chambers in red, turning on every trigger |
| **Hot bomb** (group) | A block of TNT on a podium with a fuse that shortens with the drawn time and a scorch mark when it goes off |
| **Coin flip** | A gold coin tumbling over the arena, landing on the side that came up and paving it underneath |

The scenery is **built when the round starts, left standing for a second after the result
and taken down afterwards**, so arenas always go back to their plain platform: even a
player disconnecting or the server stopping mid spin leaves no leftover blocks or floating
entities behind (the plug-in clears any show still standing on shutdown). Menus are still
used for everything that is a decision rather than a result (betting, picking a spot,
cashing out).

Four games deliberately keep only their menu, because their round is a **sequence of
picks** rather than a result to watch: mines, towers, scratch cards and the group bomb
board. Playing those in the world would mean clicking blocks in the arena, a different
feature. Blackjack and high low stay in their card menus for the same reason.

```yaml
world:
  animations:
    enabled: true           # paint results on the arena
    teleport-players: true  # move the player (or the whole room) to the arena to watch
    view-distance: 11       # blocks between the arena centre and the watcher
```

| Key | Notes |
|---|---|
| `enabled` | With `false` every game keeps its classic action bar animation |
| `teleport-players` | With `false` only players already in the casino world get a show; solo games gather one watcher in front of the table, group games spread the room in a circle round it |
| `view-distance` | How far from the middle the spectator stands, looking down at the table |

Games decide where to show their round, and a show never decides money: the result is
already drawn by the provably fair generator when the scenery is built. Adding a show to
another game is extending `ArenaShow` and calling it from the round.

## Good to know

- **The main world is never touched.** The plugin refuses to build the structures in the
  server's main world and logs a warning instead.
- **Rebuilding is safe.** `/mvgam world build` clears the casino world and lays everything
  out again; players inside are moved to spawn.
- **Sign text follows the default language**, so a Spanish server gets Spanish arena signs
  (`catalog.<game-id>.name` with `language.default`).
- **New games get an arena for free.** Register a game and the next build places it on the
  grid without you touching any coordinate.
- **It is testable geometry.** `CasinoLayout` is a pure class with no Bukkit, and the suite
  checks that 21 games fit in 500 blocks, that arenas never overlap or cover spawn, that
  entrances face the plaza and that too small a world is rejected.

## Restoring a hand built casino

If you would rather design the casino yourself:

```yaml
world:
  enabled: false
```

Nothing is created or modified, and `/mvgam world` just tells players the world is disabled
while `/mvgam play <game>` keeps working from anywhere.
