# Permisos

[English](Permissions) · **Español**

Tres permisos y nada más.

| Permiso | Por defecto | Permite |
|---|---|---|
| `mvgam_play` | `true` (todos) | Abrir `/mvgam`, jugar a los juegos públicos, usar los menús, `/mvgam balance`, `/mvgam stats`, `/mvgam verify` y elegir idioma |
| `mvgam_top` | `true` (todos) | `/mvgam top` |
| `mvgam_admin` | `op` | `/mvgam info`, `/mvgam reload`, `/mvgam world build`, `/mvgam world info`, `/mvgam give`, `/mvgam take`, `/mvgam set` y `/mvgam cancel` |

## Notas

- Los permisos son la única puerta: un juego desactivado en `config.yml` no lo puede jugar
  nadie, y a quien no tiene `mvgam_play` se le dice sin mostrarle los menús.
- Nada de aquí regala dinero. `/mvgam give` necesita `mvgam_admin`, y cuando se usa Vault el
  plugin llama a Vault, así que tu plugin de economía mantiene sus límites y sus registros.
- Los juegos en grupo reservan asiento por jugador al apostar; quien no tiene `mvgam_play`
  nunca entra en el bote, así que no se le retira dinero.

## Ejemplo con LuckPerms

```
/lp group default permission set mvgam_play true
/lp group default permission set mvgam_top true
/lp group helper permission set mvgam_admin true
```
