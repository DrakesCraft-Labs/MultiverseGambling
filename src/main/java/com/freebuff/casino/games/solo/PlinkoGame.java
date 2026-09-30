package com.freebuff.casino.games.solo;

import com.freebuff.casino.CasinoPlugin;
import com.freebuff.casino.engine.PlinkoTable;
import com.freebuff.casino.economy.Wager;
import com.freebuff.casino.game.AbstractSoloGame;
import com.freebuff.casino.game.GameCategory;
import com.freebuff.casino.game.GameMeta;
import com.freebuff.casino.gui.Gui;
import com.freebuff.casino.session.TimedSession;
import com.freebuff.casino.util.Items;
import com.freebuff.casino.util.Text;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Plinko: la bolita cae por la piramide y acaba en un cubo.
 *
 * <p>Los cubos no se inventan. Salen de la distribucion binomial real de los
 * rebotes, dividida por el numero de cubos para que el retorno sea el correcto:
 * un test comprueba que la tabla nunca devuelve mas de lo que cobra.</p>
 */
public final class PlinkoGame extends AbstractSoloGame {

    public PlinkoGame(CasinoPlugin plugin) {
        super(plugin, GameMeta.builder("plinko", "Plinko", GameCategory.SOLO, Material.SNOWBALL)
                .desc("&7Suelta la bolita y mira en que cubo",
                        "&7cae. Los bordes pagan muchisimo",
                        "&7y el centro casi nada.")
                .build());
    }

    private int rows() {
        return plugin.config().plinkoRows();
    }

    private double[] table() {
        return PlinkoTable.multipliers(rows(), plugin.config().houseEdge(),
                plugin.config().plinkoMaxMultiplier());
    }

    double payoutFor(int bucket) {
        return table()[Math.max(0, Math.min(rows(), bucket))];
    }

    @Override
    protected void start(Player player, double bet) {
        Wager wager = stake(player, bet);
        if (wager == null) {
            return;
        }
        new PlinkoGui(plugin, player, this, wager).show();
    }

    /** Deja caer la bolita. */
    void drop(Player player, Wager wager) {
        int rows = rows();
        // La direccion de cada rebote sale del generador verificable.
        double[] rolls = plugin.fair().rolls(player.getUniqueId(), rows);
        int finalBucket = bucketFor(rolls);
        TimedSession animation = new TimedSession(plugin, player, id(), rows * 3) {
            @Override
            protected void onFrame(int elapsed, int duration) {
                Player online = player();
                if (online == null) {
                    return;
                }
                int level = Math.min(rows, elapsed / 3);
                StringBuilder path = new StringBuilder();
                for (int i = 0; i < level; i++) {
                    path.append(rolls[i] < 0.5 ? "&e>" : "&b<");
                }
                online.sendActionBar(Text.c("&7Cae la bolita &8" + path + " &7(" + level + "/" + rows + ")"));
                if (elapsed % 3 == 0) {
                    online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f,
                            0.9f + level * 0.05f);
                }
            }

            @Override
            protected void onFinish() {
                Player online = player();
                if (online == null) {
                    refund(wager);
                    return;
                }
                double multiplier = PlinkoGame.this.payoutFor(finalBucket);
                double payout = settle(online, wager, multiplier);
                announceResult(online, payout > wager.amount(),
                        "&fCubo " + finalBucket + " &8· &a" + Text.multiplier(multiplier));
                info(online, title());
                info(online, "&7La bolita cayo en el cubo &f" + finalBucket + "&7/&f" + rows
                        + " y pago &f" + Text.multiplier(multiplier));
                info(online, "&7Probabilidad de ese cubo: &f"
                        + Text.percent(PlinkoTable.bucketChance(rows, finalBucket)));
                showResult(online, wager.amount(), payout);
                sound(online, payout > wager.amount() ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_VILLAGER_NO,
                        0.9f, payout > wager.amount() ? 1.3f : 0.9f);
                offerReplay(online);
            }
        };
        animation.run();
    }

    /** Cuenta los rebotes a la derecha: ese es el cubo final. */
    private static int bucketFor(double[] rolls) {
        int bucket = 0;
        for (double roll : rolls) {
            if (roll < 0.5) {
                bucket++;
            }
        }
        return bucket;
    }

    private static final class PlinkoGui extends Gui {

        private final PlinkoGame game;
        private final Wager wager;
        private boolean armed;

        PlinkoGui(CasinoPlugin plugin, Player player, PlinkoGame game, Wager wager) {
            super(plugin, player, 5, "&8Plinko &7· &6Suelta la bolita");
            this.game = game;
            this.wager = wager;
        }

        @Override
        protected void render() {
            clearActions();
            fill(Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());

            int rows = game.rows();
            double[] table = game.table();
            set(4, Items.of(Material.SNOWBALL)
                    .name("&6Tabla de &f" + rows + " &6filas")
                    .lore(
                            "&7Apuesta: &f" + plugin.economy().format(wager.amount()),
                            "&7Cubos: &f" + (rows + 1),
                            "&7Los bordes son rarisimos y pagan de sobra;",
                            "&7el centro sale casi siempre y paga poco.",
                            "&7Retorno teorico: &f"
                                    + Text.percent(PlinkoTable.rtp(table, rows, 0)) + "&7.")
                    .glow(true)
                    .build());

            // Los 9 cubos mas representativos, centrados.
            int shown = Math.min(9, rows + 1);
            int offset = (rows + 1 - shown) / 2;
            for (int i = 0; i < shown; i++) {
                int bucket = offset + i;
                int slot = 19 + i + (9 - shown) / 2;
                set(slot, Items.of(bucket == 0 || bucket == rows ? Material.GOLD_BLOCK : Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                        .name("&fCubo " + bucket + ": &a" + Text.multiplier(table[bucket]))
                        .lore("&7Probabilidad: &f" + Text.percent(PlinkoTable.bucketChance(rows, bucket)))
                        .glow(bucket == 0 || bucket == rows)
                        .build());
            }

            set(40, Items.of(Material.EMERALD_BLOCK)
                    .name("&a&lSOLTAR BOLITA")
                    .lore("&7Apuestas &6" + plugin.economy().format(wager.amount()),
                            "&7y la bolita cae sola.", "",
                            "&ePulsa para soltar")
                    .glow(true)
                    .build(), e -> {
                armed = true;
                close();
                game.drop(player(), wager);
            });

            set(36, Items.of(Material.BARRIER)
                    .name("&cCancelar")
                    .lore("&7Recuperas tu apuesta.")
                    .build(), e -> close());
        }

        @Override
        protected void onClose() {
            if (!armed) {
                game.refund(wager);
            }
        }

        @Override
        public String sessionId() {
            return "plinko";
        }
    }
}
