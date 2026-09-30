# Economía

[English](Economy) · **Español**

El plugin puede usar la economía de tu servidor a través de **Vault**, o su propio monedero,
según `economy.provider`.

| Valor | Comportamiento |
|---|---|
| `auto` (por defecto) | Vault si el servidor lo tiene, si no el monedero interno |
| `vault` | Fuerza Vault. Si falta, avisa y cae al monedero interno |
| `internal` | Usa siempre `balances.json`, aunque Vault esté instalado |

## Monedero interno

`balances.json` guarda `uuid → saldo`. El saldo de bienvenida sale de
`economy.starting-balance` (1000 por defecto) y se abona la primera vez que se ve a un jugador,
con su mensaje. Los saldos se guardan cada `data.save-every-minutes` minutos y al apagar.

Comandos de administración:

```
/mvgam give <jugador> <cantidad>
/mvgam take <jugador> <cantidad>
/mvgam set  <jugador> <cantidad>
```

Funcionan con cualquiera de los dos proveedores. Cuando Vault está activo el plugin llama a
Vault, así que tu plugin de economía mantiene sus límites, sus registros y sus hooks.

## Cantidades y formato

`economy.format` decide cómo se imprime el dinero en todas partes
(`&6{amount} &7{currency}` por defecto): mensajes, menús, carteles y comandos de saldo. Las
cantidades escritas en los comandos aceptan coma o punto, así que funcionan `1000.50` y
`1000,50`.

## Archivos de datos

| Archivo | Contenido | ¿Se puede borrar? |
|---|---|---|
| `balances.json` | Monedero interno; se ignora con Vault | Solo para reiniciar la economía interna |
| `stats.json` | Partidas, victorias, apostado, mayor premio y los rankings | Sí, los jugadores pierden sus estadísticas |
| `fairness.json` | Secreto actual, secreto anterior y semillas de cliente por jugador | Sí, pero cambia la semilla de todos |
| `languages.json` | El idioma que eligió cada jugador | Sí, todos caen a `language.default` |

## Cómo protege el dinero el plugin

- **Una liquidación por apuesta.** Un `Wager` se crea cuando se cobra la apuesta y se puede
  liquidar una sola vez: un segundo `pay()`, `refund()` o `lose()` no hace nada. Pagar dos veces
  la misma ronda no es posible, no solo improbable.
- **El bote es dueño del dinero.** En los juegos en grupo las apuestas viven en un `Pot` desde la
  ventana de apuestas hasta el reparto, así que una desconexión no puede dejar dinero a medias:
  o vuelve con la devolución o sigue siendo ganable por los demás.
- **Un solo reloj.** `SessionManager` planifica todo y `shutdownAll()` liquida cada partida
  abierta al apagar, así que un reinicio no se come la apuesta de nadie.
- **La ventaja es dato, no código.** `game.house-edge` lo aplican las tablas del `engine` y la
  suite se niega a compilar una tabla que devuelva más de lo que cobra.
