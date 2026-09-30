# Juegos en solitario

[English](Games-Solo) · **Español**

Doce juegos contra la casa. Todos se abren desde `/mvgam`, desde `/mvgam play <id>` o desde
la pestaña de solitario del menú, y todos comparten el mismo selector de apuesta y el mismo
botón de "jugar otra vez".

| Juego | Id | Reglas | Paga | Retorno |
|---|---|---|---|---|
| **Ruleta Clásica** | `roulette` | Rojo, negro, par/impar, 1-18/19-36, docenas, columnas o número exacto. El 0 es verde y solo paga a la apuesta directa | 2x / 3x / 36x | 97.30% (94.74% con `american: true`) |
| **Tragamonedas** | `slots` | Tres rodillos, siete símbolos ponderados; tres iguales pagan la tabla y las cerezas pagan con dos | hasta 600x | 94.75% |
| **Crash** | `crash` | La curva sube sola; retírate antes de que estalle | 1.00x en adelante | 98.00% |
| **Minas** | `mines` | 25 casillas con 1 a 24 minas; cada acierto sube el multiplicador | crece con la dificultad | 98.00% |
| **La Torre** | `towers` | 9 pisos, 4 casillas y 1 bomba por piso | crece por piso | 98.00% |
| **Blackjack** | `blackjack` | Baraja de seis mazos, natural 3:2, empate devuelve apuesta y se puede doblar | hasta 2.5x | ~99%+ con estrategia básica |
| **Mayor o Menor** | `high-low` | Adivina mayor o menor y encadena aciertos | encadenable | ~98.15% por paso |
| **Dados** | `dice` | Objetivo del 0.01 al 99.99, por encima o por debajo | hasta ~99x | 98.00% |
| **Plinko** | `plinko` | La bolita cae por la pirámide; los cubos siguen la binomial real | hasta cientos de x | 98.00% |
| **Rasca y Gana** | `scratch` | Destapa 3 de 9 casillas; tres iguales pagan y dos devuelven parte | hasta 50x | 92.15% |
| **Ruleta de la Suerte** | `lucky-wheel` | 12 segmentos igual de probables, la mayoría vacíos | hasta 4x | 95.00% |
| **Cara o Cruz** | `coin-flip` | Cara o cruz, pago justo recortado | ~1.96x | 98.00% |

## La regla de diseño que siguen todos

Cada juego calcula su tabla de premios en el paquete `engine`, fuera de Bukkit, y la tabla está
escrita para que **multiplicador × probabilidad = 1 − ventaja** en todas las opciones. Eso
convierte "ningún objetivo es mejor que otro" en una propiedad del código y no en una
esperanza: las apuestas de la ruleta comparten 36/37 o 36/38, minas y torre invierten la
probabilidad de sobrevivir exactamente, dados y plinko son pagos justos recortados, y crash
tiene el mismo valor esperado en cualquier objetivo.

## Detalles que conviene saber

- **Ruleta** — `american: true` añade el doble cero y baja el retorno al 94.74%. El 0 no paga
  apuestas exteriores, igual que en la rueda real.
- **Blackjack** — `dealer-hits-soft-17: false` es la opción amable; actívala para una ventaja
  algo mayor con baraja de seis mazos.
- **Crash** — el punto de explosión sale de una tirada verificable, así que se puede recalcular
  cuando el secreto rota.
- **Minas y Torre** — el multiplicador es la inversa exacta de la probabilidad de sobrevivir, así
  que retirarse antes o después tiene el mismo valor esperado.
- **Mayor o Menor** — cada paso se paga según los rangos que quedan de verdad, empates incluidos.
- **Plinko** — los cubos salen de la distribución binomial de `rows` rebotes, y `max-multiplier`
  limita el cubo más raro para que una configuración extrema no imprima dinero.
- **Ruleta de la Suerte** — `segments` es la tabla entera: el retorno es la media de la lista,
  así que mantenerla por debajo de 1 es la única regla.
- **Rasca** — tres símbolos iguales pagan el premio del símbolo, dos devuelven parte de la
  apuesta; la suite comprueba los dos extremos.

## Apostar

El selector de apuesta es igual en todas partes: mitad, bajar 10%, subir 10%, doblar, el mínimo,
la mitad del saldo o todo. El panel muestra tu saldo, los límites del juego y el tope actual, y
el botón de confirmar se apaga cuando la apuesta se sale de rango.

Los juegos en solitario se pueden abandonar: cerrar el panel devuelve o liquida la apuesta según
el estado del juego, y `/mvgam cancel <jugador>` (admin) es la salida de emergencia si alguien
se queda atascado.
