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
import com.chagui68.multiversegambling.world.anim.ArenaShow;
import com.chagui68.multiversegambling.world.anim.ArenaStage;
import com.chagui68.multiversegambling.world.anim.WheelShow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
    /**
     * Wheel painted in the arena for the round, when there is an arena to paint on.
     */
    private ArenaShow show;

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
        broadcastPlain("group.color-roulette.joined",
                "player", player.getName(),
                "amount", plugin.economy().format(amount),
                "pot", plugin.economy().format(pot.total()));
    }

    /**
     * The player picks which colour to back.
     */
    void choose(Player player, Outcome outcome) {
        if (!pot.contains(player.getUniqueId())) {
            message(player, "group.not-in-color-round");
            return;
        }
        choices.put(player.getUniqueId(), outcome);
        ColorWheel wheel = wheel();
        broadcastPlainFor(viewer -> new Object[]{
                        "player", player.getName(),
                        "colour", colourFor(viewer, outcome),
                        "multiplier", Text.multiplier(wheel.payout(outcome))},
                "group.color-roulette.chose");
    }

    /**
     * Colour name in the language of the reader.
     */
    String colourFor(Player viewer, Outcome outcome) {
        return plugin.messages().forSender(viewer, "group.color-roulette.colour-"
                + outcome.name().toLowerCase(java.util.Locale.ROOT));
    }

    Outcome choiceOf(UUID playerId) {
        return choices.get(playerId);
    }

    @Override
    protected void onRoundStart() {
        result = null;
        timer = 0;
        broadcastRoundHeader();

        // Whoever did not pick a colour gets their money back and leaves the round.
        for (UUID id : pot.participants()) {
            if (!choices.containsKey(id)) {
                pot.remove(id);
                tellKeyed(id, "group.color-roulette.refunded");
            }
        }
        if (pot.size() < minPlayers()) {
            broadcastPlain("group.color-roulette.not-enough");
            endRound();
            return;
        }
        showOdds();
        broadcastPlain("group.color-roulette.spins-in", "seconds", 3);
    }

    @Override
    protected void tickRound() {
        ColorWheel wheel = wheel();
        timer++;
        if (timer <= ANNOUNCE_TICKS) {
            if (timer % 20 == 0) {
                int seconds = (ANNOUNCE_TICKS - timer) / 20;
                if (seconds > 0) {
                    broadcastPlain("group.color-roulette.counts", "seconds", seconds);
                    soundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.4f);
                }
            }
            return;
        }

        int elapsed = timer - ANNOUNCE_TICKS;
        if (elapsed == 1) {
            // Provably fair roll attributed to the house: the wheel belongs to nobody.
            // Drawn before the show starts, so the ball in the arena can land on the
            // colour that really came up.
            result = wheel.spin(() -> plugin.fair().roll(FairnessService.HOUSE));
            show = startWheelShow();
        }
        if (elapsed <= SPIN_TICKS) {
            if (show != null) {
                show.tick();
            } else {
                double progress = (double) elapsed / SPIN_TICKS;
                int wait = 1 + (int) (progress * progress * 8);
                if (elapsed % wait == 0) {
                    Outcome filler = Outcome.values()[Rng.intBetween(0, Outcome.values().length - 1)];
                    actionBarFor(viewer -> new Object[]{"colour", colourFor(viewer, filler)},
                            "group.color-roulette.spinning");
                    soundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 0.9f + (float) progress * 0.9f);
                }
            }
            return;
        }
        if (show != null) {
            show.settle();
            show = null;
        }

        double multiplier = wheel.payout(result);

        Map<UUID, Outcome> bets = new LinkedHashMap<>(choices);
        Map<UUID, Double> stakes = new LinkedHashMap<>(pot.amounts());
        pot.payoutByMultiplier(id -> bets.get(id) == result ? multiplier : 0);

        broadcastPlainFor(viewer -> new Object[]{"colour", colourFor(viewer, result)},
                "group.color-roulette.stopped");
        for (Map.Entry<UUID, Outcome> entry : bets.entrySet()) {
            boolean won = entry.getValue() == result;
            Player viewer = online(entry.getKey());
            if (viewer == null) {
                continue;
            }
            viewer.sendMessage(plugin.messages().componentPlainFor(viewer,
                    won ? "group.color-roulette.won" : "group.color-roulette.lost",
                    "colour", colourFor(viewer, won ? result : entry.getValue()),
                    "multiplier", Text.multiplier(multiplier),
                    "prize", plugin.economy().format(
                            stakes.getOrDefault(entry.getKey(), 0.0) * multiplier)));
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
        broadcastPlainFor(viewer -> {
            StringBuilder builder = new StringBuilder();
            for (Outcome outcome : Outcome.values()) {
                if (builder.length() > 0) {
                    builder.append(" &8| ");
                }
                builder.append(colourFor(viewer, outcome)).append(" &f")
                        .append(Text.multiplier(wheel.payout(outcome)));
            }
            return new Object[]{"payouts", builder.toString()};
        }, "group.color-roulette.odds");
    }

    @Override
    protected void onRoundEnd() {
        if (show != null) {
            show.cancel();
            show = null;
        }
        choices.clear();
        result = null;
    }

    /**
     * Builds the wheel on the arena; {@code null} when the round has no arena to use.
     */
    private ArenaShow startWheelShow() {
        ArenaStage stage = gatherArena();
        if (stage == null) {
            return null;
        }
        List<Material> sectors = colourSectors();
        WheelShow wheelShow = new WheelShow(plugin, stage, sectors, sectorFor(sectors, result), SPIN_TICKS);
        wheelShow.start();
        return wheelShow;
    }

    /**
     * The real wheel: one green pocket and eighteen red and black ones around it.
     */
    private static List<Material> colourSectors() {
        int pockets = 37;
        List<Material> sectors = new ArrayList<>(pockets);
        sectors.add(sectorColour(Outcome.GREEN));
        for (int index = 1; index < pockets; index++) {
            sectors.add(index % 2 == 1 ? sectorColour(Outcome.RED) : sectorColour(Outcome.BLACK));
        }
        return sectors;
    }

    /**
     * Picks the pocket the ball lands on for the colour that really came up.
     */
    private static int sectorFor(List<Material> sectors, Outcome outcome) {
        Material wanted = sectorColour(outcome);
        List<Integer> matching = new ArrayList<>();
        for (int index = 0; index < sectors.size(); index++) {
            if (sectors.get(index) == wanted) {
                matching.add(index);
            }
        }
        if (matching.isEmpty()) {
            return 0;
        }
        return matching.get(Rng.intBetween(0, matching.size() - 1));
    }

    private static Material sectorColour(Outcome outcome) {
        return switch (outcome) {
            case RED -> Material.RED_CONCRETE;
            case BLACK -> Material.BLACK_CONCRETE;
            case GREEN -> Material.LIME_CONCRETE;
        };
    }

    private static Material materialOf(Outcome outcome) {
        return switch (outcome) {
            case RED -> Material.RED_WOOL;
            case BLACK -> Material.BLACK_WOOL;
            case GREEN -> Material.GREEN_WOOL;
        };
    }

    /**
     * Three button menu to pick a colour.
     */
    private final class ColorGui extends Gui {

        private final ColorRouletteGame game;

        ColorGui(MultiverseGamblingPlugin plugin, Player player, ColorRouletteGame game) {
            super(plugin, player, 3, plugin.messages().forSender(player, "panel.color-roulette.title",
                    "game", displayName(player)));
            this.game = game;
        }

        @Override
        protected void render() {
            clearActions();
            fill(Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());

            ColorWheel wheel = game.wheel();
            Outcome chosen = game.choiceOf(player().getUniqueId());
            String state = chosen == null
                    ? label(player(), "panel.color-roulette.not-picked")
                    : label(player(), "panel.color-roulette.picked",
                    "colour", game.colourFor(player(), chosen));
            set(4, Items.of(Material.GOLD_INGOT)
                    .name(label(player(), "panel.color-roulette.info"))
                    .lore(labelLore(player(), "panel.color-roulette.info-lore",
                            "pot", plugin.economy().format(game.pot.total()),
                            "state", state))
                    .glow(true)
                    .build());

            int[] slots = {11, 13, 15};
            Outcome[] values = Outcome.values();
            for (int i = 0; i < values.length; i++) {
                Outcome outcome = values[i];
                boolean selected = outcome == chosen;
                set(slots[i], Items.of(materialOf(outcome))
                        .name((selected ? "&a> " : "")
                                + game.colourFor(player(), outcome))
                        .lore(
                                label(player(), "panel.color-roulette.pockets",
                                        "count", wheel.pockets(outcome)),
                                label(player(), "panel.common.pays",
                                        "multiplier", Text.multiplier(wheel.payout(outcome))),
                                label(player(), "panel.color-roulette.chance",
                                        "percent", Text.percent(wheel.chance(outcome))),
                                "",
                                selected ? label(player(), "panel.common.selected")
                                        : label(player(), "panel.color-roulette.click-pick"))
                        .glow(selected)
                        .build(), e -> {
                    game.choose(player(), outcome);
                    refresh();
                });
            }

            set(22, Items.of(Material.BARRIER)
                    .name(label(player(), "panel.common.close"))
                    .lore(label(player(), "panel.color-roulette.close-lore"))
                    .build(), e -> close());
        }

        @Override
        public String sessionId() {
            return "color-roulette";
        }
    }
}
