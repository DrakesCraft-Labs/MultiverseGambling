# Idiomas

[English](Languages) · **Español**

El plugin **se traduce a sí mismo dentro del juego**. Sin reiniciar nada y sin descargar nada:
cada jugador elige lo que lee y la elección se guarda.

```
/casino language es      → este jugador lee español desde ahora
/casino language en      → vuelve al inglés
/casino language         → ¿qué leo y qué hay disponible?
/casino language reset   → olvidar mi elección y seguir mi cliente de Minecraft
```

Se acepta entrada libre: `es`, `ES`, `es_es`, `es-AR`, `spanish` y `español` significan todos
español. Un código desconocido se rechaza mostrando la lista de disponibles.

## Dónde viven los textos

```
plugins/MultiverseGambling/lang/
├── en.yml     ← inglés (se escribe en el primer arranque)
└── es.yml     ← español (se escribe en el primer arranque)
```

Los dos archivos se crean solos y nunca se sobrescriben si ya existen, así que tus ediciones
sobreviven a las actualizaciones. Un `messages.yml` de una versión anterior se migra a
`lang/en.yml` en el primer arranque de una versión moderna.

## Qué idioma recibe cada jugador

El orden de búsqueda es:

1. el idioma que ese jugador eligió con `/casino language`;
2. su **idioma de cliente de Minecraft**, cuando `language.follow-client` es `true` y el plugin
   tiene ese idioma;
3. `language.default` de `config.yml`;
4. inglés, siempre, como último recurso.

La consola, los registros y los carteles del mundo usan `language.default`.

Las elecciones se guardan por UUID en `languages.json`, así que sobreviven a los reinicios y son
independientes del idioma del servidor.

## Qué está traducido

| Pieza | Traducido |
|---|---|
| Mensajes de chat (premios, pérdidas, rondas, duelos, verificación, mundo, idioma, comandos) | sí |
| Menús: hub, estadísticas, selector de apuesta | sí |
| Nombres y descripciones del catálogo de juegos | sí, con `catalog.<id-juego>.*` |
| Carteles del mundo | sí, con el idioma por defecto |
| Respuestas de administración (`/casino give`, `/casino reload`, `/casino world build`...) | sí, en el idioma de quien lo ejecutó |
| Las etiquetas dibujadas **dentro** de cada juego (el botón "CASH OUT", las líneas "Bet:"...) | solo en inglés, por ahora |

Esa última fila es honesta, no está escondida: el dinero y las reglas son iguales en cualquier
idioma, pero un jugador español todavía ve el interior del panel de un juego en inglés. Mover
esas etiquetas a los archivos de idioma es el siguiente hito de traducción.

## Añadir otro idioma

1. Copia `lang/en.yml` a `lang/<codigo>.yml`, por ejemplo `lang/fr.yml`.
2. Traduce los valores (nunca las claves).
3. Ya está: el código aparece en `/casino language` y en el autocompletado en cuanto el archivo
   existe. `/casino reload` lo carga sin reiniciar.

Si falta una clave, el plugin la busca en el idioma del jugador y después en inglés; si falta en
todos, dibuja `&c[missing message: <clave>]` en vez de romper. Una traducción parcial siempre es
segura de publicar.

Un idioma que añadas tú no se puede detectar automáticamente desde el cliente de Minecraft:
seguir el cliente solo resuelve los códigos que el plugin incluye (`en`, `es`). Los idiomas
personalizados se eligen explícitamente con `/casino language <codigo>`.

## Traducir los nombres de los juegos

Los nombres y descripciones de los juegos están en inglés en el código, y cualquier archivo de
idioma puede sobrescribirlos:

```yaml
catalog:
  roulette:
    name: 'Ruleta Clasica'
    description:
      - '&7Rojo, negro, docenas, columnas o un'
      - '&7numero exacto: de &f2x &7a &f36x&7.'
```

`lang/es.yml` incluye todo el catálogo español como ejemplo real. Fíjate en que la clave es
`catalog.<id-juego>`, distinta a propósito de la sección `games:` de los mensajes, que guarda
los mensajes genéricos de los juegos. Las descripciones son listas: cada línea es una línea del
lore, así que puedes reordenarlas o añadir más libremente.

## Placeholders

Las llaves las rellena el plugin; déjalas tal cual:

```
{bet} {prize} {profit} {amount} {game} {player} {min} {max} {seconds} {round}
{current} {balance} {currency} {seed} {value} {usage} {language} {languages}
{native} {world} {hash} {state} {description} {games} {wins} {rate} {wagered}
{returned} {rtp} {biggest} {favourite} {provider} {limit} {edge} {name} {entry}
```

## Colores

El formato clásico con ampersand funciona en todas partes: `&6` oro, `&7` gris, `&c` rojo,
`&a` verde, `&l` negrita, `&m` tachado. Las líneas que empiezan con un guion dentro de una clave
como `gui.stats.lore` son una lista: cada entrada es una línea de la descripción del objeto.
