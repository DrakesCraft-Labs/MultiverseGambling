# Item bets

**English** · [Español](Apuestas-Items-es)

Besides money, six solo games accept **items** as the stake. The payout comes back in the
same item, so a bet of diamonds is paid in diamonds and a bet of a custom sword is paid in
copies of that very sword.

| Game | Id | Results listed in the item menu |
|---|---|---|
| Classic Roulette | `roulette` | Even money bets, a dozen or a column, a straight number |
| Slots | `slots` | Every three of a kind and every pair that pays |
| Dice | `dice` | Five sample targets, from a 75% chance down to 1% |
| Plinko | `plinko` | Every bucket of the board |
| Lucky Wheel | `lucky-wheel` | Every multiplier of the wheel and how many segments carry it |
| Coin Flip | `coin-flip` | Guessing the side |

The games with a running decision (crash, mines, towers, blackjack, high-low, scratch) and
the group games stay money only: their prize depends on choices made after the stake, which
cannot be shown up front as a fixed number of items.

## Placing an item bet

1. Open a game (`/mvgam play coin-flip`, the main menu or its pavilion) and pick
   **❖ Bet items** in the bet menu.
2. A six row chest opens. Drop the items into the 21 slots in the middle: **one kind of
   item, any amount**. Shift click from your inventory works.
3. The summary shows what you stake, and the **What you get back** panel lists every result
   with its multiplier and the number of items it returns:

   ```
   Guess the side » 1.96x = 196 × Diamond
   Any other result » you lose the 100 Diamond
   ```

4. Press **Play with these items**. The game continues exactly as with money: pick the
   side, the target, the bet on the table...

**Cancel**, **Bet money instead** or simply closing the chest gives every item back. A game
that is closed before anything is decided also refunds the items.

## Vanilla and custom items

The bet keeps **one exact copy** of the item, with everything it carries: display name,
lore, enchantments, custom model data and the persistent data other plugins use for their
custom ids. Two items only count as the same kind when the server considers them similar,
so you cannot mix a plain diamond sword with an enchanted one in the same bet.

## How many items you get

The payout is `staked × multiplier`, the same formula as money. When that is not a whole
number the fraction is paid **by chance**:

- 100 diamonds at 1.96x is exactly 196 diamonds;
- 10 diamonds at 1.96x is 19.6: you get 19, plus a **60%** chance of the 20th.

On average that pays exactly 19.6, so item bets carry the same return as money bets and
no rounding ever eats into the player's side. The chance of the extra item comes from the
provably fair generator like every other roll.

## Delivery

| Situation | What happens |
|---|---|
| The items fit in the inventory | They go straight in |
| The inventory is full | The rest is dropped at your feet and you are told |
| You left before the round ended | The result is still the one already drawn; the items wait in `pending-items.yml` and arrive one second after you join again |

Item rounds are kept out of the money statistics, the rankings and the big win
announcements: those only compare coins.

## Configuration

```yaml
item-bets:
  enabled: true
  max-items: 1728        # most items one bet can stake (27 stacks of 64)
  blocked:               # exact names, every name ending (*SUFFIX) or starting (PREFIX*)
    - '*SHULKER_BOX'
    - '*BUNDLE'
```

| Key | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Shows the **❖ Bet items** button in the bet menu |
| `max-items` | `1728` | Most items a single bet can hold |
| `blocked` | shulker boxes, bundles | Materials that can never be staked. Containers are blocked so nobody multiplies what is inside them |

Typical extra entries: `NETHERITE_*` to keep end game gear out, `ELYTRA`, `DRAGON_EGG`, or a
server currency item you do not want to grow.
