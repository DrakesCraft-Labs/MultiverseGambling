# Permissions

**English** · [Español](Permisos-es)

Three permissions, nothing more.

| Permission | Default | Grants |
|---|---|---|
| `mvgam_play` | `true` (everyone) | Opening `/mvgam`, playing the public games, opening the menus, `/mvgam balance`, `/mvgam stats`, `/mvgam verify` and picking a language |
| `mvgam_top` | `true` (everyone) | `/mvgam top` |
| `mvgam_admin` | `op` | `/mvgam info`, `/mvgam reload`, `/mvgam world build`, `/mvgam world info`, `/mvgam give`, `/mvgam take`, `/mvgam set`, `/mvgam cancel` |

## Notes

- Permissions are the only gate: a game disabled in `config.yml` is not playable by anyone,
  and a player without `mvgam_play` is told so without seeing the menus.
- Nothing here grants money. `/mvgam give` needs `mvgam_admin`, and when Vault is in use
  the plugin calls Vault, so your economy plugin keeps its own limits and logs.
- The group games reserve a seat per player at bet time; a player without `mvgam_play`
  never enters the pot, so no money is taken.

## LuckPerms example

```
/lp group default permission set mvgam_play true
/lp group default permission set mvgam_top true
/lp group helper permission set mvgam_admin true
```
