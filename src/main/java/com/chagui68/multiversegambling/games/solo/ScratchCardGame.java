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
 * Scratch card.
 *
 * <p>All the math lives in {@link ScratchCardTable}, which is pure Java and covered by
 * tests: this class only paints the card.</p>
 */
public final class ScratchCardGame extends AbstractSoloGame {

    private final ScratchCardTable table = new ScratchCardTable();

    public ScratchCardGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("scratch", "Scratch Card", GameCategory.SOLO, Material.MAP)
                .desc("&7Reveal three tiles out of nine.",
                        "&7Three of a kind pay the symbol prize",
                        "&7and two of a kind give part of it back.")
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

    /** Pays the card according to the player's picks. */
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

    private final class ScratchGui extends Gui {

        private final ScratchCardGame game;
        private final Wager wager;
        private final List<Face> card;
        private final List<Integer> picked = new ArrayList<>();
        private boolean resolved;

        ScratchGui(MultiverseGamblingPlugin plugin, Player player, ScratchCardGame game, Wager wager, List<Face> card) {
            super(plugin, player, 5, "&8" + displayName(player) + " &7· &6Pick 3 tiles");
            this.game = game;
            this.wager = wager;
            this.card = card;
        }

        @Override
        protected void render() {
            clearActions();
            fill(Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());

            set(4, Items.of(Material.MAP)
                    .name("&6Your card")
                    .lore(
                            "&7Bet: &f" + plugin.economy().format(wager.amount()),
                            "&7Scratched: &f" + picked.size() + " &7of &f" + ScratchCardTable.PICKS,
                            "&7Prizes: &f3 of a kind&7 and &f2 of a kind &7at "
                                    + Text.multiplier(ScratchCardTable.PAIR_PAYOUT),
                            "",
                            "&7A winning card comes up &f"
                                    + Text.percent(game.table.rtp()) + " &7of what is staked.")
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
                            .lore("&7Click to reveal")
                            .build(), e -> reveal(cell));
                } else {
                    set(slot, Items.of(iconOf(face))
                            .name(face.label())
                            .lore(picked.contains(cell) ? "&7Picked by you" : "&7You did not reveal it",
                                    "&7Three of a kind pay &f" + Text.multiplier(face.triple()))
                            .glow(picked.contains(cell))
                            .build());
                }
            }

            int remaining = ScratchCardTable.PICKS - picked.size();
            set(40, Items.of(remaining > 0 ? Material.CLOCK : Material.EMERALD_BLOCK)
                    .name(remaining > 0 ? "&7You have &f" + remaining + " &7picks left" : "&a&lCARD COMPLETE")
                    .lore("&7Two of a kind already give part of the stake back.")
                    .build());

            set(36, Items.of(Material.BARRIER)
                    .name("&cClose")
                    .lore(resolved ? "&7Card already settled." : "&7You get your stake back.")
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
                    payout > 0 ? "&f" + plugin.economy().format(payout) : "no prize");
            game.info(player(), game.title());
            game.info(player(), "&7You scratched: &f" + chosen.stream()
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
            return "scratch";
        }
    }
}
