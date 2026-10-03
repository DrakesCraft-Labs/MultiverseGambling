# Instalación

[English](Installation) · **Español**

## Requisitos

| Pieza | Versión |
|---|---|
| Servidor | Paper 1.21.11, 26.1 o 26.2 (un solo jar; también carga en 1.21.x) |
| Java | 21 |
| Vault | Opcional, recomendado si tu servidor ya tiene economía |

El plugin se compila contra `paper-api:1.21.11-R0.1-SNAPSHOT` y declara `api-version: '1.21'`.
Las mismas fuentes compilan y pasan sus tests contra Paper 26.1 y 26.2
(`mvn -P api-26.1 test`, `mvn -P api-26.2 test`, con JDK 25).
No usa NMS ni módulos internos, solo API pública.

## Instalación

```bash
mvn package
cp target/MultiverseGambling-1.0.4.jar ~/servidor/plugins/
```

Reinicia el servidor y el plugin escribirá sus archivos por defecto:

```
plugins/MultiverseGambling/
├── config.yml        ← toda la configuración, en inglés y comentada
├── lang/
│   ├── en.yml        ← mensajes en inglés
│   └── es.yml        ← mensajes en español (y el catálogo español de juegos)
├── balances.json     ← monedero interno (solo si no usas Vault)
├── stats.json        ← estadísticas y rankings
├── fairness.json     ← secreto del servidor y semillas de cliente
├── languages.json    ← el idioma que eligió cada jugador
└── pending-items.yml    ← premios en ítems de quien salió a mitad de ronda
```

## Economía

`economy.provider` decide de dónde sale el dinero:

| Valor | Comportamiento |
|---|---|
| `auto` (por defecto) | Prueba los motores de `economy.auto-order` en orden: sBank, Vault, monedero interno |
| `sbank` | Las cuentas bancarias del plugin sBank |
| `vault` | La economía que el servidor registre a través de Vault |
| `internal` | Usa siempre `balances.json` |

Cualquier valor cae al siguiente motor con un aviso por consola, así que el casino siempre
tiene monedero. Los detalles del puente con sBank están en [Economía](Economia-es).

Con el monedero interno, `economy.starting-balance` (1000 por defecto) se abona a cada cuenta
nueva junto con el mensaje de bienvenida. Con Vault eso no ocurre: el plugin nunca toca los
saldos, se los pregunta a Vault.

## El mundo casino

En el primer arranque el plugin crea el mundo casino aparte (`mvgam_casino` por defecto)
y levanta la plaza, las carreteras y una arena por juego. Nunca construye nada en tu mundo
principal. Lo tienes en [Mundo](Mundo-Casino-es).

## Actualizar desde una versión con `messages.yml`

Las versiones antiguas guardaban todos los mensajes en un único `messages.yml`. Las versiones
modernas leen `lang/<codigo>.yml`, y en el primer arranque el archivo viejo **se migra
automáticamente a `lang/en.yml`**, así que no pierdes tus textos personalizados. Después puedes
borrar el `messages.yml` antiguo.

## Comprobar la instalación

```
/mvgam info          → economía activa, juegos registrados y secreto actual
/mvgam world         → viajar al casino
/mvgam language      → el idioma en el que estás leyendo
```

Si falta el mundo casino, la consola dice por qué: puede estar desactivado, la distribución no
caber, o `world.name` apuntar al mundo principal (donde el plugin se niega a construir).
