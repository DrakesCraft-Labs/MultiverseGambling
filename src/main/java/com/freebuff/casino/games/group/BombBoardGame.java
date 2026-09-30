package com.freebuff.casino.games.group;

import com.freebuff.casino.CasinoPlugin;
import com.freebuff.casino.engine.Rng;
import com.freebuff.casino.fair.FairnessService;
import com.freebuff.casino.game.AbstractGroupGame;
import com.freebuff.casino.game.GameCategory;
import com.freebuff.casino.game.GameMeta;
import com.freebuff.casino.gui.Gui;
import com.freebuff.casino.util.Items;
import com.freebuff.casino.util.Text;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Tablero de bombas.
 *
 * <p>Un unico tablero con bombas escondidas y jugadores que destapan por turnos.
 * El que pisa una bomba queda fuera y su dinero se queda en el bote. Las bombas se
 * colocan con la mezcla verificable del casino, asi que la posicion de cada una es
 * reproducible a posteriori.</p>
 */
public final class BombBoardGame extends AbstractGroupGame {

    private final Set<Integer> bombs = new LinkedHashSet<>();
    private final Set<Integer> revealed = new LinkedHashSet<>();
    private final List<UUID> order = new ArrayList<>();
    private final Set<UUID> alive = new LinkedHashSet<>();
    private int turnIndex;
    private int turnTicks;
    private BombBoardGui board;
    private boolean counting;

    public BombBoardGame(CasinoPlugin plugin) {
        super(plugin, GameMeta.builder("tablero-bombas", "Tablero de Bombas", GameCategory.GRUPO, Material.GUNPOWDER)
                .desc("&7Un tablero con bombas escondidas.",
                        "&7Por turnos, cada uno destapa casilla.",
                        "&7El ultimo en pie se lleva el bote.")
                .players(2, 12)
                .build());
    }

    private int size() {
        return Math.min(54, plugin.config().bombBoardSize());
    }

    private int bombCount() {
        return Math.max(1, Math.min(size() - 1, plugin.config().bombBoardBombs()));
    }

    private UUID current() {
        if (order.isEmpty()) {
            return null;
        }
        if (turnIndex >= order.size()) {
            turnIndex = 0;
        }
        return order.get(turnIndex % order.size());
    }

    @Override
    protected void onRoundStart() {
        bombs.clear();
        revealed.clear();
        order.clear();
        order.addAll(pot.participants());
        alive.clear();
        alive.addAll(order);
        turnIndex = 0;
        turnTicks = 0;
        counting = true;
        timer = 0;

        // Colocacion verificable: se ordenan las casillas por una tirada del casino.
        double[] rolls = plugin.fair().rolls(FairnessService.HOUSE, size());
        List<Integer> cells = new ArrayList<>(size());
        for (int i = 0; i < size(); i++) {
            cells.add(i);
        }
        cells.sort((a, b) -> Double.compare(rolls[a], rolls[b]));
        for (int i = 0; i < bombCount(); i++) {
            bombs.add(cells.get(i));
        }

        broadcastRaw(roundHeader());
        broadcastRaw("&7Bote: &6" + plugin.economy().format(pot.total())
                + " &8| &7tablero de &f" + size() + " &7casillas con &c" + bombCount() + " &7bombas.");
        broadcastRaw("&7Participan: &f" + order.size() + " &7jugadores.");
    }

    @Override
    protected void tickRound() {
        if (alive.size() <= 1) {
            settle();
            return;
        }
        if (counting) {
            timer++;
            if (timer % 20 == 0) {
                actionBarAll("&7Preparados... &f" + (3 - timer / 20) + "s");
                soundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.3f);
            }
            if (timer >= 60) {
                counting = false;
                openBoard();
            }
            return;
        }

        UUID currentId = current();
        if (currentId == null) {
            return;
        }
        turnTicks++;
        int limit = plugin.config().bombBoardTurnSeconds() * 20;
        Player player = online(currentId);
        if (player != null) {
            int left = Math.max(0, (limit - turnTicks) / 20);
            player.sendActionBar(Text.c("&7Tu turno &8| &f" + left + "s &8| &7bote &6"
                    + plugin.economy().format(pot.total())));
        }
        if (turnTicks >= limit) {
            // Sin decision: se destapa una casilla al azar.
            List<Integer> free = freeCells();
            if (free.isEmpty()) {
                settle();
                return;
            }
            broadcastRaw("&7Se agoto el tiempo de &f" + playerName(currentId)
                    + "&7; destapa al azar.");
            reveal(currentId, Rng.pick(free));
        }
    }

    private List<Integer> freeCells() {
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < size(); i++) {
            if (!revealed.contains(i)) {
                free.add(i);
            }
        }
        return free;
    }

    private void openBoard() {
        UUID currentId = current();
        if (currentId == null) {
            return;
        }
        Player player = online(currentId);
        if (player == null) {
            return;
        }
        if (board != null) {
            board.close();
        }
        board = new BombBoardGui(plugin, player, this);
        board.show();
        tell(currentId, "&7Te toca: &fpulsa una casilla&7. Bombas escondidas: &c" + bombCount());
        soundAll(Sound.BLOCK_ANVIL_LAND, 0.6f, 1.2f);
    }

    /** Un jugador destapa una casilla. */
    public void reveal(UUID playerId, int cell) {
        if (cell < 0 || cell >= size() || revealed.contains(cell)) {
            return;
        }
        UUID currentId = current();
        if (currentId == null || !currentId.equals(playerId)) {
            return;
        }
        revealed.add(cell);
        turnTicks = 0;

        if (bombs.contains(cell)) {
            alive.remove(playerId);
            soundAll(Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
            broadcastRaw("&c&lBOOM &8» &f" + playerName(playerId) + " &7destapo una bomba en la casilla &f"
                    + (cell + 1) + " &7y queda fuera con &6"
                    + plugin.economy().format(pot.amountOf(playerId)) + " &7en el bote.");
            if (board != null) {
                board.close();
                board = null;
            }
            order.remove(playerId);
            if (turnIndex >= order.size()) {
                turnIndex = 0;
            }
            if (alive.size() <= 1) {
                settle();
                return;
            }
            broadcastRaw("&7Bote: &6" + plugin.economy().format(pot.total())
                    + " &8| &7quedan &f" + alive.size());
            openBoard();
            return;
        }

        Player player = online(playerId);
        if (player != null) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.4f);
        }
        broadcastRaw("&8» &f" + playerName(playerId) + " &7destapo la casilla &f" + (cell + 1)
                + "&7 y sigue vivo. &8(" + revealed.size() + "/" + size() + ")");
        turnIndex++;
        if (turnIndex >= order.size()) {
            turnIndex = 0;
        }
        if (freeCells().isEmpty()) {
            settle();
            return;
        }
        openBoard();
    }

    private void settle() {
        if (board != null) {
            board.close();
            board = null;
        }
        if (alive.isEmpty()) {
            broadcastRaw("&cEl tablero se quedo sin supervivientes; el bote pasa a la casa.");
            pot.burn();
            endRound();
            return;
        }
        UUID winner = alive.iterator().next();
        double total = pot.total();
        pot.payAllTo(winner);
        broadcastRaw("&8&m        &r &6TABLERO DE BOMBAS &8&m        ");
        broadcastRaw("&aGana &f" + playerName(winner) + " &acon &6" + plugin.economy().format(total));
        soundAll(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.1f);
        Player winnerPlayer = online(winner);
        if (winnerPlayer != null) {
            winnerPlayer.showTitle(Title.title(Text.c("&a&lULTIMO EN PIE"),
                    Text.c("&f" + plugin.economy().format(total)), Title.Times.times(
                            java.time.Duration.ofMillis(200),
                            java.time.Duration.ofMillis(2500),
                            java.time.Duration.ofMillis(400))));
        }
        endRound();
    }

    @Override
    public void handleAction(Player player, String action, String[] args) {
        if ("destapar".equals(action) && args.length > 0) {
            try {
                reveal(player.getUniqueId(), Integer.parseInt(args[0]) - 1);
            } catch (NumberFormatException error) {
                message(player, "grupo.casilla-invalida");
            }
            return;
        }
        super.handleAction(player, action, args);
    }

    @Override
    protected void onQuitDuringRound(UUID playerId) {
        alive.remove(playerId);
        order.remove(playerId);
        if (turnIndex >= order.size()) {
            turnIndex = 0;
        }
        if (board != null) {
            board.close();
            board = null;
        }
        if (alive.size() <= 1 && !alive.isEmpty()) {
            settle();
        }
    }

    @Override
    protected void onRoundEnd() {
        if (board != null) {
            board.close();
            board = null;
        }
        bombs.clear();
        revealed.clear();
        order.clear();
        alive.clear();
        turnIndex = 0;
        turnTicks = 0;
        counting = false;
    }

    /** Tablero compartido que solo puede pulsar quien tiene el turno. */
    private static final class BombBoardGui extends Gui {

        private static final ItemStack FILLER = Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();

        private final BombBoardGame game;

        BombBoardGui(CasinoPlugin plugin, Player player, BombBoardGame game) {
            super(plugin, player, 6, "&8Tablero de Bombas");
            this.game = game;
        }

        @Override
        protected void render() {
            clearActions();
            fill(FILLER);

            set(4, Items.of(Material.GUNPOWDER)
                    .name("&6Tablero de bombas")
                    .lore(
                            "&7Bote: &6" + plugin.economy().format(game.pot.total()),
                            "&7Bombas: &c" + game.bombCount() + " &7en &f" + game.size() + " &7casillas",
                            "&7Destapadas: &f" + game.revealed.size(),
                            "&7Jugadores vivos: &f" + game.alive.size(),
                            "",
                            "&ePulsa una casilla para destaparla")
                    .glow(true)
                    .build());

            for (int cell = 0; cell < game.size() && cell < 45; cell++) {
                final int index = cell;
                int slot = 9 + (cell / 9) * 9 + (cell % 9);
                if (game.revealed.contains(cell)) {
                    boolean bomb = game.bombs.contains(cell);
                    set(slot, Items.of(bomb ? Material.TNT : Material.EMERALD)
                            .name(bomb ? "&cBomba" : "&aSegura")
                            .lore("&7Casilla " + (cell + 1))
                            .build());
                } else {
                    set(slot, Items.of(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                            .name("&7Casilla " + (cell + 1))
                            .lore("&7Pulsa para destapar")
                            .build(), e -> {
                        close();
                        game.reveal(player().getUniqueId(), index);
                    });
                }
            }

            set(49, Items.of(Material.CLOCK)
                    .name("&7Turno de &f" + game.playerName(game.current()))
                    .lore("&7Si no elige a tiempo, se destapa al azar.")
                    .build());
        }

        @Override
        public String sessionId() {
            return "tablero-bombas";
        }
    }
}
