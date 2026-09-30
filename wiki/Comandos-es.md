# Comandos

[English](Commands) · **Español**

El comando principal es `/casino`, con los alias **`/gambling`**, **`/mg`** y **`/bets`**.
Todo tiene autocompletado, y cada respuesta se escribe en el idioma de quien ejecutó el
comando.

## Jugar

| Comando | Qué hace |
|---|---|
| `/casino` | Abre el menú principal: pestaña de solitario y pestaña de grupo |
| `/casino menu` | Igual que el anterior |
| `/casino games [solo\|group]` | Lista el catálogo con los límites de apuesta de cada juego |
| `/casino play <juego>` | Juega por id o por nombre (`roulette`, `ruleta`, `slots`...) |
| `/casino action <accion>` | Punto de entrada de los **botones del chat** (`shoot`, `reveal`, `horse 3`, `accept`, `decline`) |

Dentro de cada juego hay menús propios: el selector de apuesta permite mitad, doblar o
apostarlo todo, y los paneles de juego tienen retirarse, plantarse, doblar o disparar.

## Tu cuenta

| Comando | Qué hace |
|---|---|
| `/casino balance [jugador]` | Muestra tu saldo, o el de otro |
| `/casino stats [jugador]` | Partidas, victorias, apostado, retorno real y juego favorito |
| `/casino top [profit\|wagered\|prize]` | Ranking por beneficio neto, volumen o mayor premio |

`/casino top` necesita `casino.top`, que todos tienen por defecto.

## Azar verificable

| Comando | Qué hace |
|---|---|
| `/casino verify` | Muestra el hash del secreto actual, el secreto anterior y tu semilla |
| `/casino verify <texto>` | Cambia tu semilla de cliente a ese texto |

La explicación completa está en [Azar verificable](Justicia-es).

## Mundo casino

| Comando | Qué hace |
|---|---|
| `/casino world` | Te lleva al mundo casino (avisa con claridad si no está listo) |
| `/casino world build` | Reconstruye la plaza, las carreteras y todas las arenas (**`casino.admin`**) |

## Idiomas

| Comando | Qué hace |
|---|---|
| `/casino language` | Muestra tu idioma y los disponibles |
| `/casino language <codigo>` | Cambia a ese idioma (`en`, `es`, `ES`, `es-AR`, `spanish`, `español`...) |
| `/casino language reset` | Olvida tu elección y vuelve a seguir tu cliente de Minecraft |

El alias `lang` también funciona: `/casino lang es`.

## Administración

| Comando | Qué hace |
|---|---|
| `/casino info` | Economía activa, proveedor, juegos registrados y secreto actual |
| `/casino reload` | Recarga `config.yml` y todos los `lang/*.yml` sin tocar las partidas |
| `/casino give <jugador> <cantidad>` | Añade saldo |
| `/casino take <jugador> <cantidad>` | Quita saldo |
| `/casino set <jugador> <cantidad>` | Fija el saldo a un valor exacto |
| `/casino cancel <jugador>` | Cierra la partida de alguien y devuelve su dinero |

Las cantidades aceptan coma o punto: `1000`, `1000.50`, `1000,50`.

Todos necesitan `casino.admin`, que los operadores tienen. Ver [Permisos](Permisos-es).

## Alias de subcomandos

| Alias | Subcomando real |
|---|---|
| `list` | `games` |
| `bet` | `play` |
| `ranking` | `top` |
| `fair` | `verify` |
| `lang` | `language` |

## Ejemplos

```
/casino play crash            → abrir crash y elegir apuesta
/casino play horse-race       → entrar en la siguiente ronda de carrera
/casino action shoot          → lo usa el botón de la ruleta rusa
/casino top prize             → ranking de mayores premios
/casino language es           → todo lo que leas pasa a español
/casino world build           → reconstruir el mundo casino desde cero
```
