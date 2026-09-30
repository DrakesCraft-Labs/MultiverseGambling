# Commands

**English** · [Español](Comandos-es)

The main command is `/casino`, with the aliases **`/gambling`**, **`/mg`** and **`/bets`**.
Everything is tab completed, and every answer is written in the language of whoever ran the
command.

## Playing

| Command | What it does |
|---|---|
| `/casino` | Opens the main menu: a solo tab and a group tab |
| `/casino menu` | Same as above |
| `/casino games [solo\|group]` | Lists the catalogue with the betting limits of each game |
| `/casino play <game>` | Plays a game by id or by name (`roulette`, `ruleta`, `slots`...) |
| `/casino action <action>` | Entry point of the **chat buttons** (`shoot`, `reveal`, `horse 3`, `accept`, `decline`) |

Inside a game, every action has its own menu: the bet selector lets you halve, double or
stake everything, and the game panels expose cash out, stand, double down or shoot.

## Your account

| Command | What it does |
|---|---|
| `/casino balance [player]` | Shows your balance, or somebody else's |
| `/casino stats [player]` | Games played, wins, wagered, real return and favourite game |
| `/casino top [profit\|wagered\|prize]` | Server ranking by net profit, volume or biggest win |

`/casino top` needs `casino.top`, which everybody has by default.

## Fairness

| Command | What it does |
|---|---|
| `/casino verify` | Shows the current secret hash, the previous secret and your seed |
| `/casino verify <text>` | Changes your client seed to that text |

See [Fairness](Fairness) for the full explanation.

## Casino world

| Command | What it does |
|---|---|
| `/casino world` | Teleports you to the casino world (fails gracefully when it is not ready) |
| `/casino world build` | Rebuilds the plaza, the roads and every arena (**`casino.admin`**) |

## Languages

| Command | What it does |
|---|---|
| `/casino language` | Shows your language and the available ones |
| `/casino language <code>` | Switches to that language (`en`, `es`, `ES`, `es-AR`, `spanish`, `español`...) |
| `/casino language reset` | Forgets your choice and follows your Minecraft client again |

The alias `lang` works too: `/casino lang es`.

## Administration

| Command | What it does |
|---|---|
| `/casino info` | Active economy, provider, registered games and current secret |
| `/casino reload` | Reloads `config.yml` and every `lang/*.yml` without touching running games |
| `/casino give <player> <amount>` | Adds balance |
| `/casino take <player> <amount>` | Removes balance |
| `/casino set <player> <amount>` | Sets the balance to an exact value |
| `/casino cancel <player>` | Closes somebody's game and refunds their money |

Amounts accept a comma or a dot: `1000`, `1000.50`, `1000,50`.

All of them need `casino.admin`, which operators have. See [Permissions](Permissions).

## Subcommand aliases

| Alias | Real subcommand |
|---|---|
| `list` | `games` |
| `bet` | `play` |
| `ranking` | `top` |
| `fair` | `verify` |
| `lang` | `language` |

## Examples

```
/casino play crash            → open crash and pick your bet
/casino play horse-race       → join the next race round
/casino action shoot          → used by the russian roulette button
/casino top prize             → ranking of biggest wins
/casino language es           → everything you read is now Spanish
/casino world build           → rebuild the casino world from scratch
```
