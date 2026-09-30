# Problemas

[English](Troubleshooting) · **Español**

## No aparece el mundo casino

```
/mvgam world      → "El mundo casino no está listo todavía; revisa la consola del servidor."
```

Mira la consola. Las causas habituales:

| Mensaje en consola | Solución |
|---|---|
| `world is disabled` | Pon `world.enabled: true` en `config.yml` y ejecuta `/mvgam reload` |
| `too small` | Sube `world.size`; el plugin también lo agranda solo en pasos de 50 |
| `refuses to build in the main world` | `world.name` apunta a tu mundo de supervivencia: cámbialo |
| `could not be created` | La carpeta no tiene permisos de escritura, o ya hay un mundo con ese nombre cargado con otros ajustes |

Si solo querías los juegos, desactiva el mundo (`world.enabled: false`): `/mvgam play <id>`
sigue funcionando desde cualquier sitio.

## Un juego dice que está desactivado

`games.<id>.enabled: false` en `config.yml`. Actívalo y `/mvgam reload`. Los juegos
desactivados también pierden su arena en el siguiente `/mvgam world build`.

## Un jugador se ha quedado atascado en una partida

```
/mvgam cancel <jugador>
```

Cierra su partida y devuelve la apuesta. Necesita `mvgam_admin`.

## El dinero no es mío, es del servidor

Comprueba el proveedor activo con `/mvgam info`. Si dice `Vault`, todos los saldos vienen de tu
plugin de economía y el plugin nunca escribe en él directamente. Si dice `Internal`, el plugin
usa `balances.json`; cambia `economy.provider` a `vault` para devolver el dinero a tu economía.

## No se anuncian los premios

`game.announce-wins: false`, o el premio está por debajo de `game.announce-threshold` (50000 por
defecto).

## Aparece `[missing message: algo.clave]`

A un archivo de idioma le falta esa clave. El plugin muestra el marcador en vez de romper, y
además cae primero al inglés. Copia la clave de `lang/en.yml`,o borra el archivo para que se regenere y rehaz tus cambios.

## Un idioma no aparece en `/mvgam language`

Solo se ofrecen los archivos que existen en disco. Comprueba que existe
`plugins/MultiverseGambling/lang/<codigo>.yml` (el código es el nombre del archivo sin la
extensión) y ejecuta `/mvgam reload`.

## El plugin solo contesta en inglés

`language.default` es `en` y nadie ha elegido nada: la consola y los jugadores sin preferencia
leen inglés. Los jugadores pueden elegir con `/mvgam language es`, o pon `language.default: es`
para todo el servidor.

## Ha cambiado el retorno de tragaperras, plinko, rasca o la ruleta

Es a propósito: la suite fija esos retornos. `mvn test` te dice exactamente qué tabla se salió de
rango, para que corrijas pesos o pagos de forma deliberada.

## Señal de que falta una dependencia

`UnsupportedClassVersionError` al arrancar significa que el servidor usa un Java antiguo: Paper
1.21 necesita Java 21. Vault solo hace falta cuando `economy.provider` es `vault` o `auto` **y**
quieres usar la economía del servidor.
