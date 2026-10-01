# Desarrollo

[English](Development) · **Español**

## Compilar y testear

```bash
mvn package      # compila, ejecuta los 197 tests y escribe target/MultiverseGambling-1.0.4.jar
mvn test         # solo los tests
mvn -q compile   # solo el compilador
```

La compilación necesita Java 21. Los tests son JUnit 5 y cubren los paquetes `engine`, `i18n` y
`world`, así que se ejecutan en un par de segundos y sin servidor.

## Requisitos de la cadena de herramientas

| Pieza | Versión |
|---|---|
| Java | 21 (`maven.compiler.release`) |
| Paper API | `1.21.11-R0.1-SNAPSHOT`, `provided` |
| Vault API | `1.7.1`, `provided` |
| JUnit | Jupiter 5.10.2, `test` |
| Filtrado | Solo se filtra `plugin.yml`; los demás recursos conservan sus placeholders |

## Estructura del proyecto

```
src/main/java/com/chagui68/multiversegambling
├── engine/    tablas y reglas en Java puro (sin Bukkit); aquí vive la matemática
├── economy/   EconomyProvider (sBank | Vault | interno), EconomyProviders, Wager, Pot, EconomyManager
├── fair/      FairnessService: secreto del servidor, semillas, nonces
├── i18n/      Language, LanguageStore
├── world/     CasinoLayout (geometría pura), CasinoWorldManager (bloques)
├── game/      Game, GameMeta, AbstractSoloGame, AbstractGroupGame, GameRegistry
├── session/   SessionManager, SoloSession, TimedSession
├── gui/       Gui, GuiListener, HubGui, BetSelectorGui, StatsGui
├── games/solo/  12 juegos
├── games/group/ 9 juegos
├── stats/     PlayerStats, StatsStore
├── config/    MultiverseGamblingConfig, Messages
└── command/, listener/, util/
```

## Las cuatro clases que lo mantienen honesto

- **`Wager`** — una apuesta que se liquida una vez. `pay()`, `refund()` y `lose()` son
  idempotentes por diseño, así que un pago doble es imposible.
- **`Pot`** — el bote compartido de los juegos en grupo: un wager por jugador, devoluciones antes
  de la ronda y ganable por los demás después de ella.
- **`SessionManager`** — un único planificador para todo el plugin; `shutdownAll()` liquida cada
  partida abierta al apagar.
- **`AbstractGroupGame`** — el ciclo de ronda (espera → apuestas → juego) en un solo sitio, así
  ningún juego puede saltarse el cobro ni la devolución. Los juegos escriben `onRoundStart()` y
  `tickRound()`.

## Añadir un juego

1. Escribe la tabla de premios en `engine/` **con sus tests** si el juego necesita una. Ahí se
   comprueban los invariantes de retorno.
2. Extiende `AbstractSoloGame` (unas 60 líneas) o `AbstractGroupGame` (unas 80) y declara su
   `GameMeta`.
3. En solitario escribe `start(Player, double)`; en grupo, `onRoundStart()` y `tickRound()`.
4. Registra la instancia en `MultiverseGamblingPlugin.registerGames()`.

Gratis: permisos, comprobación de saldo, selector de apuesta, estadísticas, anuncios, el botón de
"jugar otra vez", una arena en el mundo casino y un nombre traducible con `catalog.<id-juego>` en
los archivos de idioma.

## Convenciones

- El inglés es el idioma del código, la configuración, los comentarios y la documentación; el
  español vive en `lang/es.yml` y en `README.es.md` y las páginas `-es` de la wiki.
- El dinero nunca usa el `Rng` de animaciones; pasa por `FairnessService`.
- Todo lo que lee el jugador sale de una clave en `lang/*.yml` (o de un bloque `catalog.<id>`
  para los nombres de juegos).
- Las tablas de premios se quedan en `engine/`, testeadas y fuera de las clases de Bukkit.

## Qué garantizan los tests

197 tests: invariantes de retorno de cada tabla, valor esperado del crash por fórmula y con
400.000 simulaciones, comprobaciones de "nada paga de más" (incluida una rueda que regala dinero
a propósito y que el detector debe cazar), uniformidad y determinismo de las tiradas
verificables, reglas del blackjack y del póker de dados, distribuciones simuladas, geometría del
mundo casino y resolución de idiomas.

Cuando cambies una tabla ejecuta `mvn test` antes de commitear: un retorno por encima de 1 hace
fallar la compilación a propósito.
