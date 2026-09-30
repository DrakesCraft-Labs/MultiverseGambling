# Group games

**English** · [Español](Juegos-Grupo-es)

Nine games with **automatic rounds**. Nobody has to start anything: a player joins with
`/mvgam play <game>`, bets during the window, and the round plays itself. When it ends the
next betting window opens on its own for whoever wants to join again.

The shared round cycle is: **waiting for players → betting window → in game → payout**.

| Game | Id | Rules | Players | Return |
|---|---|---|---|---|
| **Color Roulette** | `color-roulette` | Everybody bets red, black or green; green is a single pocket, so it pays ~36x | 2-24 | 97.30% |
| **Jackpot** | `jackpot` | Everybody puts money in and tickets are proportional to the stake; one winner takes it all | 2-24 | 100% − commission |
| **Hot Bomb** | `hot-bomb` | TNT passes from hand to hand with a drawn fuse; whoever it catches is out and their money stays in the pot | 2-12 | 100% − commission |
| **Bomb Board** | `bomb-board` | Shared board with hidden bombs; players reveal tiles in turns; the last one standing collects | 2-12 | 100% − commission |
| **Russian Roulette** | `russian-roulette` | In turns each player pulls the trigger with 1 bullet in 6 chambers; the survivor takes the pot | 2-8 | 100% − commission |
| **Horse Race** | `race` | 8 horses with published odds; backing the favourite pays little, the outsider pays a lot | 2-24 | 98.00% per horse |
| **Duel 1v1** | `duel` | Challenge somebody for a stake; both put the same in and a coin decides | 2 | 100% − commission |
| **Raffle** | `raffle` | Tickets at a fixed price and a draw of three prizes: 70%, 20% and 10% of the pot | 2-24 | 100% − commission |
| **Dice Poker** | `dice-poker` | Five dice each; the best hand wins and ties split the pot | 2-16 | 100% − commission |

## Player against player means no house edge

These games move money between players, so by default the house keeps **nothing**
(`group.house-commission: 0.0`). Set it to a small value, say `0.02`, if you want the casino
to take a cut of the jackpot, the raffle and dice poker.

## Rounds and the pot

- The betting window lasts `group.betting-seconds` (20 by default). The lobby announces the
  countdown, then the round starts on its own.
- Money leaves the wallets **when you bet**, not when the round starts: the player's stake is
  held in a `Pot` that keeps one settled-once wager per player.
- A round needs the minimum number of players with a bet; otherwise it is cancelled and
  everybody is refunded.
- If you join while a round is in progress you are queued for the next one and told so.
- **Disconnecting mid round does not return your money.** Your bet stays in the pot and any
  other player can win it, which is what makes rage quitting pointless. Leaving **before** the
  round starts refunds you in full.
- The seat limit is `group.max-players`; when the table is full you are told to try the next
  round.

## Turn based games and chat buttons

Hot Bomb, Bomb Board and Russian Roulette run on turns with a per turn clock
(`group.bomb-board.seconds-per-turn`, and the fuse range for the hot bomb). They announce the
current player and, if they do not act in time, the turn moves on by itself, so a single idle
player cannot freeze the table.

Some actions are also reachable from the chat: clickable buttons send `/mvgam action <action>`
(`reveal`, `horse 3`, `shoot`, `accept`, `decline`...) which is what makes the games playable
from a phone or from chat without opening a menu.

## Fairness

Rounds draw their randomness from the same provably fair source as the solo games, attributed
to the fixed casino identity (zero UUID). That means a whole race or a hot bomb round can be
audited afterwards with `/mvgam verify`. See [Fairness](Fairness).
