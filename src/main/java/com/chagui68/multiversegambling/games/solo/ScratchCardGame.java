package com.chagui68.multiversegambling.games.solo;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.ScratchCardTable;
import com.chagui68.multiversegambling.engine.ScratchCardTable.Face;
import com.chagui68.multiversegambling.economy.Wager;
import com.chagui68.multiversegambling.game.AbstractSoloGame;
import com.chagui68.multiversegambling.game.GameCategory;
import com.chagui68.multiversegambling.game.GameMeta;
import com.chagui68.multiversegambling.gui.Gui;
import com.chagui68.multiversegambling.util.Items;
import com.chagui68.multiversegambling.util.Text;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Rasca y gana.
 *
 * <p>Toda la matematica vive en {@link ScratchCardTable}, que es puro Java y esta
 * cubierta por tests: esta clase solo pinta el carton.</p>
 */
public final class ScratchCardGame extends AbstractSoloGame {

    private final ScratchCardTable table = new ScratchCardTable();

    public ScratchCardGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("rasca", "Rasca y Gana", GameCategory.SOLO, Material.MAP)
                .desc("&7Destapa tres casillas de nueve.",
                        "&7Tres iguales pagan el premio del simbolo",
                        "&7y dos iguales devuelven parte.")
                .build());
    }

    @Override
    protected void start(Player player, double bet) {
        Wager wager = stake(player, bet);
        if (wager == null) {
            return;
        }
        List<Face> card = table.newCard(() -> plugin.fair().roll(player.getUniqueId()));
        new ScratchGui(plugin, player, this, wager, card).show();
    }

    /** Paga el carton segun los destapes del jugador. */
    public double resolve(Player player, Wager wager, List<Face> picked) {
        return settle(player, wager, table.payout(picked));
    }

    private static Material iconOf(Face face) {
        return switch (face) {
            case CEREZA -> Material.RED_DYE;
            case LIMON -> Material.YELLOW_DYE;
            case CAMPANA -> Material.BELL;
            case DIAMANTE -> Material.DIAMOND;
            case SIETE -> Material.GOLD_INGOT;
            case CORONA -> Material.NETHER_STAR;
        };
    }

    private static final class ScratchGui extends Gui {

        private final ScratchCardGame game;
        private final Wager wager;
        private final List<Face> card;
        private final List<Integer> picked = new ArrayList<>();
        private boolean resolved;

        ScratchGui(MultiverseGamblingPlugin plugin, Player player, ScratchCardGame game, Wager wager, List<Face> card) {
            super(plugin, player, 5, "&8Rasca y Gana &7· &6Elige 3 casillas");
            this.game = game;
            this.wager = wager;
            this.card = card;
        }

        @Override
        protected void render() {
            clearActions();
            fill(Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());

            set(4, Items.of(Material.MAP)
                    .name("&6Tu carton")
                    .lore(
                            "&7Apuesta: &f" + plugin.economy().format(wager.amount()),
                            "&7Destapadas: &f" + picked.size() + " &7de &f" + ScratchCardTable.PICKS,
                            "&7Premios: &f3 iguales&7 y &f2 iguales &7a "
                                    + Text.multiplier(ScratchCardTable.PAIR_PAYOUT),
                            "",
                            "&7Un carton premiado sale &f"
                                    + Text.percent(game.table.rtp()) + " &7de lo apostado.")
                    .glow(true)
                    .build());

            int[] slots = {11, 12, 13, 20, 21, 22, 29, 30, 31};
            for (int index = 0; index < ScratchCardTable.CELLS; index++) {
                final int cell = index;
                int slot = slots[cell];
                boolean shown = picked.contains(cell) || resolved;
                Face face = card.get(cell);
                if (!shown) {
                    set(slot, Items.of(Material.GRAY_STAINED_GLASS_PANE)
                            .name("&8? ? ?")
                            .lore("&7Pulsa para destapar")
                            .build(), e -> reveal(cell));
                } else {
                    set(slot, Items.of(iconOf(face))
                            .name(face.label())
                            .lore(picked.contains(cell) ? "&7Elegida por ti" : "&7No la destapaste",
                                    "&7Tres iguales pagan &f" + Text.multiplier(face.triple()))
                            .glow(picked.contains(cell))
                            .build());
                }
            }

            int remaining = ScratchCardTable.PICKS - picked.size();
            set(40, Items.of(remaining > 0 ? Material.CLOCK : Material.EMERALD_BLOCK)
                    .name(remaining > 0 ? "&7Te quedan &f" + remaining + " &7destapes" : "&a&lCARTON COMPLETO")
                    .lore("&7Con dos iguales ya recuperas algo de la apuesta.")
                    .build());

            set(36, Items.of(Material.BARRIER)
                    .name("&cSalir")
                    .lore(resolved ? "&7Carton ya resuelto." : "&7Recuperas tu apuesta.")
                    .build(), e -> close());
        }

        private void reveal(int index) {
            if (resolved || picked.contains(index) || picked.size() >= ScratchCardTable.PICKS) {
                return;
            }
            picked.add(index);
            game.sound(player(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.1f + picked.size() * 0.25f);
            if (picked.size() < ScratchCardTable.PICKS) {
                refresh();
                return;
            }

            resolved = true;
            List<Face> chosen = picked.stream().map(card::get).toList();
            double payout = game.resolve(player(), wager, chosen);
            render();

            game.announceResult(player(), payout > wager.amount(),
                    payout > 0 ? "&f" + plugin.economy().format(payout) : "sin premio");
            game.info(player(), game.title());
            game.info(player(), "&7Destapaste: &f" + chosen.stream()
                    .map(Face::label).reduce((a, b) -> a + "&7, " + b).orElse("-"));
            game.showResult(player(), wager.amount(), payout);
            game.sound(player(), payout > wager.amount() ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_VILLAGER_NO,
                    0.9f, payout > wager.amount() ? 1.3f : 0.9f);
            game.offerReplay(player());
        }

        @Override
        protected void onClose() {
            if (!resolved) {
                game.refund(wager);
            }
        }

        @Override
        public String sessionId() {
            return "rasca";
        }
    }
}
