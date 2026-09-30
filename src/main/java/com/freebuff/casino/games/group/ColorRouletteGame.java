package com.freebuff.casino.games.group;

import com.freebuff.casino.CasinoPlugin;
import com.freebuff.casino.engine.ColorWheel;
import com.freebuff.casino.engine.ColorWheel.Outcome;
import com.freebuff.casino.engine.Rng;
import com.freebuff.casino.fair.FairnessService;
import com.freebuff.casino.game.AbstractGroupGame;
import com.freebuff.casino.game.GameCategory;
import com.freebuff.casino.game.GameMeta;
import com.freebuff.casino.gui.Gui;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import com.freebuff.casino.util.Items;
import com.freebuff.casino.util.Text;

/**
 * Ruleta de colores en grupo.
 *
 * <p>Cada jugador pone su apuesta y elige color. El verde es una casilla unica, asi
 * que paga unas 36 veces: es la emocion de la ronda y, aun asi, tiene la misma
 * ventaja que el rojo. Todos los colores se pagan con las casillas reales de la
 * rueda, nunca con un numero inventado.</p>
 */
public final class ColorRouletteGame extends AbstractGroupGame {

    private static final int ANNOUNCE_TICKS = 60;
    private static final int SPIN_TICKS = 60;

    private final Map<UUID, Outcome> choices = new LinkedHashMap<>();
    private Outcome result;

    public ColorRouletteGame(CasinoPlugin plugin) {
        super(plugin, GameMeta.builder("ruleta-colores", "Ruleta de Colores", GameCategory.GRUPO, Material.RED_WOOL)
                .desc("&7Rojo, negro o verde. Todos apuestan",
                        "&7y la rueda decide quien cobra.",
                        "&7El verde paga unas &f36x&7.")
                .players(2, 24)
                .build());
    }

    private ColorWheel wheel() {
        return new ColorWheel(18, 18, 1, plugin.config().houseEdge());
    }

    @Override
    protected void onBetPlaced(Player player, double amount) {
        choices.putIfAbsent(player.getUniqueId(), Outcome.ROJO);
        new ColorGui(plugin, player, this).show();
        broadcastRaw("&8» &f" + player.getName() + " &7entro con &6"
                + plugin.economy().format(amount) + "&7. Bote: &6" + plugin.economy().format(pot.total()));
    }

    /** El jugador elige a que color va. */
    void choose(Player player, Outcome outcome) {
        if (!pot.contains(player.getUniqueId())) {
            message(player, "grupo.sin-mesa-color");
            return;
        }
        choices.put(player.getUniqueId(), outcome);
        ColorWheel wheel = wheel();
        broadcastRaw("&8» &f" + player.getName() + " &7va al " + colour(outcome)
                + " &8(&7paga &f" + Text.multiplier(wheel.payout(outcome)) + "&8)");
    }

    Outcome choiceOf(UUID playerId) {
        return choices.get(playerId);
    }

    @Override
    protected void onRoundStart() {
        result = null;
        timer = 0;
        broadcastRaw(roundHeader());

        // Quien no haya elegido color recupera su dinero y sale de la ronda.
        for (UUID id : pot.participants()) {
            if (!choices.containsKey(id)) {
                pot.remove(id);
                tell(id, "&7No elegiste color, te devolvemos la apuesta para esta ronda.");
            }
        }
        if (pot.size() < minPlayers()) {
            tellAll("&7No hay suficientes jugadores con color elegido.");
            endRound();
            return;
        }
        showOdds();
        tellAll("&7La rueda gira en &f3 &7segundos. Puedes cambiar de color hasta entonces.");
    }

    @Override
    protected void tickRound() {
        ColorWheel wheel = wheel();
        timer++;
        if (timer <= ANNOUNCE_TICKS) {
            if (timer % 20 == 0) {
                int seconds = (ANNOUNCE_TICKS - timer) / 20;
                if (seconds > 0) {
                    broadcastRaw("&7La rueda gira en &f" + seconds + "&7...");
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
                actionBarAll("&7La ruleta de colores... " + colour(filler));
                soundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 0.9f + (float) progress * 0.9f);
            }
            return;
        }

        // Tirada verificable atribuida a la casa: la rueda no es de nadie.
        result = wheel.spin(() -> plugin.fair().roll(FairnessService.HOUSE));
        double multiplier = wheel.payout(result);

        Map<UUID, Outcome> bets = new LinkedHashMap<>(choices);
        Map<UUID, Double> stakes = new LinkedHashMap<>(pot.amounts());
        pot.payoutByMultiplier(id -> bets.get(id) == result ? multiplier : 0);

        broadcastRaw("&8&m        &r &6La ruleta se paro en " + colour(result) + " &8&m        ");
        for (Map.Entry<UUID, Outcome> entry : bets.entrySet()) {
            boolean won = entry.getValue() == result;
            tell(entry.getKey(), won
                    ? "&aTu apuesta al " + colour(result) + " cobra &f" + Text.multiplier(multiplier)
                            + "&a, o sea &f"
                            + plugin.economy().format(stakes.getOrDefault(entry.getKey(), 0.0) * multiplier)
                    : "&cTu apuesta al " + colour(entry.getValue()) + " no ha salido.");
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
        StringBuilder builder = new StringBuilder("&7Pagos de la rueda: ");
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
            case ROJO -> "&cRojo";
            case NEGRO -> "&8Negro";
            case VERDE -> "&aVerde";
        };
    }

    private static Material materialOf(Outcome outcome) {
        return switch (outcome) {
            case ROJO -> Material.RED_WOOL;
            case NEGRO -> Material.BLACK_WOOL;
            case VERDE -> Material.GREEN_WOOL;
        };
    }

    /** Menu de tres botones para elegir color. */
    private static final class ColorGui extends Gui {

        private final ColorRouletteGame game;

        ColorGui(CasinoPlugin plugin, Player player, ColorRouletteGame game) {
            super(plugin, player, 3, "&8Ruleta de Colores &7· &6Elige");
            this.game = game;
        }

        @Override
        protected void render() {
            clearActions();
            fill(Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());

            ColorWheel wheel = game.wheel();
            Outcome chosen = game.choiceOf(player().getUniqueId());
            set(4, Items.of(Material.GOLD_INGOT)
                    .name("&6Elige tu color")
                    .lore(
                            "&7Apuesta registrada. Bote actual: &6"
                                    + plugin.economy().format(game.pot.total()),
                            chosen == null ? "&7Todavia no has elegido."
                                    : "&7Has elegido " + colour(chosen) + "&7.",
                            "",
                            "&7Puedes cambiarlo hasta que la rueda gire.")
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
                                "&7Casillas en la rueda: &f" + wheel.pockets(outcome),
                                "&7Paga: &f" + Text.multiplier(wheel.payout(outcome)),
                                "&7Probabilidad: &f" + Text.percent(wheel.chance(outcome)),
                                "",
                                selected ? "&aSeleccionado" : "&ePulsa para elegir")
                        .glow(selected)
                        .build(), e -> {
                    game.choose(player(), outcome);
                    refresh();
                });
            }

            set(22, Items.of(Material.BARRIER)
                    .name("&cCerrar")
                    .lore("&7Sigues dentro de la ronda con tu color actual.")
                    .build(), e -> close());
        }

        @Override
        public String sessionId() {
            return "ruleta-colores";
        }
    }
}
