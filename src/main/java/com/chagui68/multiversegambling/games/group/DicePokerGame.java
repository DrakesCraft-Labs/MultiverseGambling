package com.chagui68.multiversegambling.games.group;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.DicePoker;
import com.chagui68.multiversegambling.fair.FairnessService;
import com.chagui68.multiversegambling.game.AbstractGroupGame;
import com.chagui68.multiversegambling.game.GameCategory;
import com.chagui68.multiversegambling.game.GameMeta;
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

    public DicePokerGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("dice-poker", "Dice Poker", GameCategory.GROUP, Material.DRAGON_BREATH)
                .desc("&7Every player rolls five dice.",
                        "&7The best hand wins: five of a kind, four",
                        "&7of a kind, full house, straight, three...")
                .players(2, 16)
                .build());
    }

    @Override
    protected void onRoundStart() {
        hands.clear();
        resolvedCount = 0;
        timer = 0;
        broadcastRoundHeader();
        broadcastPlain("group.dice-poker.dealing", "players", pot.size());
        // The rolls are generated right here, before anybody can react.
        for (UUID id : pot.participants()) {
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

        // Everybody has revealed: the winner is settled.
        resolvedCount++;
        UUID winner = null;
        int bestScore = Integer.MIN_VALUE;
        List<UUID> tied = new ArrayList<>();
        for (Map.Entry<UUID, int[]> entry : hands.entrySet()) {
            int score = DicePoker.score(entry.getValue());
            if (score > bestScore) {
                bestScore = score;
                winner = entry.getKey();
                tied.clear();
                tied.add(entry.getKey());
            } else if (score == bestScore) {
                tied.add(entry.getKey());
            }
        }

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
        hands.clear();
        resolvedCount = 0;
    }

    /** Name of a hand in the language of the reader. */
    String handName(Player viewer, DicePoker.Hand hand) {
        return plugin.messages().forSender(viewer, handKey(hand));
    }

    private static String handKey(DicePoker.Hand hand) {
        return "group.dice-poker.hand."
                + hand.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }
}
