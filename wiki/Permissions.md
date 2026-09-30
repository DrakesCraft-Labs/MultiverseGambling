# Permissions

**English** · [Español](Permisos-es)

Three permissions, nothing more.

| Permission | Default | Grants |
|---|---|---|
| `casino.play` | `true` (everyone) | Opening `/casino`, playing the public games, opening the menus, `/casino balance`, `/casino stats`, `/casino verify` and picking a language |
| `casino.top` | `true` (everyone) | `/casino top` |
| `casino.admin` | `op` | `/casino info`, `/casino reload`, `/casino world build`, `/casino give`, `/casino take`, `/casino set`, `/casino cancel` |

## Notes

- Permissions are the only gate: a game disabled in `config.yml` is not playable by anyone,
  and a player without `casino.play` is told so without seeing the menus.
- Nothing here grants money. `/casino give` needs `casino.admin`, and when Vault is in use
  the plugin calls Vault, so your economy plugin keeps its own limits and logs.
- The group games reserve a seat per player at bet time; a player without `casino.play`
  never enters the pot, so no money is taken.

## LuckPerms example

```
/lp group default permission set casino.play true
/lp group default permission set casino.top true
/lp group helper permission set casino.admin true
```
