package com.freebuff.casino.game;

import com.freebuff.casino.CasinoPlugin;
import com.freebuff.casino.economy.Wager;
import com.freebuff.casino.util.Text;
import java.time.Duration;
import java.util.function.DoubleConsumer;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import org.bukkit.entity.Player;

/**
 * Juego individual. La secuencia es siempre la misma: permisos, apuesta, ronda.
 * Las subclases solo escriben {@link #start}.
 */
public abstract class AbstractSoloGame extends AbstractGame {

    protected AbstractSoloGame(CasinoPlugin plugin, GameMeta meta) {
        super(plugin, meta);
    }

    @Override
    public final void open(Player player) {
        if (!enabled()) {
            message(player, "juegos.desactivado", "juego", name());
            return;
        }
        if (!player.hasPermission(permission())) {
            message(player, "general.sin-permiso");
            return;
        }
        if (plugin.sessions().busy(player.getUniqueId())) {
            message(player, "juegos.ya-jugando");
            return;
        }
        if (!plugin.economy().has(player.getUniqueId(), minBet())) {
            message(player, "economia.sin-saldo", "apuesta", plugin.economy().format(minBet()));
            return;
        }
        plugin.guis().openBetSelector(player, this, begin(player));
    }

    /** Codigo de confirmacion de apuesta listo para pasar al GUI. */
    protected final DoubleConsumer begin(Player player) {
        return bet -> start(player, bet);
    }

    /** Ejecuta la ronda. Debe cobrar la apuesta con {@link #stake}. */
    protected abstract void start(Player player, double bet);

    /**
     * Retira la apuesta del monedero.
     *
     * @return {@code null} si ya no hay saldo, en cuyo caso el jugador ya ha sido avisado.
     */
    public final Wager stake(Player player, double bet) {
        Wager wager = plugin.economy().stake(player, bet);
        if (wager == null) {
            message(player, "economia.sin-saldo", "apuesta", plugin.economy().format(bet));
        }
        return wager;
    }

    /** Mensaje de resultado reutilizable por todos los juegos. */
    public void showResult(Player player, double bet, double payout) {
        if (payout > bet) {
            message(player, "juegos.ganaste",
                    "apuesta", plugin.economy().format(bet),
                    "premio", plugin.economy().format(payout),
                    "beneficio", plugin.economy().format(payout - bet));
        } else if (payout == bet) {
            message(player, "juegos.empate", "apuesta", plugin.economy().format(bet));
        } else {
            message(player, "juegos.perdiste", "apuesta", plugin.economy().format(bet));
        }
    }

    /** Multiplicador que la casa puede pagar sin dejar los cofres vacios. */
    public double cappedMultiplier(double multiplier, double cap) {
        return Math.min(Math.max(0, multiplier), cap);
    }

    /** Ofrece repetir la partida con un boton de chat, sin pasar por el menu. */
    public void offerReplay(Player player) {
        if (!player.isOnline()) {
            return;
        }
        player.sendMessage(Text.c(plugin.messages().get("juegos.repetir-pregunta"))
                .append(Text.button(
                        plugin.messages().get("juegos.boton-repetir"),
                        "/casino jugar " + id(),
                        plugin.messages().get("juegos.boton-repetir-hover", "juego", name()))));
    }

    /** Encabezado de resultado con titulo en pantalla. */
    public void announceResult(Player player, boolean won, String detail) {
        String title = won ? plugin.messages().get("juegos.titulo-ganaste")
                : plugin.messages().get("juegos.titulo-perdiste");
        String subtitle = detail == null ? "" : detail;
        player.showTitle(Title.title(Text.c(title), Text.c(subtitle), Times.times(
                Duration.ofMillis(150), Duration.ofMillis(1500), Duration.ofMillis(300))));
    }
}
