package com.chagui68.multiversegambling.games.group;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.DicePoker;
import com.chagui68.multiversegambling.fair.FairnessService;
import com.chagui68.multiversegambling.game.AbstractGroupGame;
import com.chagui68.multiversegambling.game.GameCategory;
import com.chagui68.multiversegambling.game.GameMeta;
import com.chagui68.multiversegambling.world.anim.ArenaShow;
import com.chagui68.multiversegambling.world.anim.ArenaStage;
import com.chagui68.multiversegambling.world.anim.DiceShow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Dice poker.
 *
 * <p>Everybody rolls five dice and the best hand wins. Because every roll comes from
 * the provably fair generator, two players can replay the whole game with
 * {@code /mvgam verify} and check that nobody cheated.</p>
 */
public final class DicePokerGame extends AbstractGroupGame {

    private final Map<UUID, int[]> hands = new LinkedHashMap<>();
    private int resolvedCount;
    /** Dice painted in the arena while the hands are revealed. */
    private ArenaShow show;
    private boolean showTried;

    public DicePokerGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("dice-poker", "Dice Poker", GameCategory.GROUP, Material.DRAGON_BREATH)
                .desc("&7Every player rolls five dice.",
                        "&7The best hand wins: five of a kind, four",
                        "&7of a kind, full house, straight, three...")
                .players(2, 16)
                .build());
    }

    @Override
    protected boolean houseDuelAvailable() {
        return true;
    }

    /**
     * Against the house, the dealer rolls a hand too: the better hand wins, a tie gives
     * the stake back. Both hands come from the same dice, so the duel is even and is paid
     * with the house edge.
     */
    @Override
    protected double houseDuelMultiplier(Player player) {
        return fairDuelMultiplier(0.5);
    }

    @Override
    protected void onRoundStart() {
        hands.clear();
        resolvedCount = 0;
        timer = 0;
        broadcastRoundHeader();
        broadcastPlain("group.dice-poker.dealing", "players", pot.size() + (houseDuelActive() ? 1 : 0));
        List<UUID> seats = new ArrayList<>(pot.participants());
        if (houseDuelActive()) {
            seats.add(FairnessService.HOUSE);
        }
        // The rolls are generated right here, before anybody can react.
        for (UUID id : seats) {
            int[] dice = new int[DicePoker.DICE];
            for (int i = 0; i < dice.length; i++) {
                dice[i] = plugin.fair().rollInt(FairnessService.HOUSE, 6) + 1;
            }
            hands.put(id, dice);
        }
    }

    @Override
    protected void tickRound() {
        timer++;
        if (timer <= 40) {
            if (timer % 20 == 0) {
                soundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.1f);
                actionBarAllKey("group.dice-poker.rolling");
            }
            return;
        }
        // One hand every two seconds, to keep the suspense.
        int index = (timer - 40) / 40;
        List<UUID> order = new ArrayList<>(hands.keySet());
        if (index < order.size()) {
            if (!showTried) {
                showTried = true;
                show = startDiceShow();
            }
            if (show != null) {
                show.tick();
            }
            if ((timer - 40) % 40 == 0) {
                UUID id = order.get(index);
                int[] dice = hands.get(id);
                DicePoker.Hand hand = DicePoker.handOf(dice);
                broadcastPlainFor(player -> new Object[]{
                                "player", playerName(id),
                                "dice", DicePoker.describe(dice),
                                "hand", handName(player, hand)},
                        "group.dice-poker.reveal");
                soundAll(Sound.BLOCK_ANVIL_LAND, 0.6f, 1.0f + index * 0.1f);
            }
            return;
        }

        if (show != null) {
            show.settle();
            show = null;
        }

        // Everybody has revealed: the winner is settled.
        resolvedCount++;
        List<UUID> tied = bestHand();
        if (houseDuelActive()) {
            int outcome = tied.size() > 1 ? 0 : !tied.isEmpty() && isHouse(tied.get(0)) ? -1 : 1;
            if (outcome != 0) {
                UUID best = tied.get(0);
                broadcastPlainFor(player -> new Object[]{
                                "player", playerName(best),
                                "hand", handName(player, DicePoker.handOf(hands.get(best))),
                                "dice", DicePoker.describe(hands.get(best))},
                        "group.dice-poker.winner");
            }
            settleHouseDuel(outcome, houseDuelMultiplier(null));
            return;
        }
        UUID winner = tied.isEmpty() ? null : tied.get(0);

        double total = pot.total();
        if (tied.size() > 1) {
            // A real tie: the pot is split evenly.
            broadcastPlain("group.dice-poker.tie",
                    "players", String.join(", ", tied.stream().map(this::playerName).toList()));
            pot.shareAmong(new java.util.LinkedHashSet<>(tied));
        } else if (winner != null) {
            final UUID winnerId = winner;
            final DicePoker.Hand winningHand = DicePoker.handOf(hands.get(winnerId));
            final String winningDice = DicePoker.describe(hands.get(winnerId));
            pot.payAllTo(winner, plugin.config().groupHouseCut());
            broadcastPlain("group.dice-poker.banner");
            broadcastPlainFor(player -> new Object[]{
                            "player", playerName(winnerId),
                            "hand", handName(player, winningHand),
                            "dice", winningDice},
                    "group.dice-poker.winner");
        }
        broadcastPlain("group.dice-poker.paid", "pot", plugin.economy().format(total));
        soundAll(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        Player winnerPlayer = winner == null ? null : online(winner);
        if (winnerPlayer != null) {
            showTitle(winnerPlayer, "group.dice-poker.title",
                    handKey(DicePoker.handOf(hands.get(winner))));
        }
        endRound();
    }

    @Override
    protected void onRoundEnd() {
        if (show != null) {
            show.cancel();
            show = null;
        }
        showTried = false;
        hands.clear();
        resolvedCount = 0;
    }

    /** Players holding the best hand, in the order they staked. */
    private List<UUID> bestHand() {
        List<UUID> best = new ArrayList<>();
        int bestScore = Integer.MIN_VALUE;
        for (Map.Entry<UUID, int[]> entry : hands.entrySet()) {
            int score = DicePoker.score(entry.getValue());
            if (score > bestScore) {
                bestScore = score;
                best.clear();
                best.add(entry.getKey());
            } else if (score == bestScore) {
                best.add(entry.getKey());
            }
        }
        return best;
    }

    /** Builds the dice of the winning hand in the arena; {@code null} when there is none. */
    private ArenaShow startDiceShow() {
        ArenaStage stage = gatherArena();
        if (stage == null) {
            return null;
        }
        List<UUID> best = bestHand();
        int[] faces = best.isEmpty() ? new int[DicePoker.DICE] : hands.get(best.get(0));
        DiceShow dice = new DiceShow(plugin, stage, faces, Math.max(40, hands.size() * 40));
        dice.start();
        return dice;
    }

    /**
     * Name of a hand in the language of the reader.
     */
    String handName(Player viewer, DicePoker.Hand hand) {
        return plugin.messages().forSender(viewer, handKey(hand));
    }

    private static String handKey(DicePoker.Hand hand) {
        return "group.dice-poker.hand."
                + hand.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }
}
