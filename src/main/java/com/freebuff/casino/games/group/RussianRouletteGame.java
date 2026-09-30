package com.freebuff.casino.games.group;

import com.freebuff.casino.CasinoPlugin;
import com.freebuff.casino.fair.FairnessService;
import com.freebuff.casino.game.AbstractGroupGame;
import com.freebuff.casino.game.GameCategory;
import com.freebuff.casino.game.GameMeta;
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

/**
 * Ruleta rusa por turnos.
 *
 * <p>Cada jugador aprieta el gatillo cuando le toca. La probabilidad de que suene
 * el disparo es exactamente {@code balas / recamaras} en cada tirada, decidida por
 * el generador verificable, asi que la ronda es tan auditable como cualquier otra
 * apuesta del casino. El que cae deja su dinero en el bote del superviviente.</p>
 */
public final class RussianRouletteGame extends AbstractGroupGame {

    private static final int FIRST_DELAY_TICKS = 40;

    private final List<UUID> order = new ArrayList<>();
    private final Set<UUID> alive = new LinkedHashSet<>();
    private int turnIndex;
    private int turnTicks;
    private boolean started;

    public RussianRouletteGame(CasinoPlugin plugin) {
        super(plugin, GameMeta.builder("ruleta-rusa", "Ruleta Rusa", GameCategory.GRUPO, Material.FLINT_AND_STEEL)
                .desc("&7Por turnos, cada uno aprieta el gatillo.",
                        "&7Con &f1 bala en 6 recamaras&7, la suerte",
                        "&7decide quien queda en pie.")
                .players(2, 8)
                .build());
    }

    private int chambers() {
        return plugin.config().russianRouletteChambers();
    }

    private int bullets() {
        return Math.min(plugin.config().russianRouletteBullets(), chambers() - 1);
    }

    private UUID current() {
        if (turnIndex >= order.size()) {
            turnIndex = 0;
        }
        return order.get(turnIndex % Math.max(1, order.size()));
    }

    @Override
    protected void onRoundStart() {
        order.clear();
        order.addAll(pot.participants());
        alive.clear();
        alive.addAll(order);
        turnIndex = 0;
        turnTicks = 0;
        started = false;
        timer = 0;

        broadcastRaw(roundHeader());
        broadcastRaw("&7Bote: &6" + plugin.economy().format(pot.total()));
        broadcastRaw("&7Revolver: &f" + bullets() + " bala(s) &7en &f" + chambers()
                + " recamaras &8(&7" + Text.percent((double) bullets() / chambers())
                + " por tirada&8)");
        broadcastRaw("&7Turno de &f" + playerName(current()) + "&7. Tienes &f"
                + plugin.config().groupCountdownSeconds() + " &7segundos.");
        prompt();
    }

    @Override
    protected void tickRound() {
        timer++;
        if (timer < FIRST_DELAY_TICKS) {
            return;
        }
        if (!started) {
            started = true;
            return;
        }
        if (alive.size() <= 1) {
            settle();
            return;
        }
        turnTicks++;
        UUID currentId = current();
        Player player = online(currentId);
        if (player != null) {
            int left = Math.max(0, plugin.config().groupCountdownSeconds() - turnTicks / 20);
            player.sendActionBar(Text.c("&7Te toca &8| &f" + left
                    + "s &8| &7bote &6" + plugin.economy().format(pot.total())));
        }
        if (turnTicks % 20 == 0 && turnTicks / 20 > 0) {
            soundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, 1.5f);
        }
        if (turnTicks >= plugin.config().groupCountdownSeconds() * 20) {
            broadcastRaw("&7Se acabo el tiempo de &f" + playerName(currentId)
                    + "&7, el gatillo se aprieta solo.");
            pull(currentId);
        }
    }

    /** Aprieta el gatillo. */
    public void trigger(Player player, boolean fromChat) {
        if (alive.size() <= 1) {
            message(player, "grupo.ruleta-rusa-terminada");
            return;
        }
        UUID currentId = current();
        if (!currentId.equals(player.getUniqueId())) {
            message(player, "grupo.ruleta-rusa-no-es-tu-turno", "jugador", playerName(currentId));
            return;
        }
        pull(currentId);
    }

    private void pull(UUID shooter) {
        boolean fires = plugin.fair().rollInt(FairnessService.HOUSE, chambers()) < bullets();
        turnTicks = 0;
        Player player = online(shooter);

        if (!fires) {
            if (player != null) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 0.6f);
            }
            broadcastRaw("&8» &f" + playerName(shooter) + " &7aprieta el gatillo... &a&lclic&7.");
            advance();
            return;
        }

        alive.remove(shooter);
        soundAll(Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
        if (player != null) {
            player.showTitle(Title.title(Text.c("&c&lBANG"),
                    Text.c("&7Tu apuesta engorda el bote"), Title.Times.times(
                            java.time.Duration.ofMillis(100),
                            java.time.Duration.ofMillis(1400),
                            java.time.Duration.ofMillis(300))));
        }
        broadcastRaw("&c&lBANG &8» &f" + playerName(shooter)
                + " &7cae y sus &6" + plugin.economy().format(pot.amountOf(shooter))
                + " &7pasan al bote.");
        broadcastRaw("&7Bote: &6" + plugin.economy().format(pot.total())
                + " &8| &7quedan &f" + alive.size());

        if (alive.size() <= 1) {
            settle();
            return;
        }
        // El tambor se vuelve a girar y el revolver pasa al siguiente.
        order.remove(shooter);
        if (turnIndex >= order.size()) {
            turnIndex = 0;
        }
        prompt();
    }

    private void advance() {
        turnIndex++;
        if (turnIndex >= order.size()) {
            turnIndex = 0;
        }
        prompt();
    }

    private void prompt() {
        if (alive.size() <= 1) {
            return;
        }
        UUID currentId = current();
        Player player = online(currentId);
        if (player == null) {
            return;
        }
        player.sendMessage(Text.c("&8» &7Te toca: aprieta el gatillo cuando quieras.")
                .append(chatButton("&c&lAPRETAR EL GATILLO", "disparar",
                        "&7Probabilidad de disparo: "
                                + Text.percent((double) bullets() / chambers()))));
    }

    private void settle() {
        if (alive.isEmpty()) {
            broadcastRaw("&cNo quedo nadie en pie; el bote pasa a la casa.");
            pot.burn();
            endRound();
            return;
        }
        UUID winner = alive.iterator().next();
        double total = pot.total();
        pot.payAllTo(winner);
        broadcastRaw("&8&m        &r &6RULETA RUSA &8&m        ");
        broadcastRaw("&a&f" + playerName(winner) + " &aes el unico en pie y se lleva &6"
                + plugin.economy().format(total) + "&a.");
        soundAll(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.1f);
        Player winnerPlayer = online(winner);
        if (winnerPlayer != null) {
            winnerPlayer.showTitle(Title.title(Text.c("&a&lSOBREVIVISTE"),
                    Text.c("&f" + plugin.economy().format(total)), Title.Times.times(
                            java.time.Duration.ofMillis(200),
                            java.time.Duration.ofMillis(2500),
                            java.time.Duration.ofMillis(400))));
        }
        endRound();
    }

    @Override
    public void handleAction(Player player, String action, String[] args) {
        if ("disparar".equals(action)) {
            trigger(player, true);
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
        if (alive.size() <= 1 && !alive.isEmpty()) {
            settle();
        }
    }

    @Override
    protected void onRoundEnd() {
        order.clear();
        alive.clear();
        turnIndex = 0;
        turnTicks = 0;
        started = false;
    }
}
