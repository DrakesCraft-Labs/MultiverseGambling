package com.chagui68.multiversegambling.games.solo;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.economy.Wager;
import com.chagui68.multiversegambling.engine.TowerDifficulty;
import com.chagui68.multiversegambling.game.AbstractSoloGame;
import com.chagui68.multiversegambling.game.GameCategory;
import com.chagui68.multiversegambling.game.GameMeta;
import com.chagui68.multiversegambling.gui.Gui;
import com.chagui68.multiversegambling.session.LiveRound;
import com.chagui68.multiversegambling.util.Items;
import com.chagui68.multiversegambling.util.Text;
import com.chagui68.multiversegambling.world.anim.ArenaStage;
import com.chagui68.multiversegambling.world.anim.HoloButton;
import com.chagui68.multiversegambling.world.anim.TowerShow;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Towers: climb floor after floor by picking a door without a bomb behind it.
 *
 * <p>The player first picks a difficulty, which sets how many doors every floor has and
 * how many of them hide a bomb. The bombs of a floor are placed with the provably fair
 * generator before the player picks, and the multiplier is the exact inverse of the
 * chance of having cleared that many floors, so every difficulty carries the same house
 * edge. In the casino world the round is played on a tower standing on the pavilion,
 * with floating buttons; anywhere else it is played in a menu.</p>
 */
public final class TowersGame extends AbstractSoloGame {

    private static final Color[] DIFFICULTY_COLOURS = {
            HoloButton.GREEN, HoloButton.BLUE, HoloButton.GOLD, HoloButton.RED, HoloButton.PURPLE
    };

    public TowersGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("towers", "Towers", GameCategory.SOLO, Material.LADDER)
                .desc("&7Climb the tower picking doors",
                        "&7without a bomb behind them. Five",
                        "&7difficulties; cash out before you fall.")
                .build());
    }

    int levels() {
        return plugin.config().towersLevels();
    }

    double houseEdge() {
        return plugin.config().houseEdge();
    }

    TowerDifficulty defaultDifficulty() {
        return plugin.config().towersDefaultDifficulty();
    }

    @Override
    protected void start(Player player, double bet) {
        Wager wager = stake(player, bet);
        if (wager == null) {
            return;
        }
        ArenaStage stage = arenaFor(player, ArenaStage.BOARD_PITCH);
        if (stage != null) {
            new ArenaTower(player, wager, stage).open();
            return;
        }
        new TowerGui(plugin, player, this, wager).show();
    }

    // -------------------------------------------------------------------- climb

    /**
     * The state of one climb, shared by the menu and the tower in the arena.
     */
    private final class Climb {

        private final Player owner;
        private final Wager wager;
        private final Set<Integer> bombs = new LinkedHashSet<>();
        private TowerDifficulty difficulty;
        private int level;
        private boolean resolved;

        Climb(Player owner, Wager wager) {
            this.owner = owner;
            this.wager = wager;
        }

        boolean started() {
            return difficulty != null;
        }

        void choose(TowerDifficulty chosen) {
            if (started() || resolved) {
                return;
            }
            difficulty = chosen;
            drawFloor();
        }

        /**
         * Hides the bombs of the floor about to be played: the doors are sorted by a
         * provably fair roll each and the first ones get the bombs.
         */
        private void drawFloor() {
            bombs.clear();
            int tiles = difficulty.tiles();
            double[] rolls = plugin.fair().rolls(owner.getUniqueId(), tiles);
            List<Integer> order = new ArrayList<>(tiles);
            for (int i = 0; i < tiles; i++) {
                order.add(i);
            }
            order.sort((a, b) -> Double.compare(rolls[a], rolls[b]));
            for (int i = 0; i < difficulty.bombs(); i++) {
                bombs.add(order.get(i));
            }
        }

        double multiplierAt(int reached) {
            return difficulty == null ? 1.0 : difficulty.multiplier(reached, houseEdge());
        }

        double[] floorMultipliers() {
            double[] out = new double[levels()];
            for (int i = 0; i < out.length; i++) {
                out[i] = multiplierAt(i + 1);
            }
            return out;
        }

        /**
         * Opens a door of the current floor.
         *
         * @return true when it was safe
         */
        boolean pick(int tile) {
            return !bombs.contains(tile);
        }

        /**
         * Climbs after a safe door and hides the bombs of the next floor.
         */
        void climb() {
            level++;
            if (level < levels()) {
                drawFloor();
            }
        }

        boolean atTop() {
            return level >= levels();
        }
    }

    /**
     * The door hid a bomb: the stake is lost.
     */
    private void lose(Climb climb) {
        climb.resolved = true;
        Player player = climb.owner;
        double payout = settle(player, climb.wager, 0);
        announceResult(player, false, plugin.messages().forSender(player, "panel.towers.boom",
                "floor", climb.level + 1));
        info(player, title(player));
        message(player, "panel.towers.lost", "floor", climb.level + 1,
                "bet", plugin.economy().format(climb.wager.amount()));
        showResult(player, climb.wager.amount(), payout);
        sound(player, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.8f);
        offerReplay(player);
    }

    /**
     * Cashes out what the floors climbed so far pay.
     */
    private void win(Climb climb) {
        climb.resolved = true;
        Player player = climb.owner;
        double multiplier = climb.multiplierAt(climb.level);
        double payout = settle(player, climb.wager, multiplier);
        announceResult(player, true, plugin.messages().forSender(player, "panel.towers.cashed-subtitle",
                "multiplier", Text.multiplier(multiplier)));
        info(player, title(player));
        message(player, "panel.towers.cashed", "floors", climb.level,
                "multiplier", Text.multiplier(multiplier));
        showResult(player, climb.wager.amount(), payout);
        sound(player, Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.3f);
        offerReplay(player);
    }

    /**
     * The round was left open: what was climbed is cashed out, nothing is lost for
     * walking away, and a climb that never started is refunded.
     */
    private void leave(Climb climb, boolean online) {
        if (climb.resolved) {
            return;
        }
        climb.resolved = true;
        if (!climb.started() || climb.level == 0) {
            refund(climb.wager);
            return;
        }
        double multiplier = climb.multiplierAt(climb.level);
        if (online) {
            double payout = settle(climb.owner, climb.wager, multiplier);
            message(climb.owner, "games.closed-cashed", "multiplier", Text.multiplier(multiplier),
                    "prize", plugin.economy().format(payout));
        } else {
            settleOffline(climb.owner.getUniqueId(), climb.wager, multiplier);
        }
    }

    String difficultyName(Player viewer, TowerDifficulty difficulty) {
        return plugin.messages().forSender(viewer, "panel.towers.difficulty." + difficulty.id());
    }

    // ------------------------------------------------------------------- arena

    /**
     * The climb played on the tower of the pavilion, with floating buttons in front of
     * the player: first one per difficulty, then one per door and the cash out.
     */
    private final class ArenaTower extends LiveRound {

        private final Climb climb;
        private final TowerShow show;
        private final List<HoloButton> choices = new ArrayList<>();
        private final List<HoloButton> doors = new ArrayList<>();
        private HoloButton cash;

        ArenaTower(Player player, Wager wager, ArenaStage stage) {
            super(plugin, player, id(), stage);
            this.climb = new Climb(player, wager);
            this.show = new TowerShow(plugin, stage, levels(), 20 * 60 * 10);
        }

        void open() {
            Player player = climb.owner;
            show.start();
            show.waiting(Text.c(plugin.messages().forSender(player, "panel.towers.choose")));
            TowerDifficulty[] all = TowerDifficulty.values();
            List<HoloButton.Spec> specs = new ArrayList<>();
            for (int i = 0; i < all.length; i++) {
                TowerDifficulty difficulty = all[i];
                specs.add(new HoloButton.Spec(Text.c(plugin.messages().forSender(player,
                                "panel.towers.button-difficulty",
                                "name", Text.strip(difficultyName(player, difficulty)).toUpperCase(java.util.Locale.ROOT),
                                "tiles", difficulty.tiles(), "bombs", difficulty.bombs(),
                                "top", Text.multiplier(difficulty.multiplier(levels(), houseEdge())))),
                        DIFFICULTY_COLOURS[i % DIFFICULTY_COLOURS.length], 0.95f, clicker -> choose(difficulty)));
            }
            choices.addAll(show.addControlRow(0.7, player.getUniqueId(), specs));
            begin();
            message(player, "panel.towers.arena-hint");
        }

        private void choose(TowerDifficulty difficulty) {
            if (climb.started() || over()) {
                return;
            }
            climb.choose(difficulty);
            choices.forEach(HoloButton::remove);
            choices.clear();
            Player player = climb.owner;
            show.build(difficulty.tiles(), climb.floorMultipliers(), head(player));
            List<HoloButton.Spec> specs = new ArrayList<>();
            for (int tile = 0; tile < difficulty.tiles(); tile++) {
                int chosen = tile;
                specs.add(new HoloButton.Spec(Text.c("&f&l" + (tile + 1)), HoloButton.BLUE, 1.9f,
                        clicker -> pick(chosen)));
            }
            // The cash out closes the row on the right; created with its widest text, so
            // its hitbox fits whatever it says later.
            specs.add(new HoloButton.Spec(Text.c(plugin.messages().forSender(player,
                    "panel.towers.button-cash", "multiplier", Text.multiplier(99.99),
                    "prize", plugin.economy().format(climb.wager.amount() * 20))),
                    HoloButton.GOLD, 1.0f, clicker -> cashOut()));
            List<HoloButton> row = show.addControlRow(0.7, player.getUniqueId(), specs);
            doors.addAll(row.subList(0, difficulty.tiles()));
            cash = row.get(row.size() - 1);
            cash.text(cashText(player));
            cash.enabled(false);
            sound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.2f);
        }

        private void pick(int tile) {
            if (!climb.started() || climb.resolved || over()) {
                return;
            }
            Set<Integer> floorBombs = new LinkedHashSet<>(climb.bombs);
            if (!climb.pick(tile)) {
                show.boom(climb.level, tile, floorBombs);
                lose(climb);
                end();
                return;
            }
            int floor = climb.level;
            climb.climb();
            show.safe(floor, tile, floorBombs);
            sound(climb.owner, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.0f + climb.level * 0.1f);
            if (climb.atTop()) {
                show.cashOut(climb.level);
                win(climb);
                end();
                return;
            }
            cash.text(cashText(climb.owner));
            cash.enabled(true);
        }

        private void cashOut() {
            if (!climb.started() || climb.resolved || climb.level == 0 || over()) {
                return;
            }
            show.cashOut(climb.level);
            win(climb);
            end();
        }

        private Component cashText(Player viewer) {
            if (climb.level == 0) {
                return Text.c(plugin.messages().forSender(viewer, "panel.towers.button-first"));
            }
            double multiplier = climb.multiplierAt(climb.level);
            return Text.c(plugin.messages().forSender(viewer, "panel.towers.button-cash",
                    "multiplier", Text.multiplier(multiplier),
                    "prize", plugin.economy().format(climb.wager.amount() * multiplier)));
        }

        private void end() {
            finish();
            choices.forEach(HoloButton::remove);
            doors.forEach(HoloButton::remove);
            if (cash != null) {
                cash.remove();
            }
            show.settle();
        }

        @Override
        protected void onAbandon(Player player) {
            if (climb.level > 0 && !climb.resolved) {
                show.cashOut(climb.level);
            }
            leave(climb, true);
            end();
        }

        @Override
        protected void onCancel() {
            leave(climb, false);
            choices.forEach(HoloButton::remove);
            doors.forEach(HoloButton::remove);
            show.cancel();
        }
    }

    private static ItemStack head(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(player);
            head.setItemMeta(meta);
        }
        return head;
    }

    // -------------------------------------------------------------------- menu

    /**
     * The same climb in a menu, for whoever plays outside the casino world: the
     * difficulties first, then the doors of the current floor over a bar with every
     * floor of the tower.
     */
    private final class TowerGui extends Gui {

        private final TowersGame game;
        private final Climb climb;

        TowerGui(MultiverseGamblingPlugin plugin, Player player, TowersGame game, Wager wager) {
            super(plugin, player, 6, plugin.messages().forSender(player, "panel.towers.title",
                    "game", displayName(player)));
            this.game = game;
            this.climb = new Climb(player, wager);
        }

        @Override
        protected void render() {
            clearActions();
            fill(Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
            border(Items.of(Material.YELLOW_STAINED_GLASS_PANE).name(" ").build());
            if (!climb.started()) {
                renderDifficulties();
            } else {
                renderClimb();
            }
            set(49, Items.of(Material.BARRIER)
                    .name(label(player(), "panel.common.close"))
                    .lore(climb.resolved ? label(player(), "panel.common.round-finished")
                            : climb.level > 0 ? label(player(), "panel.towers.close-cashes")
                            : label(player(), "panel.common.refund-lore"))
                    .build(), e -> close());
        }

        private void renderDifficulties() {
            set(4, Items.of(Material.LADDER)
                    .name(label(player(), "panel.towers.choose"))
                    .lore(label(player(), "panel.common.bet",
                            "bet", plugin.economy().format(climb.wager.amount())))
                    .glow(true)
                    .build());
            Material[] icons = {Material.LIME_CONCRETE, Material.LIGHT_BLUE_CONCRETE, Material.YELLOW_CONCRETE,
                    Material.RED_CONCRETE, Material.PURPLE_CONCRETE};
            TowerDifficulty[] all = TowerDifficulty.values();
            int[] slots = {20, 21, 22, 23, 24};
            for (int i = 0; i < all.length; i++) {
                TowerDifficulty difficulty = all[i];
                boolean preferred = difficulty == game.defaultDifficulty();
                set(slots[i], Items.of(icons[i])
                        .name(difficultyName(player(), difficulty))
                        .lore(labelLore(player(), "panel.towers.difficulty-lore",
                                "tiles", difficulty.tiles(), "bombs", difficulty.bombs(),
                                "first", Text.multiplier(difficulty.multiplier(1, houseEdge())),
                                "top", Text.multiplier(difficulty.multiplier(levels(), houseEdge())),
                                "levels", levels()))
                        .glow(preferred)
                        .build(), e -> {
                    climb.choose(difficulty);
                    refresh();
                });
            }
        }

        private void renderClimb() {
            TowerDifficulty difficulty = climb.difficulty;
            double current = climb.multiplierAt(climb.level);
            double next = climb.multiplierAt(climb.level + 1);
            set(4, Items.of(Material.LADDER)
                    .name(label(player(), "panel.towers.floor", "floor", climb.level, "levels", levels()))
                    .lore(label(player(), "panel.common.bet", "bet", plugin.economy().format(climb.wager.amount())),
                            difficultyName(player(), difficulty),
                            label(player(), "panel.towers.multiplier", "multiplier", Text.multiplier(current)),
                            climb.atTop() ? label(player(), "panel.towers.top")
                                    : label(player(), "panel.towers.next", "multiplier", Text.multiplier(next)),
                            label(player(), "panel.towers.bombs", "count", difficulty.bombs()))
                    .glow(true)
                    .build());

            // The doors of the floor being played, spread over the middle of the menu.
            for (int tile = 0; tile < difficulty.tiles(); tile++) {
                int chosen = tile;
                int slot = 22 + 2 * tile - (difficulty.tiles() - 1);
                if (climb.resolved) {
                    boolean bomb = climb.bombs.contains(tile);
                    set(slot, Items.of(bomb ? Material.TNT : Material.EMERALD)
                            .name(label(player(), bomb ? "panel.towers.bomb" : "panel.towers.safe"))
                            .build());
                } else {
                    set(slot, Items.of(Material.OAK_DOOR)
                            .name(label(player(), "panel.towers.tile", "number", tile + 1))
                            .lore(label(player(), "panel.towers.climb", "multiplier", Text.multiplier(next)))
                            .build(), e -> pick(chosen));
                }
            }

            // Every floor of the tower, the cleared ones green.
            int shown = Math.min(7, levels());
            int start = Math.max(0, Math.min(levels() - shown, climb.level - shown / 2));
            for (int i = 0; i < shown; i++) {
                int floor = start + i;
                boolean cleared = floor < climb.level;
                boolean playing = floor == climb.level && !climb.resolved;
                set(37 + i, Items.of(cleared ? Material.LIME_STAINED_GLASS_PANE
                                : playing ? Material.YELLOW_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE)
                        .name(label(player(), "panel.towers.floor", "floor", floor + 1, "levels", levels()))
                        .lore(label(player(), "panel.towers.multiplier",
                                "multiplier", Text.multiplier(climb.multiplierAt(floor + 1))))
                        .glow(playing)
                        .build());
            }

            if (!climb.resolved) {
                set(45, Items.of(climb.level == 0 ? Material.CLOCK : Material.GOLD_BLOCK)
                        .name(climb.level == 0 ? label(player(), "panel.towers.start")
                                : label(player(), "panel.towers.cash-out", "multiplier", Text.multiplier(current)))
                        .lore(climb.level == 0 ? label(player(), "panel.towers.start-lore")
                                : label(player(), "panel.towers.cash-out-lore",
                                "prize", plugin.economy().format(climb.wager.amount() * current)))
                        .glow(climb.level > 0)
                        .build(), e -> cashOut());
            }
        }

        private void pick(int tile) {
            if (climb.resolved || !climb.started()) {
                return;
            }
            if (!climb.pick(tile)) {
                lose(climb);
                render();
                return;
            }
            climb.climb();
            sound(player(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.0f + climb.level * 0.1f);
            if (climb.atTop()) {
                win(climb);
                render();
                return;
            }
            refresh();
        }

        private void cashOut() {
            if (climb.resolved || climb.level == 0) {
                return;
            }
            win(climb);
            render();
        }

        @Override
        protected void onClose() {
            leave(climb, player().isOnline());
        }

        @Override
        public String sessionId() {
            return "towers";
        }
    }
}
