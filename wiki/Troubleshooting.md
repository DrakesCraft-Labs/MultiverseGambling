# Troubleshooting

**English** · [Español](Problemas-es)

## The casino world is not there

```
/mvgam world      → "The casino world is not ready yet; check the server console."
```

Run `/mvgam world info` for a report of what exists, what is built and what is missing; it
works from the console too. The usual causes are:

| Console message | Fix |
|---|---|
| `world is disabled` | Set `world.enabled: true` in `config.yml` and run `/mvgam reload` |
| `too small` | Raise `world.size`; the plugin also grows it automatically in steps of 50 |
| `refuses to build in the main world` | `world.name` points at your survival world: rename it |
| `could not be created` | The folder is not writable, or a world by that name is already loaded with other settings |
| `The casino world could not be prepared` | The plugin started without it on purpose. Read the trace above, fix the cause and run `/mvgam world build` |

If you only wanted the games, disable the world entirely (`world.enabled: false`): `/mvgam
play <id>` keeps working from anywhere.

## A game says it is disabled

`games.<id>.enabled: false` in `config.yml`. Enable it and `/mvgam reload`. Disabled games
also lose their arena in the next `/mvgam world build`.

## A player is stuck in a game

```
/mvgam cancel <player>
```

Closes their game and refunds the stake. It needs `mvgam_admin`.

## The money is not mine, it is the server's

Check the active provider with `/mvgam info`. If it says `Vault`, every balance comes from
your economy plugin and the plugin never writes to it directly. If it says `Internal`, the
plugin is using `balances.json`; switch `economy.provider` to `vault` to hand money back to
your economy.

## A file named `something.json.corrupt` appeared

A data file (`balances.json`, `stats.json`, `fairness.json`, `languages.json`) was not valid
JSON: usually the server was killed while the file was being written, or it was edited by hand
until it stopped being JSON. The plugin never refuses to start for that: it moves the file
aside, starts with an empty one, and keeps working. Look at the `.corrupt` copy if you want to
recover entries, merge them back into the live file and run `/mvgam reload` (or restart).

The same rule covers the whole startup: a casino world that cannot be prepared, a game that
cannot be built or a data file that cannot be read is reported in the console and skipped,
and everything else keeps running. If you never wanted the casino world, `world.enabled:
false` avoids the attempt altogether.

## Wins are not announced

`game.announce-wins: false`, or the win is below `game.announce-threshold` (50000 by default).

## Text shows `[missing message: something.key]`

A language file is missing that key. The plugin shows the marker instead of breaking, and
falls back to English first. Copy the key from `lang/en.yml`, or delete the file so it is
regenerated and redo your edits.

## A language is not offered in `/mvgam language`

Only files that exist on disk are offered. Check `plugins/MultiverseGambling/lang/<code>.yml`
exists (the code is the file name without the extension) and run `/mvgam reload`.

## The plugin only answers in English

`language.default` is `en` and nobody picked anything: the console and the players with no
preference read English. Players can pick with `/mvgam language es`, or set
`language.default: es` for the whole server.

## The slots/plinko/scratch/wheel return changed

That is on purpose: the suite pins those returns. `mvn test` tells you the exact table that
went out of range, so you can correct the weights or the payouts deliberately.

## Signature of a missing dependency

`UnsupportedClassVersionError` on start means the server runs an older Java: Paper 1.21 needs
Java 21. Vault is only needed when `economy.provider` is `vault` or `auto` **and** you want to
use the server economy.
