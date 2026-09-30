package com.chagui68.multiversegambling.games.group;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.ColorWheel;
import com.chagui68.multiversegambling.engine.ColorWheel.Outcome;
import com.chagui68.multiversegambling.engine.Rng;
import com.chagui68.multiversegambling.fair.FairnessService;
import com.chagui68.multiversegambling.game.AbstractGroupGame;
import com.chagui68.multiversegambling.game.GameCategory;
import com.chagui68.multiversegambling.game.GameMeta;
import com.chagui68.multiversegambling.gui.Gui;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import com.chagui68.multiversegambling.util.Items;
import com.chagui68.multiversegambling.util.Text;

/**
 * Colour roulette for a group.
 *
 * <p>Every player stakes and picks a colour. Green is a single pocket, so it pays
 * around 36 times: it is the thrill of the round and still carries the same edge as
 * red. Every colour is paid with the real pockets of the wheel, never with an
 * invented number.</p>
 */
public final class ColorRouletteGame extends AbstractGroupGame {

    private static final int ANNOUNCE_TICKS = 60;
    private static final int SPIN_TICKS = 60;

    private final Map<UUID, Outcome> choices = new LinkedHashMap<>();
    private Outcome result;

    public ColorRouletteGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("color-roulette", "Colour Roulette", GameCategory.GROUP, Material.RED_WOOL)
                .desc("&7Red, black or green. Everybody bets",
                        "&7and the wheel decides who gets paid.",
                        "&7Green pays &f36x&7.")
                .players(2, 24)
                .build());
    }

    private ColorWheel wheel() {
        return new ColorWheel(18, 18, 1, plugin.config().houseEdge());
    }

    @Override
    protected void onBetPlaced(Player player, double amount) {
        choices.putIfAbsent(player.getUniqueId(), Outcome.RED);
        new ColorGui(plugin, player, this).show();
        broadcastRaw("&8» &f" + player.getName() + " &7joined with &6"
                + plugin.economy().format(amount) + "&7. Pot: &6" + plugin.economy().format(pot.total()));
    }

    /** The player picks which colour to back. */
    void choose(Player player, Outcome outcome) {
        if (!pot.contains(player.getUniqueId())) {
            message(player, "group.not-in-color-round");
            return;
        }
        choices.put(player.getUniqueId(), outcome);
        ColorWheel wheel = wheel();
        broadcastRaw("&8» &f" + player.getName() + " &7goes for " + colour(outcome)
                + " &8(&7pays &f" + Text.multiplier(wheel.payout(outcome)) + "&8)");
    }

    Outcome choiceOf(UUID playerId) {
        return choices.get(playerId);
    }

    @Override
    protected void onRoundStart() {
        result = null;
        timer = 0;
        broadcastRaw(roundHeader());

        // Whoever did not pick a colour gets their money back and leaves the round.
        for (UUID id : pot.participants()) {
            if (!choices.containsKey(id)) {
                pot.remove(id);
                tell(id, "&7You did not pick a colour, so your stake is refunded for this round.");
            }
        }
        if (pot.size() < minPlayers()) {
            tellAll("&7Not enough players picked a colour.");
            endRound();
            return;
        }
        showOdds();
        tellAll("&7The wheel spins in &f3 &7seconds. You can still change colour until then.");
    }

    @Override
    protected void tickRound() {
        ColorWheel wheel = wheel();
        timer++;
        if (timer <= ANNOUNCE_TICKS) {
            if (timer % 20 == 0) {
                int seconds = (ANNOUNCE_TICKS - timer) / 20;
                if (seconds > 0) {
                    broadcastRaw("&7The wheel spins in &f" + seconds + "&7...");
                    soundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.4f);
                }
            }
            return;
        }

        int elapsed = timer - ANNOUNCE_TICKS;
        if (elapsed <= SPIN_TICKS) {
            double progress = (double) elapsed / SPIN_TICKS;
            int wait = 1 + (int) (progress * progress * 8);
            if (elapsed % wait == 0) {
                Outcome filler = Outcome.values()[Rng.intBetween(0, Outcome.values().length - 1)];
                actionBarAll("&7The colour roulette... " + colour(filler));
                soundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 0.9f + (float) progress * 0.9f);
            }
            return;
        }

        // Provably fair roll attributed to the house: the wheel belongs to nobody.
        result = wheel.spin(() -> plugin.fair().roll(FairnessService.HOUSE));
        double multiplier = wheel.payout(result);

        Map<UUID, Outcome> bets = new LinkedHashMap<>(choices);
        Map<UUID, Double> stakes = new LinkedHashMap<>(pot.amounts());
        pot.payoutByMultiplier(id -> bets.get(id) == result ? multiplier : 0);

        broadcastRaw("&8&m        &r &6The wheel stopped on " + colour(result) + " &8&m        ");
        for (Map.Entry<UUID, Outcome> entry : bets.entrySet()) {
            boolean won = entry.getValue() == result;
            tell(entry.getKey(), won
                    ? "&aYour bet on " + colour(result) + " pays &f" + Text.multiplier(multiplier)
                            + "&a, that is &f"
                            + plugin.economy().format(stakes.getOrDefault(entry.getKey(), 0.0) * multiplier)
                    : "&cYour bet on " + colour(entry.getValue()) + " did not come in.");
        }
        soundAll(wonSound(bets), 0.9f, 1.2f);
        choices.clear();
        endRound();
    }

    private Sound wonSound(Map<UUID, Outcome> bets) {
        boolean anyWinner = bets.values().stream().anyMatch(value -> value == result);
        return anyWinner ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_VILLAGER_NO;
    }

    private void showOdds() {
        ColorWheel wheel = wheel();
        StringBuilder builder = new StringBuilder("&7Wheel payouts: ");
        for (Outcome outcome : Outcome.values()) {
            builder.append(colour(outcome)).append(" &f")
                    .append(Text.multiplier(wheel.payout(outcome))).append(" &8| ");
        }
        broadcastRaw(builder.toString());
    }

    private void tellAll(String legacyText) {
        for (UUID id : audienceIds()) {
            tell(id, legacyText);
        }
    }

    private java.util.Set<UUID> audienceIds() {
        java.util.Set<UUID> ids = new java.util.LinkedHashSet<>(pot.participants());
        ids.addAll(waiting);
        return ids;
    }

    @Override
    protected void onRoundEnd() {
        choices.clear();
        result = null;
    }

    static String colour(Outcome outcome) {
        return switch (outcome) {
            case RED -> "&cRed";
            case BLACK -> "&8Black";
            case GREEN -> "&aGreen";
        };
    }

    private static Material materialOf(Outcome outcome) {
        return switch (outcome) {
            case RED -> Material.RED_WOOL;
            case BLACK -> Material.BLACK_WOOL;
            case GREEN -> Material.GREEN_WOOL;
        };
    }

    /** Three button menu to pick a colour. */
    private final class ColorGui extends Gui {

        private final ColorRouletteGame game;

        ColorGui(MultiverseGamblingPlugin plugin, Player player, ColorRouletteGame game) {
            super(plugin, player, 3, "&8" + displayName(player) + " &7· &6Pick a colour");
            this.game = game;
        }

        @Override
        protected void render() {
            clearActions();
            fill(Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());

            ColorWheel wheel = game.wheel();
            Outcome chosen = game.choiceOf(player().getUniqueId());
            set(4, Items.of(Material.GOLD_INGOT)
                    .name("&6Pick your colour")
                    .lore(
                            "&7Bet registered. Current pot: &6"
                                    + plugin.economy().format(game.pot.total()),
                            chosen == null ? "&7You have not picked yet."
                                    : "&7You picked " + colour(chosen) + "&7.",
                            "",
                            "&7You can change it until the wheel spins.")
                    .glow(true)
                    .build());

            int[] slots = {11, 13, 15};
            Outcome[] values = Outcome.values();
            for (int i = 0; i < values.length; i++) {
                Outcome outcome = values[i];
                boolean selected = outcome == chosen;
                set(slots[i], Items.of(materialOf(outcome))
                        .name((selected ? "&a> " : "") + colour(outcome))
                        .lore(
                                "&7Pockets on the wheel: &f" + wheel.pockets(outcome),
                                "&7Pays: &f" + Text.multiplier(wheel.payout(outcome)),
                                "&7Probabilidad: &f" + Text.percent(wheel.chance(outcome)),
                                "",
                                selected ? "&aSelected" : "&eClick to pick")
                        .glow(selected)
                        .build(), e -> {
                    game.choose(player(), outcome);
                    refresh();
                });
            }

            set(22, Items.of(Material.BARRIER)
                    .name("&cClose")
                    .lore("&7You stay in the round with your current colour.")
                    .build(), e -> close());
        }

        @Override
        public String sessionId() {
            return "color-roulette";
        }
    }
}
