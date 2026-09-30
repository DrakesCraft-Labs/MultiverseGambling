# Installation

**English** · [Español](Instalacion-es)

## Requirements

| Piece | Version |
|---|---|
| Server | Paper 1.21.11 (the jar also loads on 1.21.x) |
| Java | 21 |
| Vault | Optional, recommended if your server already has an economy |

The plugin builds against `paper-api:1.21.11-R0.1-SNAPSHOT` and declares
`api-version: '1.21'`. It uses no NMS and no server internals, only the public API.

## Installing

```bash
mvn package
cp target/MultiverseGambling-1.0.0.jar ~/server/plugins/
```

Restart the server (or use a plugin manager that loads jars at runtime) and the plugin
writes its default files:

```
plugins/MultiverseGambling/
├── config.yml        ← every setting, in English and commented
├── lang/
│   ├── en.yml        ← English messages
│   └── es.yml        ← Spanish messages (plus the Spanish game catalogue)
├── balances.json     ← internal wallet (only when Vault is not used)
├── stats.json        ← statistics and rankings
├── fairness.json     ← social secret and client seeds
└── languages.json    ← the language each player picked
```

## Economy

`economy.provider` decides where money comes from:

| Value | Behaviour |
|---|---|
| `auto` (default) | Uses Vault when the server has it, otherwise the internal wallet |
| `vault` | Forces Vault; if it is missing the plugin warns and falls back to the internal wallet |
| `internal` | Always uses the plugin's own `balances.json` |

With the internal wallet, `economy.starting-balance` (1000 by default) is credited to every
new account and the welcome message is sent once. With Vault nothing of that happens: the
plugin never touches the balances, it asks Vault for them.

## The casino world

On first start the plugin creates the separate casino world (`mvgam_casino` by
default) and builds the plaza, the roads and one arena per game. Nothing is built in your
main world. See [Casino World](Casino-World).

## Updating from a version with `messages.yml`

Older builds kept every message in a single `messages.yml`. Modern builds read
`lang/<code>.yml` instead, and on the first start the old file is **migrated into
`lang/en.yml`** automatically, so customised texts are not lost. You can delete the old
`messages.yml` afterwards.

## Checking the installation

```
/mvgam info          → active economy, registered games and current secret
/mvgam world         → travel to the casino
/mvgam language      → the language you are reading
```

If the casino world is missing, the console says why: the world may be disabled, the layout
may not fit, or you pointed `world.name` at the main world (which the plugin refuses to
build in).
