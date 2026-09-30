# Permisos

[English](Permissions) · **Español**

Tres permisos y nada más.

| Permiso | Por defecto | Permite |
|---|---|---|
| `casino.play` | `true` (todos) | Abrir `/casino`, jugar a los juegos públicos, usar los menús, `/casino balance`, `/casino stats`, `/casino verify` y elegir idioma |
| `casino.top` | `true` (todos) | `/casino top` |
| `casino.admin` | `op` | `/casino info`, `/casino reload`, `/casino world build`, `/casino give`, `/casino take`, `/casino set` y `/casino cancel` |

## Notas

- Los permisos son la única puerta: un juego desactivado en `config.yml` no lo puede jugar
  nadie, y a quien no tiene `casino.play` se le dice sin mostrarle los menús.
- Nada de aquí regala dinero. `/casino give` necesita `casino.admin`, y cuando se usa Vault el
  plugin llama a Vault, así que tu plugin de economía mantiene sus límites y sus registros.
- Los juegos en grupo reservan asiento por jugador al apostar; quien no tiene `casino.play`
  nunca entra en el bote, así que no se le retira dinero.

## Ejemplo con LuckPerms

```
/lp group default permission set casino.play true
/lp group default permission set casino.top true
/lp group helper permission set casino.admin true
```
