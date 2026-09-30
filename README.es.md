# MultiverseGambling

[English](README.md) · **Español** · [Wiki](wiki/Home-es.md)

Motor de azar y apuestas para **Paper 1.21.11** con **21 minijuegos**: 12 en solitario
contra la casa y 9 en grupo con rondas automáticas, todos dentro de un **mundo casino**
propio y jugables en **inglés o español**.

No es una colección de comandos suelta: es un motor donde cada moneda pasa por el mismo
sitio, cada tirada de dinero sale de un **azar verificable** y cada tabla de premios está
**fijada por tests**.

```
mvn package      →  target/MultiverseGambling-1.0.0.jar
```

---

## Índice

- [Por qué este diseño](#por-qué-este-diseño)
- [Instalación](#instalación)
- [El mundo casino](#el-mundo-casino)
- [Idiomas](#idiomas)
- [El catálogo](#el-catálogo)
- [Retornos reales](#retornos-reales)
- [Azar verificable](#azar-verificable)
- [Comandos](#comandos)
- [Permisos](#permisos)
- [Configuración](#configuración)
- [Arquitectura](#arquitectura)
- [Añadir un juego nuevo](#añadir-un-juego-nuevo)
- [Qué cubren los tests](#qué-cubren-los-tests)

---

## Por qué este diseño

Un casino de servidor se rompe siempre de la misma forma: una tabla de premios que paga de
más, un pago que se aplica dos veces, o una tirada que un jugador puede predecir.
MultiverseGambling está construido para hacer esos tres fallos **imposibles por
construcción**, no por cuidado al escribir el código.

1. **La matemática del azar vive en `com.chagui68.multiversegambling.engine`, sin Bukkit.**
   Son clases de Java puro (ruleta, minas, crash, plinko, blackjack, póker de dados,
   tragaperras, rasca, rueda de premios, azar verificable). Se testean en milisegundos y el
   plugin solo puede *usar* esas fórmulas, nunca reinterpretarlas.

2. **Todo el dinero pasa por `EconomyManager` y `Wager`.** Una apuesta se retira del
   monedero al empezar y queda envuelta en un objeto que **solo se puede liquidar una vez**.
   Pagar dos veces la misma jugada deja de ser posible, no improbable.

3. **El azar que decide dinero es siempre `FairnessService`.** El `Rng` normal solo se usa
   para pintar animaciones. Cualquiera puede reproducir una tirada después.

Los tests ya han cazado bugs reales durante el desarrollo: el generador verificable devolvía
la mitad de las tiradas en negativo, el Plinko pagaba 9 veces de más por olvidar el divisor
de cubos, la ruleta calculaba mal su ventaja, cara o cruz no tenía ventaja ninguna y la
ruleta "clásica" ofrecía apostar al verde a 2x cuando eso es un timo. Todos están cubiertos
por un test que impide que vuelvan.

---

## Instalación

**Requisitos:** Paper 1.21.11, Java 21. Vault es opcional pero recomendado.

El plugin se compila contra `paper-api:1.21.11-R0.1-SNAPSHOT` y declara `api-version:
'1.21'`, así que también carga en cualquier servidor de la serie 1.21.x. No depende de NMS
ni de ningún módulo interno: solo API pública.

```bash
mvn package
cp target/MultiverseGambling-1.0.0.jar ~/servidor/plugins/
```

Sin Vault el plugin arranca su propio monedero en
`plugins/MultiverseGambling/balances.json`, con saldo de bienvenida configurable. Con Vault
usa la economía del servidor y no duplica nada. Se controla con
`economy.provider: auto | sbank | vault | internal`.

El puente es deliberadamente agnóstico. Además de la economía Vault de siempre, el casino
habla con las **cuentas bancarias de sBank**: en un servidor que guarda el dinero de los
jugadores en el banco, las apuestas y los premios mueven ese saldo, con el mismo redondeo a
dos decimales y el mismo registro de auditoría que escribe el propio banco. `auto` prueba los
motores en el orden de `economy.auto-order` (banco, luego Vault, luego el monedero interno) y
siempre termina en un monedero, así que el plugin nunca puede fallar al arrancar por culpa
del dinero.

Archivos de datos que crea:

| Archivo | Contenido |
|---|---|
| `balances.json` | Monedero interno (solo sin Vault) |
| `stats.json` | Estadísticas y rankings por jugador |
| `fairness.json` | Semillas de cliente y secreto del servidor para la auditoría |
| `languages.json` | El idioma que eligió cada jugador |

---

## El mundo casino

El plugin puede crear y mantener un **mundo aparte** que contiene todas las estructuras, así
no hay que pegar nada a mano y el mundo de juego queda limpio.

```
/mvgam world          → te lleva al casino
/mvgam world build    → reconstruye la plaza, las carreteras y todas las arenas (admin)
```

Por defecto crea un **mundo plano de 500 × 500 bloques** llamado `mvgam_casino` con
borde centrado, y en el primer uso levanta:

- una **plaza central** (radio 30) como disco pavimentado con bordillo, monumento de oro,
  cuatro farolas y un cartel de bienvenida, donde está el punto de aparición;
- **una arena por juego** (radio 12), en una rejilla cuadrada que se llena de dentro hacia
  fuera: los juegos más jugados quedan cerca de la plaza y los nuevos se extienden hacia
  fuera;
- **carreteras de tres bloques de ancho** que unen la plaza con cada arena;
- cada arena lleva su propia paleta de colores, una valla con una única abertura de entrada
  que siempre mira a la plaza, cuatro farolas en las esquinas y un cartel con el nombre del
  juego.

Todo se controla desde `world:` en [config.yml](src/main/resources/config.yml):

```yaml
world:
  enabled: true            # crear/cargar el mundo al arrancar
  name: 'mvgam_casino'
  size: 500                # lado del cuadrado, en bloques (200-2000)
  build-structures: true   # plaza, arenas y carreteras en el primer uso
  teleport-on-join: false  # mandar aquí a cada jugador al entrar
```

Cosas que conviene saber:

- La **geometría es una clase pura** (`CasinoLayout`) sin Bukkit, así que está testeada: la
  suite comprueba que los 21 juegos caben en 500 bloques, que ninguna arena se solapa, que
  nada tapa el spawn y que la entrada de cada arena mira a la plaza.
- Si `world.size` es pequeño para la rejilla, el plugin **agranda el mundo** en pasos de 50
  bloques (hasta 2000) en vez de fallar.
- Apunta `world.name` a un mundo existente para reutilizarlo, o pon `enabled: false` y
  construye el casino a mano: `/mvgam world` te dirá entonces que el mundo está desactivado.
- El plugin se niega a construir las estructuras en el mundo principal del servidor, así que
  nunca pisa el spawn de un mapa de supervivencia.
- El texto de los carteles usa el catálogo del idioma por defecto, así que un servidor en
  español tiene las arenas rotuladas en español.

La referencia completa está en la página [Mundo](wiki/Mundo-Casino-es.md) de la wiki.

---

## Idiomas

El plugin **se traduce a sí mismo dentro del juego**. Cada jugador elige lo que lee, y los
comandos de administración contestan en el idioma de quien los ejecutó.

```
/mvgam language es      → este jugador pasa a leer español
/mvgam language en      → vuelve al inglés
/mvgam language         → muestra el idioma actual y los disponibles
/mvgam language reset   → seguir otra vez el idioma del cliente de Minecraft
```

Cómo funciona:

- Cada idioma es un archivo en `plugins/MultiverseGambling/lang/`, un código por archivo:
  `en.yml`, `es.yml`. Los dos se escriben en el primer arranque, y un `messages.yml` de una
  versión anterior se migra solo a `lang/en.yml`.
- La elección se guarda **por jugador** en `languages.json`, así que sobrevive a los
  reinicios, y el orden de búsqueda es: idioma elegido → idioma del cliente de Minecraft
  (si `language.follow-client` es `true`) → `language.default` → inglés.
- La consola y los carteles del mundo usan `language.default`.
- Añadir un idioma es copiar un archivo: copia `lang/en.yml` a `lang/<codigo>.yml`,
  tradúcelo, y el código aparece al instante en `/mvgam language`. Los códigos que no
  existen se rechazan mostrando la lista de disponibles.
- Se acepta entrada libre: `es`, `ES`, `es_es`, `es-AR`, `spanish` y `español` significan
  todos español.
- Una clave que falte se dibuja como `&c[missing message: <clave>]` en vez de romper, así que
  una traducción parcial degrada con elegancia.
- Los **nombres y descripciones** de los juegos están en inglés en el código y se pueden
  sobrescribir por idioma con un bloque `catalog.<id-juego>.name` /
  `catalog.<id-juego>.description`; `lang/es.yml` incluye todo el catálogo español como
  ejemplo.
- Los **paneles de cada juego** — títulos de ventana, botones, descripciones de objetos y
  las líneas que esos menús escriben en el chat — viven en `panel.*`, una sección por juego,
  así que el interior de un minijuego también se lee en el idioma del jugador. Un test hace
  fallar la compilación si `lang/en.yml` y `lang/es.yml` se desvían en claves o placeholders.

```yaml
language:
  default: en          # consola, jugadores sin preferencia, carteles del mundo
  follow-client: true  # quien nunca eligió sigue el idioma de su cliente
```

Los detalles, incluido cómo añadir un tercer idioma, están en la página
[Idiomas](wiki/Idiomas-es.md) de la wiki.

---

## El catálogo

### En solitario (12)

| Juego | Id | Reglas | Paga |
|---|---|---|---|
| **Ruleta Clásica** | `roulette` | Rojo, negro, par/impar, 1-18/19-36, docenas, columnas o número exacto. El 0 es verde y **solo** paga a la apuesta directa, como en la ruleta real. | 2x / 3x / 36x |
| **Tragamonedas** | `slots` | 3 rodillos, 7 símbolos ponderados. Tres iguales pagan la tabla; las cerezas pagan algo con dos. | hasta 600x |
| **Crash** | `crash` | La curva sube sola y hay que retirarse antes de que estalle. El punto de explosión sale de una sola tirada verificable. | 1.00x en adelante |
| **Minas** | `mines` | Rejilla de 25 casillas con 1 a 24 minas. Cada acierto sube el multiplicador; te retiras cuando quieras. | crece con la dificultad |
| **La Torre** | `towers` | 9 pisos, 4 casillas y 1 bomba por piso. Elige casilla segura para subir, retírate antes de caer. | crece por piso |
| **Blackjack** | `blackjack` | Baraja de 6 mazos. Natural 3:2, empate devuelve apuesta, se puede doblar. El crupier puede pedir con 17 blando (configurable). | hasta 2.5x |
| **Mayor o Menor** | `high-low` | Adivina si la siguiente carta es mayor o menor y encadena aciertos. El pago de cada paso sale de los rangos que quedan de verdad. | encadenable |
| **Dados** | `dice` | Objetivo del 0.01 al 99.99 con apuesta por encima o por debajo. Pago justo recortado. | hasta ~99x |
| **Plinko** | `plinko` | La bolita cae por la pirámide. Los cubos salen de la distribución binomial real, no de una tabla inventada. | hasta cientos de x |
| **Rasca y Gana** | `scratch` | Destapa 3 de 9 casillas. Tres iguales pagan el premio del símbolo, dos devuelven parte. | hasta 50x |
| **Ruleta de la Suerte** | `lucky-wheel` | 12 casillas igual de probables, la mayoría sin premio y un par de golpes grandes. | hasta 4x |
| **Cara o Cruz** | `coin-flip` | Elige cara o cruz. Pago justo recortado (no 2x fijo: eso no daría ventaja a la casa). | ~1.96x |

### En grupo (9)

Todos funcionan por rondas automáticas: entra quien quiere, apuesta durante la ventana, la
ronda se juega sola y la siguiente arranca sin que nadie lance comandos.

| Juego | Id | Reglas | Jugadores |
|---|---|---|---|
| **Ruleta de Colores** | `color-roulette` | Cada uno apuesta a rojo, negro o verde. El verde es una casilla única, así que paga ~36x con la misma ventaja que el rojo. | 2-24 |
| **Bote Común** | `jackpot` | Todos ponen dinero y las papeletas son proporcionales a lo apostado. Un ganador se lo lleva todo. | 2-24 |
| **Bomba Caliente** | `hot-bomb` | La TNT pasa de mano en mano con una mecha sorteada. Al que le pille, fuera y su dinero al bote. | 2-12 |
| **Tablero de Bombas** | `bomb-board` | Tablero compartido con bombas escondidas; por turnos cada uno destapa. El último en pie cobra. | 2-12 |
| **Ruleta Rusa** | `russian-roulette` | Por turnos, cada uno aprieta el gatillo con 1 bala en 6 recámaras. El superviviente se lleva el bote. | 2-8 |
| **Carrera de Caballos** | `race` | 8 caballos con probabilidades publicadas. Elegir el favorito paga poco; el tapado, mucho. | 2-24 |
| **Duelo 1v1** | `duel` | Retas a alguien por una cantidad; los dos ponen lo mismo y una moneda decide. Si no acepta a tiempo, recuperas tu dinero. | 2 |
| **Rifa** | `raffle` | Boletas a precio fijo y sorteo de **tres premios**: 70%, 20% y 10% del bote. | 2-24 |
| **Póker de Dados** | `dice-poker` | Cinco dados cada uno; gana la mejor mano. Los empates reparten el bote. | 2-16 |

Los ids son estables y también aceptan el nombre traducido, así que `/mvgam play ruleta`
funciona en un servidor en español y `/mvgam play roulette` en uno en inglés.

---

## Retornos reales

Estos números no están estimados: salen de las fórmulas y los fija la suite de tests.
`mvn test` los vuelve a comprobar en cada compilación.

| Juego | Retorno | Nota |
|---|---|---|
| Ruleta Clásica (europea) | **97.30%** | Todas las apuestas comparten retorno: 36/37 |
| Ruleta Clásica (americana) | **94.74%** | El 00 duplica la ventaja |
| Ruleta de Colores | **97.30%** | Igual en rojo, negro y verde |
| Tragamonedas | **94.75%** | Tabla calibrada a mano y verificada |
| Crash | **98.00%** | El mismo valor esperado en cualquier objetivo |
| Minas / La Torre | **98.00%** | El multiplicador es la inversa exacta de la probabilidad |
| Mayor o Menor | **~98.15%** | Por paso, contando los empates |
| Dados / Plinko | **98.00%** | Plinko baja algo más por el tope de 500x |
| Cara o Cruz | **98.00%** | Pago justo recortado |
| Rasca y Gana | **92.15%** | La tarjeta más generosa en premios pequeños |
| Ruleta de la Suerte | **95.00%** | Es la media de sus 12 casillas |
| Carrera de Caballos | **98.00%** | Cada caballo por separado |
| Bote, Rifa, Póker, Duelo, Bombas, Bomba Caliente, Ruleta Rusa | **100% − comisión** | Jugador contra jugador: por defecto sin comisión |

Todos los juegos contra la casa usan `game.house-edge` (2% por defecto) salvo aquellos cuya
ventaja está fijada por la propia rueda, como la ruleta.

---

## Azar verificable

Al arrancar, el plugin genera un secreto y **publica su hash**:

```
/mvgam verify
```

Cada tirada se deriva de `HMAC-SHA256(secreto, semillaDelJugador:nonce:cursor)`. Cuando el
secreto rota, se revela el anterior y cualquiera puede recalcular las tiradas para comprobar
que la casa no las retocó. La semilla de cliente es tuya y se puede cambiar con
`/mvgam verify <texto>`; mezclarla con el secreto del servidor es lo que impide que el
servidor pueda elegir el resultado después de conocerte.

Las tiradas de las partidas en grupo se atribuyen a la identidad fija del casino (UUID
cero), así que una ronda de bomba caliente o una carrera se puede auditar entera, paso a
paso.

---

## Comandos

| Comando | Qué hace |
|---|---|
| `/mvgam` | Abre el menú principal con las dos pestañas |
| `/mvgam games [solo\|group]` | Lista el catálogo con sus límites de apuesta |
| `/mvgam play <juego>` | Juega a un juego por su id o su nombre |
| `/mvgam action <accion>` | Punto de entrada de los **botones del chat** (shoot, horse 3, accept...) |
| `/mvgam balance [jugador]` | Consulta el saldo |
| `/mvgam stats [jugador]` | Estadísticas: partidas, retorno real, juego favorito |
| `/mvgam top [profit\|wagered\|prize]` | Ranking del servidor |
| `/mvgam verify [semilla]` | Auditoría del azar y cambio de semilla |
| `/mvgam world [build]` | Te lleva al mundo casino, o lo reconstruye |
| `/mvgam language [codigo\|reset]` | Cambia el idioma que lee este jugador |
| `/mvgam info` | Economía activa, juegos y secreto actual |
| `/mvgam give \| take \| set` | Administración de saldo |
| `/mvgam cancel <jugador>` | Cierra la partida de alguien y devuelve su dinero |
| `/mvgam reload` | Recarga config y mensajes sin tocar las partidas en curso |

El comando es `/mvgam` y **no tiene alias**: cada subcomando tiene una sola forma, así que el
autocompletado y los mensajes nunca pueden discrepar.

Los permisos, las etiquetas y el mundo que crea llevan el prefijo `mvgam_`, así que un
servidor con otros plugins nunca mezcla un identificador: `mvgam_play`, `mvgam_top`,
`mvgam_admin` y el mundo `mvgam_casino`.

## Permisos

| Permiso | Por defecto | Permite |
|---|---|---|
| `mvgam_play` | todos | Entrar al casino, jugar a los juegos públicos y elegir idioma |
| `mvgam_top` | todos | Ver el ranking del servidor |
| `mvgam_admin` | operadores | Recargar, construir el mundo casino, dar/quitar saldo y cancelar partidas |

---

## Configuración

Todo está en `plugins/MultiverseGambling/config.yml`, en inglés y comentado: economía
(`provider`, `currency`, `format`, `starting-balance`), ventaja de la casa y límites de
apuesta, fairness, el intervalo de guardado, `language`, `world`, los valores compartidos de
`group` y una entrada por juego con sus opciones. La página
[Configuración](wiki/Configuracion-es.md)
de la wiki tiene todas las claves; y antes de tocar una tabla de premios lee la cabecera del
archivo: el retorno de tragaperras, plinko, rasca y ruleta de la suerte está fijado por
tests, así que ejecuta `mvn test` tras cambiar pesos o pagos.

---

## Arquitectura

```
com.chagui68.multiversegambling
├── engine/        ← Java puro, sin Bukkit, cubierto por tests
│   ├── ProvablyFair, Rng, WeightedTable
│   ├── RouletteTable, ColorWheel, PrizeWheel, SlotsTable, ScratchCardTable
│   ├── MinesTable, CrashTable, DiceTable, PlinkoTable
│   ├── Card.Deck, BlackjackHand, DicePoker, HorseOdds
├── economy/       ← EconomyProvider (sBank | Vault | interno), Wager, Pot, EconomyManager
├── fair/          ← FairnessService: secreto del servidor, semillas, nonces
├── i18n/          ← Language, LanguageStore (elección por jugador)
├── world/         ← CasinoLayout (geometría pura), CasinoWorldManager (bloques)
├── game/          ← Game, GameMeta, AbstractSoloGame, AbstractGroupGame, GameRegistry
├── session/       ← SessionManager (reloj único), SoloSession, TimedSession
├── gui/           ← Gui, GuiListener, HubGui, BetSelectorGui, StatsGui
├── games/solo/    ← los 12 juegos en solitario
├── games/group/   ← los 9 juegos en grupo
├── stats/         ← PlayerStats, StatsStore
├── config/        ← MultiverseGamblingConfig, Messages (con idiomas)
├── command/  listener/  util/
```

Cuatro piezas merecen explicación:

**`Wager`** es una apuesta ya cobrada. `pay()`, `refund()` y `lose()` marcan la apuesta como
liquidada, así que una segunda llamada no hace nada. Es la diferencia entre "hay que
acordarse de no pagar dos veces" y "no se puede pagar dos veces".

**`Pot`** es el bote común de los juegos en grupo. Guarda un `Wager` por jugador, de modo que
el dinero está fuera de los monederos desde la ventana de apuestas. Cuando alguien se
desconecta en mitad de una ronda no recupera su dinero: su apuesta sigue en el bote y la
puede ganar cualquier otro. Si se va antes de empezar, se le devuelve íntegra. Nunca hay un
camino en el que el dinero se quede a medio camino.

**`SessionManager`** es un único planificador para todo el plugin, en lugar de una tarea por
jugador. Cuando el servidor se apaga, `shutdownAll()` cierra todas las partidas y devuelve el
dinero de las que estaban a medias. No quedan inventarios huérfanos ni tareas colgadas.

**`AbstractGroupGame`** lleva el ciclo de rondas completo (espera → apuestas → juego) una
sola vez, para que ningún juego pueda saltarse el cobro ni la devolución. Un juego en grupo
solo escribe `onRoundStart()`, `tickRound()` y llama a `endRound()` cuando termina; toda la
contabilidad de dinero queda garantizada por la clase base.

---

## Añadir un juego nuevo

Un juego en solitario son unas 60 líneas: extiende `AbstractSoloGame`, declara su `GameMeta`
y escribe `start(Player, double)`. Ya hereda permisos, comprobación de saldo, selector de
apuesta, tickets de estadísticas, anuncios globales y el botón de "jugar otra vez". Su nombre
y descripción salen de `displayName(sender)` / `displayDescription(sender)`, que leen
`catalog.<id-juego>.*` cuando el archivo de idioma los tiene.

Un juego en grupo son unas 80: extiende `AbstractGroupGame` y escribe `onRoundStart()` y
`tickRound()`. El lobby, la ventana de apuestas, el bote, las desconexiones y el reparto los
lleva la clase base.

En ambos casos, si el juego necesita una tabla de premios nueva, **esa tabla va al paquete
`engine` con sus tests**, no dentro del juego. Por último, registra la instancia en
`MultiverseGamblingPlugin.registerGames()`: es la única línea extra. Un juego nuevo recibe
además su arena en el mundo casino automáticamente, y su nombre se traduce añadiendo un
bloque `catalog.<id-juego>` en cada `lang/*.yml`.

---

## Qué cubren los tests

**107 tests.** No comprueban que el código haga lo que dice, sino que **no pueda explotarse**:

- **Invariantes de retorno.** El multiplicador de Minas multiplicado por la probabilidad de
  sobrevivir da exactamente `1 − ventaja` para las 24 dificultades y cada número de casillas
  destapadas. En la ruleta, *todas* las apuestas comparten el mismo retorno.
- **Valor esperado del Crash.** Retirarse en cualquier objetivo tiene el mismo valor
  esperado, comprobado por fórmula y con 400.000 simulaciones.
- **Nada puede pagar de más.** La tabla del Plinko nunca devuelve más de lo que cobra, ni
  siquiera sin tope; la tragaperras está calibrada al 94.75%; el rasca al 92.15%; y un test
  auxiliar crea a propósito una rueda que regala dinero para comprobar que el detector de
  trampas funciona.
- **Uniformidad del azar verificable.** 100.000 tiradas repartidas en 10 cubos, cada uno al
  10%. Además, determinismo: la misma semilla y nonce dan siempre el mismo resultado.
- **Corrección de las reglas.** Los Ases del blackjack como 11 o 1, las 8 categorías del
  póker de dados contando los desempates, las 5 categorías de estrella, las escaleras que no
  deben contar, el 0 que no paga apuestas exteriores.
- **Distribuciones simuladas.** La bolita del Plinko sigue la binomial, cada caballo gana con
  la frecuencia de su fuerza, cada color de la rueda sale con sus casillas y el ganador del
  bote se elige con la probabilidad que le corresponde.
- **Geometría del mundo casino.** Los 21 juegos caben en 500 bloques, las arenas nunca se
  solapan ni tapan el spawn, las entradas miran a la plaza, cada arena tiene su carretera, un
  mundo demasiado pequeño se rechaza y un solo juego recibe igualmente su arena.
- **Resolución de idiomas.** `es`, `ES`, `es_es`, `es-AR`, `spanish` y `español` resuelven
  todos a español, los códigos desconocidos caen a inglés y los códigos incluidos son
  estables.

```bash
mvn test
```

---

Desarrollado por **Chagui68** — [MultiverseGambling](https://github.com/DrakesCraft-Labs/MultiverseGambling).
