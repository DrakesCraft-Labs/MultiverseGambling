# Troubleshooting

**English** · [Español](Problemas-es)

## The casino world is not there

```
/casino world      → "The casino world is not ready yet; check the server console."
```

Look at the console. The usual causes are:

| Console message | Fix |
|---|---|
| `world is disabled` | Set `world.enabled: true` in `config.yml` and run `/casino reload` |
| `too small` | Raise `world.size`; the plugin also grows it automatically in steps of 50 |
| `refuses to build in the main world` | `world.name` points at your survival world: rename it |
| `could not be created` | The folder is not writable, or a world by that name is already loaded with other settings |

If you only wanted the games, disable the world entirely (`world.enabled: false`): `/casino
play <game>` keeps working from anywhere.

## A game says it is disabled

`games.<id>.enabled: false` in `config.yml`. Enable it and `/casino reload`. Disabled games
also lose their arena in the next `/casino world build`.

## A player is stuck in a game

```
/casino cancel <player>
```

Closes their game and refunds the stake. It needs `casino.admin`.

## The money is not mine, it is the server's

Check the active provider with `/casino info`. If it says `Vault`, every balance comes from
your economy plugin and the plugin never writes to it directly. If it says `Internal`, the
plugin is using `balances.json`; switch `economy.provider` to `vault` to hand money back to
your economy.

## Wins are not announced

`game.announce-wins: false`, or the win is below `game.announce-threshold` (50000 by default).

## Text shows `[missing message: something.key]`

A language file is missing that key. The plugin shows the marker instead of breaking, and
falls back to English first. Copy the key from `lang/en.yml`, or delete the file so it is
regenerated and redo your edits.

## A language is not offered in `/casino language`

Only files that exist on disk are offered. Check `plugins/MultiverseGambling/lang/<code>.yml`
exists (the code is the file name without the extension) and run `/casino reload`.

## The plugin only answers in English

`language.default` is `en` and nobody picked anything: the console and the players with no
preference read English. Players can pick with `/casino language es`, or set
`language.default: es` for the whole server.

## The slots/plinko/scratch/wheel return changed

That is on purpose: the suite pins those returns. `mvn test` tells you the exact table that
went out of range, so you can correct the weights or the payouts deliberately.

## Signature of a missing dependency

`UnsupportedClassVersionError` on start means the server runs an older Java: Paper 1.21 needs
Java 21. Vault is only needed when `economy.provider` is `vault` or `auto` **and** you want to
use the server economy.
