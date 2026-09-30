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

## Animaciones en el mundo

Las arenas no son decorado: cuando se juega una ronda y el mundo casino está listo, el juego
pinta el resultado en su propia arena en vez de limitarse a contar números en la barra de
acción.

| Juego | Animación |
|---|---|
| **Ruleta** | Una mesa redonda de casillas de colores dentro de un aro dorado, con una bolita que gira y se para en el número ganador |
| **Ruleta de la suerte** | La misma mesa, con un sector por casilla: gris no paga nada, el oro es el premio máximo |
| **Ruleta de colores** (grupo) | La rueda real de 18 casillas rojas, 18 negras y una verde; la bolita cae en el color que ha salido |
| **Bote** (grupo) | Una rueda de boletos, un color por jugador, que se para en el boleto que se lleva el bote |
| **Rifa** (grupo) | El mismo tambor de boletos, parándose en el primer premio |
| **Tragaperras** | Un mueble de tres rodillos que pasan por la tabla de símbolos y se paran de izquierda a derecha, dejando la combinación en la línea de pago |
| **Plinko** | Una pirámide de clavijas en el suelo donde la bola da los rebotes reales y se queda iluminado el cubo donde cae |
| **Crash** | Una torre que sube un bloque por cada doblada del multiplicador: dorada si te retiras, calcinada si estalla |
| **Dados** | Una recta de 0 a 100 con el objetivo en rojo y un marcador que sube hasta la tirada, dejando el tramo recorrido en verde o en rojo |
| **Póker de dados** (grupo) | La mano ganadora como cinco dados con sus puntos en relieve, parándose uno a uno |
| **Carrera** (grupo) | Una calle por caballo corriendo hacia el fondo, hacia una meta dorada |
| **Ruleta rusa** (grupo) | El tambor del revólver dibujado en el suelo, con las recámaras cargadas en rojo, girando en cada disparo |
| **Bomba caliente** (grupo) | Un bloque de TNT sobre un podio con una mecha que se acorta con el tiempo sorteado y una marca de quemadura al estallar |
| **Cara o cruz** | Una moneda de oro girando sobre la arena, que aterriza en la cara que ha salido y la pavimenta debajo |

El decorado se **monta al empezar la ronda, se queda un segundo tras el resultado y se
retira después**, así que las arenas siempre vuelven a su plataforma limpia: ni un jugador
desconectándose ni el servidor parándose a mitad de giro dejan bloques sueltos ni
entidades flotando (el plugin limpia al apagarse cualquier animación que siga en pie). Los
menús siguen usándose para todo lo que es una decisión y no un resultado (apostar, elegir
casilla, retirarse).

## Jugar sobre los bloques

Cuatro juegos no muestran un resultado: su ronda es una **secuencia de elecciones**, así
que se juegan en los bloques de su arena. Pulsar cualquier bloque de esas cuatro arenas
abre el juego (el selector de apuesta, o la sala de espera del tablero de bombas) y a
partir de ahí las casillas son la entrada, igual que los botones del menú a los que
sustituyen.

| Juego | Tablero | Cómo se juega |
|---|---|---|
| **Minas** | Una rejilla de 5x5, un cajero y un bloque rojo y otro verde para elegir cuántas minas hay | Pulsa casillas para destaparlas; el bloque dorado cobra |
| **Torres** | Una fila de casillas por piso, con los pisos ya subidos en verde | Pulsa una casilla del piso encendido; el bloque dorado cobra |
| **Rasca y Gana** | Una tarjeta de 3x3 | Tres clics rascan tres casillas |
| **Tablero de Bombas** (grupo) | El tablero compartido de 9x4 | El turno pasa de jugador en jugador y al que le toca se le coloca sobre el tablero |

El tablero en reposo forma parte de la construcción del mundo: ejecuta `/mvgam world build`
una vez tras actualizar para que cada arena muestre su tablero. Cada ronda devuelve sus
casillas exactamente a los datos de bloque que tenían, así que un tablero nunca guarda
restos de una partida. El número de minas, la apuesta y todo lo demás
que es una decisión siguen siendo menús; los mismos interruptores de `world.animations`
gobiernan los tableros, y con `enabled: false` los cuatro juegos vuelven a sus menús. El
blackjack y el alto-bajo se quedan en sus menús de cartas: su ronda tampoco es un tablero.

```yaml
world:
  animations:
    enabled: true           # pintar los resultados en la arena
    teleport-players: true  # llevar al jugador (o a toda la sala) a la arena para verlo
    view-distance: 11       # bloques entre el centro de la arena y el espectador
```

| Clave | Notas |
|---|---|
| `enabled` | Con `false` cada juego conserva su animación clásica en la barra de acción y los cuatro tableros vuelven a sus menús |
| `teleport-players` | Con `false` solo ven la animación quienes ya estén en el mundo casino; los juegos individuales colocan a un espectador frente a la mesa y los de grupo reparten la sala en círculo alrededor |
| `view-distance` | A cuántos bloques del centro se coloca el espectador, mirando hacia la mesa |

Cada juego decide dónde mostrar su ronda, y una animación nunca decide dinero: el resultado
ya lo ha sorteado el generador provablemente justo cuando se monta el decorado. Añadir una
animación a otro juego es extender `ArenaShow` y llamarlo desde la ronda.

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
mientras `/mvgam play <id>` sigue funcionando desde cualquier sitio.
