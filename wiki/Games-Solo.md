# Solo games

**English** · [Español](Juegos-Solo-es)

Twelve games against the house. All of them open from `/mvgam`, from `/mvgam play <id>`, or
from the solo tab of the main menu, and all of them share the same bet selector and the same
"play again" button.

| Game | Id | Rules | Pays | Return |
|---|---|---|---|---|
| **Classic Roulette** | `roulette` | Red, black, even/odd, 1-18/19-36, dozens, columns or a straight number. The 0 is green and pays only the straight bet | 2x / 3x / 36x | 97.30% (94.74% with `american: true`) |
| **Slots** | `slots` | Three reels, seven weighted symbols; three of a kind pay the table and cherries pay with two | up to 600x | 94.75% |
| **Crash** | `crash` | The curve climbs on its own; cash out before it bursts | 1.00x and up | 98.00% |
| **Mines** | `mines` | 25 tiles with 1 to 24 mines; every safe pick raises the multiplier | grows with difficulty | 98.00% |
| **Towers** | `towers` | 9 floors, 4 tiles, 1 bomb per floor | grows per floor | 98.00% |
| **Blackjack** | `blackjack` | Six deck shoe, natural pays 3:2, push returns the bet, doubling allowed | up to 2.5x | ~99%+ with basic strategy |
| **High-Low** | `high-low` | Guess higher or lower and chain correct calls | chainable | ~98.15% per step |
| **Dice** | `dice` | Target from 0.01 to 99.99, over or under | up to ~99x | 98.00% |
| **Plinko** | `plinko` | The ball falls through the pyramid; the buckets follow the real binomial | up to hundreds of x | 98.00% |
| **Scratch** | `scratch` | Scratch 3 of 9 tiles; three equal pay, two give part of the stake back | up to 50x | 92.15% |
| **Lucky Wheel** | `lucky-wheel` | 12 equally likely segments, most of them empty | up to 4x | 95.00% |
| **Coin Flip** | `coin-flip` | Heads or tails, fair payout trimmed | ~1.96x | 98.00% |

## The design rule they all follow

Every game computes its prize table in the `engine` package, outside Bukkit, and the table is
written so that **multiplier × probability = 1 − edge** for every option. That is what makes
"no target is better than another" a property of the code instead of a hope: roulette bets all
share 36/37 or 36/38, mines and towers invert the survival probability exactly, dice and
plinko are trimmed fair payouts, and crash has the same expected value at any cash out target.

## Details worth knowing

- **Roulette** — `american: true` adds the double zero and drops the return to 94.74%. The 0
  pays no outside bet, exactly like the real wheel.
- **Blackjack** — `dealer-hits-soft-17: false` is the friendly setting; turn it on for a
  slightly bigger house edge with a six deck shoe.
- **Crash** — the crash point comes from a single provably fair roll, so it can be recomputed
  after the secret rotates.
- **Mines and Towers** — the multiplier is the exact inverse of the probability of surviving,
  so cashing out early or late has the same expected value.
- **High-Low** — each step is priced from the ranks that really remain in the shoe, pushes
  included.
- **Plinko** — buckets come from the binomial distribution of `rows` bounces, and
  `max-multiplier` caps the rarest bucket so an extreme configuration cannot print money.
- **Lucky Wheel** — `segments` is the whole table: the return is the average of the list, so
  keeping it below 1 is the only rule.
- **Scratch** — three equal symbols pay the symbol prize, two return part of the stake; the
  test suite checks both ends.

## Betting

The bet selector is the same everywhere: halve, lower 10%, raise 10%, double, the minimum, half
of your balance or everything. The panel shows your balance, the game limits and the current
cap, and the confirm button is greyed out when the stake is out of range.

Solo games can be abandoned: closing the panel refunds or settles the bet depending on the
game state, and `/mvgam cancel <player>` (admin) is the escape hatch if a player gets stuck.
