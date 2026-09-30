# Economy

**English** · [Español](Economia-es)

The plugin can use your server economy through **Vault**, or its own wallet, decided by
`economy.provider`.

| Value | Behaviour |
|---|---|
| `auto` (default) | Vault when the server has it, otherwise the internal wallet |
| `vault` | Forces Vault. If it is missing the plugin warns and falls back to the internal wallet |
| `internal` | Always uses `balances.json`, even when Vault is installed |

## Internal wallet

`balances.json` holds `uuid → balance`. The welcome balance comes from
`economy.starting-balance` (1000 by default) and is credited the first time a player is seen,
with a message. Balances are saved every `data.save-every-minutes` minutes and on shutdown.

Administration commands:

```
/mvgam give <player> <amount>
/mvgam take <player> <amount>
/mvgam set  <player> <amount>
```

They work with either provider. When Vault is active the plugin calls Vault, so your economy
plugin keeps its own limits, logs and hooks.

## Amounts and formatting

`economy.format` decides how money is printed everywhere (`&6{amount} &7{currency}` by
default): messages, menus, signs and the balance commands. Amounts typed in commands accept a
comma or a dot, so both `1000.50` and `1000,50` work.

## Data files

| File | Contents | Safe to delete? |
|---|---|---|
| `balances.json` | Internal wallet; ignored when Vault is used | Only to reset the internal economy |
| `stats.json` | Games played, wins, wagered, biggest win and the rankings | Yes, players just lose their statistics |
| `fairness.json` | Current server secret, previous secret and the per player client seeds | Yes, but every player's seed changes |
| `languages.json` | The language each player chose | Yes, everyone falls back to `language.default` |

## How the plugin protects money

- **One settlement per bet.** A `Wager` is created when the stake is taken and can be settled
  once: a second `pay()`, `refund()` or `lose()` does nothing. Paying the same round twice is
  not possible, not just unlikely.
- **The pot owns the money.** In group games the stakes live in a `Pot` from the betting window
  until the payout, so a disconnect cannot leave money half way: it either comes back with the
  refund or stays winnable by the others.
- **One clock.** `SessionManager` schedules everything, and `shutdownAll()` settles every open
  game on shutdown, so a restart does not eat anybody's stake.
- **The house edge is data, not code.** `game.house-edge` is applied by the engine tables and
  the suite refuses to build a table that returns more than it takes.
