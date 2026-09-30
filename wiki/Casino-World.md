# Casino World

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
