# Mundo

[English](Casino-World) · **Español**

El plugin mantiene todas sus estructuras en un **mundo aparte**, así el mapa de juego queda
limpio y no hay que pegar nada a mano.

```
/mvgam world          → viajar al casino
/mvgam world build    → reconstruirlo entero en segundo plano (mvgam_admin)
/mvgam world info     → qué existe, qué está construido y qué falta (mvgam_admin)
```

## Qué crea

Por defecto un **mundo plano de 500 × 500 bloques** llamado `mvgam_casino`, con borde
centrado en el spawn y la hora fijada al atardecer para que farolas y pabellones brillen:

| Pieza | Detalles |
|---|---|
| **Plaza** | Plaza de mármol de radio 36: centro oscuro, anillos de oro, ocho radios, una banda de cuarzo ajedrezado, una fuente iluminada con un pilar de cuarzo, ocho farolas grandes, jardineras con flores y un seto de azalea florecida. Sobre la fuente gira una moneda de oro gigante bajo el nombre del plugin, y un cartel de bienvenida mira al spawn |
| **Pabellones** | Uno por juego registrado, de 41 × 41 bloques: un escenario oscuro con un anillo de oro en el centro, suelo de mármol blanco y negro, muros con vidrieras del color del pabellón, una puerta con arco de cuarzo y faroles colgantes en **cada** lado, cuatro torres en las esquinas, gradas de cuarzo a lo largo de los muros y bloques de luz invisibles sobre la plataforma. El nombre del juego flota encima con su icono girando debajo, y está escrito sobre cada puerta |
| **Bulevares** | 7 bloques de ancho, piedra oscura con bordillos blancos y línea central discontinua, uno por cada fila y columna de la rejilla más una ronda que rodea todo el casino. Farolas a ambos lados y avenidas de cerezos, abedules y robles |
| **Jardines** | Las casillas de la rejilla que no usa ningún juego se convierten en rotondas alrededor de un cerezo |
| **Afueras** | Un cinturón de árboles entre la ronda y el borde, un seto a lo largo del borde y hierba y flores en el césped |
| **Spawn** | En la plaza, mirando a la fuente y al cartel de bienvenida |

El mundo es plano con suelo de `minecraft:plains` (bedrock, dos capas de tierra, hierba) y la
generación de estructuras está desactivada, así que nada del mundo vanilla interfiere. En el
mundo del casino se desactivan los mobs, el clima, la propagación del fuego y las incursiones.

Si el mundo no se puede crear — carpeta sin permisos de escritura, nombre ocupado, un ajuste
que el servidor rechaza — el plugin lo avisa en consola y arranca igual: cada juego sigue
funcionando desde su menú, y `/mvgam world build` se puede reintentar cuando arregles la causa.
Ejecuta `/mvgam world info` para ver en cuál de esos estados está; también responde en consola,
que es donde se suele mirar cuando el arranque ha fallado.

## Cómo se construye

Todo el diseño se planifica primero y luego se escribe **unos pocos chunks por tick** (como
mucho unos 18 ms de cada tick), así el servidor sigue funcionando mientras el casino se levanta;
la primera construcción tarda unos segundos. Cada columna se compara con el diseño desde el suelo
hacia arriba y solo se cambian los bloques que difieren, lo que además borra lo que una versión
anterior hubiera dejado.

El mundo guarda una **firma del diseño** (su versión, el tamaño, los juegos y sus tableros).
Cuando el plugin se actualiza con un diseño nuevo, se añade un juego o un tablero cambia de
tamaño, el casino **se reconstruye solo** en el siguiente arranque. La reconstrucción limpia la
superficie del mundo del casino, así que guarda tus propias construcciones en otros mundos.

## Cómo se coloca todo

Los pabellones van en una rejilla cuadrada que se llena **de dentro hacia fuera**: los primeros
juegos registrados quedan más cerca de la plaza y los nuevos se extienden hacia fuera. La
casilla central se reserva para la plaza, así que una rejilla de `n` columnas aloja `n² − 1`
pabellones.

Con los valores por defecto (plaza de radio 36, pabellones de radio 20, separación 84, margen
16) la rejilla de 21 juegos ocupa **408 de los 500 bloques**, así que el mundo por defecto lo
contiene todo y deja un cinturón verde alrededor. Las tres casillas sobrantes son jardines.

Cada pabellón tiene una puerta en cada lado; la **puerta principal mira a la plaza** y los
espectáculos se orientan hacia ella, así quien llega caminando desde el spawn ve cada
espectáculo de frente.

## Configuración

```yaml
world:
  enabled: true
  name: 'mvgam_casino'
  size: 500
  build-structures: true
  teleport-on-join: false
  time: 13000
```

| Clave | Notas |
|---|---|
| `enabled` | Con `false` el plugin nunca crea el mundo y `/mvgam world` responde que está desactivado |
| `name` | Apúntalo a un mundo que ya tengas para reutilizarlo: el plugin construirá el casino ahí (y limpiará su superficie) |
| `size` | De 200 a 2000. Si la distribución no cabe, el mundo **crece de 50 en 50** (hasta 2000) en vez de fallar |
| `build-structures` | Con `false` el mundo se crea vacío y lo construyes tú |
| `teleport-on-join` | Manda a cada jugador al casino cuando entra al servidor |
| `time` | Hora a la que se congela el mundo: `6000` mediodía, `13000` atardecer, `18000` medianoche; `-1` mantiene el ciclo de día |

## Espectáculos en el mundo

Los pabellones no son decoración: cuando se juega una ronda y el mundo del casino está listo,
el juego **escenifica la ronda en su pabellón con display entities** (displays de bloque, de
ítem y de texto) en lugar de solo contar números en la barra de acción. Cada pieza en
movimiento se entrega al cliente con un tiempo de interpolación, así el movimiento es suave a
cualquier tasa de fotogramas, y las piezas están totalmente iluminadas, así un espectáculo se ve
igual a mediodía que a medianoche.

| Juego | Espectáculo |
|---|---|
| **Ruleta** | Una gran ruleta inclinada como una mesa, con sus números en las casillas, bombillas que se persiguen por el aro y una bola lanzada contra el giro que se acerca en espiral y cae en la casilla ganadora |
| **Rueda de la suerte** | Una rueda de la fortuna de pie, con los multiplicadores escritos, que frena casilla a casilla bajo un puntero dorado |
| **Ruleta de colores** (grupo) | La rueda real de 18 rojas, 18 negras y una verde, con su bola |
| **Jackpot** (grupo) | Una rueda de la fortuna con una porción por jugador, **del tamaño de su apuesta** como el propio sorteo, cada una con el nombre de su jugador |
| **Rifa** (grupo) | La misma rueda, repartida por boletos, que se detiene en el primer premio |
| **Tragamonedas** | Un gabinete rojo con marquesina iluminada: se tira de la palanca, tres tambores giran de verdad, frenan y se detienen de izquierda a derecha; una pareja o un trío iluminan la línea de pago |
| **Plinko** | Una pared de clavos de pie con el multiplicador escrito bajo cada cubeta; la bola salta de clavo en clavo siguiendo los rebotes reales e ilumina cada clavo que toca |
| **Crash** | Un cohete que despega de la esquina de una gráfica y sube por la curva del multiplicador, dejando una estela que pasa de verde a rojo, con el multiplicador contando en letras grandes; sale volando en dorado al retirarte y estalla en pedazos al reventar |
| **Dados** | Un marcador con una barra de 0 a 100 partida en la zona ganadora y la perdedora, un dado que rueda por ella y un contador que gira hasta parar en la tirada |
| **Póker de dados** (grupo) | Cinco dados lanzados sobre una mesa de fieltro que rebotan y se asientan uno a uno con sus puntos de cara a ti |
| **Carrera** (grupo) | Caballos de verdad con armadura teñida del color de su calle, galopando en una pista escalonada desde los cajones de salida hasta una meta a cuadros |
| **Ruleta rusa** (grupo) | Un revólver gigante: el tambor gira en cada disparo, se detiene bajo el percutor y o dispara con un fogonazo en el cañón o hace clic |
| **Bomba caliente** (grupo) | Una TNT enorme que late más deprisa a medida que se consume la mecha, con chispas recorriéndola y una TNT pequeña flotando sobre la cabeza de quien tiene la bomba |
| **Cara o cruz** | Una moneda lanzada desde un pedestal que da vueltas y vueltas y cae de canto mostrando la cara que salió |
| **Duelo** (grupo) | La misma moneda, con las cabezas de ambos jugadores a cada lado: el ganador se ilumina y el perdedor cae |
| **Blackjack** | Una mesa de cartas: las cartas vuelan desde el sabot hasta un tablero, la carta oculta se voltea cuando juega el crupier y un cartel anuncia el resultado |
| **Mayor o menor** | La misma mesa: arriba las cartas ya jugadas y abajo la actual, que se voltea en la siguiente |

El decorado **se monta al empezar la ronda, se deja un momento tras el resultado y después se
retira**, así un pabellón siempre vuelve a su escenario limpio: aunque un jugador se desconecte
o el servidor se pare a mitad de giro no queda nada (el plugin retira al apagarse cualquier
espectáculo en pie, y la siguiente construcción elimina lo que un cierre inesperado dejara). Solo
se escenifica una ronda de cada juego a la vez: si otro jugador gira el mismo juego mientras hay
un espectáculo en marcha, se queda con la animación de la barra de acción en lugar de pintar
encima. Los menús se siguen usando para todo lo que es una decisión y no un resultado (apostar,
elegir casilla, retirarse).

## Jugar en los bloques

Cuatro juegos no muestran un resultado: su ronda es una **secuencia de elecciones**, así que se
juegan sobre los bloques de su escenario. Pulsar el escenario de esos cuatro pabellones abre el
juego (el selector de apuesta o la sala de espera del tablero bomba) y desde ahí las casillas
son la entrada, igual que los botones del menú que sustituyen. Los muros, las gradas y las
puertas siguen siendo bloques normales.

| Juego | Tablero | Cómo se juega |
|---|---|---|
| **Minas** | Una rejilla de 5x5 casillas, una caja y un bloque rojo y otro verde para elegir el número de minas | Pulsa casillas para destaparlas; el bloque de oro cobra |
| **Torres** | Una fila de casillas por piso; los pisos ya subidos se vuelven verdes | Pulsa una casilla del piso iluminado; el bloque de oro cobra |
| **Rasca y gana** | Un cartón de 3x3 | Tres clics rascan tres casillas |
| **Tablero bomba** (grupo) | El tablero compartido de 9x4 | El turno pasa de jugador en jugador y el actual se coloca sobre el tablero |

El tablero en reposo forma parte de la construcción del mundo. Cada ronda devuelve sus casillas
exactamente al bloque que tenían, así un tablero nunca conserva los restos de una partida
terminada. El número de minas, la apuesta y todo lo que es una decisión siguen siendo menús;
los mismos interruptores de `world.animations` gobiernan los tableros, y con `enabled: false`
los cuatro juegos vuelven a sus menús.

```yaml
world:
  animations:
    enabled: true           # escenificar las rondas en los pabellones
    teleport-players: true  # llevar al jugador (o a toda la sala) al pabellón para verlo
    view-distance: 14       # bloques entre el centro del escenario y el espectador
```

| Clave | Notas |
|---|---|
| `enabled` | Con `false` cada juego mantiene su animación clásica en la barra de acción y los cuatro tableros de bloques vuelven a sus menús |
| `teleport-players` | Con `false` solo ven el espectáculo quienes ya están en el mundo del casino; un juego individual pone a su espectador justo delante, uno de grupo reparte a la sala en un arco delante del espectáculo |
| `view-distance` | A qué distancia del centro del escenario se colocan los espectadores |

Cada juego decide dónde mostrar su ronda y un espectáculo nunca decide dinero: el resultado ya
lo ha sacado el generador provably fair cuando se monta el decorado. Añadir un espectáculo a
otro juego es extender `ArenaShow`, dibujarlo en el marco local del escenario (el público en
`+z`) y llamarlo desde la ronda.

## Conviene saber

- **El mundo principal nunca se toca.** El plugin se niega a construir en el mundo principal del
  servidor y lo avisa en consola.
- **La reconstrucción va en segundo plano.** `/mvgam world build` responde al momento y te avisa
  cuando el casino está listo; mientras tanto `/mvgam world info` muestra que se está construyendo.
- **Los nombres flotantes siguen el idioma por defecto**, así un servidor en español tiene los
  nombres de los pabellones en español (`catalog.<id-del-juego>.name` con `language.default`);
  `/mvgam reload` los refresca.
- **Los juegos nuevos tienen pabellón gratis.** Registra un juego y el siguiente arranque lo
  coloca en la rejilla y reconstruye el casino sin que toques ninguna coordenada.
- **Es geometría con tests.** `CasinoLayout`, `StageFrame`, `CrashCurve`, `PlinkoBoard` y
  `WheelMath` son clases puras sin Bukkit, y la batería comprueba que 21 juegos caben en 500
  bloques, que los pabellones nunca se solapan ni tapan el spawn, que cada pabellón está sobre
  dos bulevares conectados a la plaza, que los espectáculos se orientan a la puerta principal sin
  quedar en espejo y que el cohete nunca sale de su gráfica.

## Volver a un casino hecho a mano

Si prefieres diseñar el casino tú mismo:

```yaml
world:
  enabled: false
```

No se crea ni se modifica nada, y `/mvgam world` solo avisa de que el mundo está desactivado
mientras `/mvgam play <id>` sigue funcionando desde cualquier sitio.
