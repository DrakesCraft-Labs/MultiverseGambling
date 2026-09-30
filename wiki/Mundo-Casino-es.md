# Mundo

[English](Casino-World) · **Español**

El plugin puede mantener todas sus estructuras en un **mundo aparte**, así el mapa de juego
queda limpio y no hay que pegar nada a mano.

```
/mvgam world          → viajar al casino
/mvgam world build    → reconstruirlo entero (mvgam_admin)
```

## Qué crea

Por defecto un **mundo plano de 500 × 500 bloques** llamado `mvgam_casino`, con borde
centrado en el spawn:

| Pieza | Detalles |
|---|---|
| **Plaza** | Disco pavimentado de radio 30 alrededor del spawn: deepslate pulido, cuarzo liso y centro de oro, bordillo de piedra negra pulida, monumento de oro con linterna marina, cuatro farolas en diagonal y cartel de bienvenida |
| **Arenas** | Una por juego registrado, radio 12: borde de piedra negra pulida, medallón de oro, paleta de 15 colores, valla de roble con una única abertura de 3 bloques, cuatro farolas en las esquinas y un cartel con el nombre del juego |
| **Carreteras** | 3 bloques de ancho, diorita pulida, uniendo la plaza con cada arena |
| **Spawn** | Delante de la plaza, mirando al monumento |

El mundo es plano con suelo de `minecraft:plains` (bedrock, dos capas de tierra, hierba) y la
generación de estructuras está desactivada, así que nada del mundo vanilla interfiere.

## Cómo se coloca todo

Las arenas van en una rejilla cuadrada que se llena **de dentro hacia fuera**: los primeros
juegos registrados quedan más cerca de la plaza y los nuevos se extienden hacia fuera. La
casilla central se reserva para la plaza, así que una rejilla de `n` columnas aloja `n² − 1`
arenas.

Con los valores por defecto (plaza radio 30, arena radio 12, separación 110, margen 8) la
rejilla de 21 juegos necesita **480 de los 500 bloques**, así que el mundo por defecto aloja
todo con margen de sobra.

La entrada de cada arena mira siempre a la plaza: es el lado por el que llega la carretera, así
que quien camina por ella ve el cartel del juego antes de entrar.

## Configuración

```yaml
world:
  enabled: true
  name: 'mvgam_casino'
  size: 500
  build-structures: true
  teleport-on-join: false
```

| Clave | Notas |
|---|---|
| `enabled` | Con `false` el plugin nunca crea el mundo y `/mvgam world` responde que está desactivado |
| `name` | Apúntalo a un mundo que ya tengas para reutilizarlo: el plugin construirá allí la rejilla de arenas |
| `size` | De 200 a 2000. Si la distribución no cabe, el mundo **se agranda en pasos de 50** (hasta 2000) en vez de fallar |
| `build-structures` | Con `false` el mundo se crea vacío y lo construyes tú |
| `teleport-on-join` | Manda a cada jugador al casino cuando entra al servidor |

## Cosas que conviene saber

- **El mundo principal no se toca nunca.** El plugin se niega a construir las estructuras en el
  mundo principal y lo avisa por consola.
- **Reconstruir es seguro.** `/mvgam world build` limpia el mundo casino y lo vuelve a
  levantar; los jugadores que estén dentro aparecen en el spawn.
- **El texto de los carteles sigue el idioma por defecto**, así que un servidor en español tiene
  las arenas rotuladas en español (`catalog.<id-juego>.name` con `language.default`).
- **Los juegos nuevos reciben arena gratis.** Registra un juego y la siguiente construcción lo
  coloca en la rejilla sin que toques ninguna coordenada.
- **Es geometría testeable.** `CasinoLayout` es una clase pura sin Bukkit, y la suite comprueba
  que 21 juegos caben en 500 bloques, que las arenas no se solapan ni tapan el spawn, que las
  entradas miran a la plaza y que un mundo demasiado pequeño se rechaza.

## Si prefieres un casino hecho a mano

```yaml
world:
  enabled: false
```

No se crea ni se modifica nada, y `/mvgam world` solo avisa de que el mundo está desactivado
mientras `/mvgam play <juego>` sigue funcionando desde cualquier sitio.
