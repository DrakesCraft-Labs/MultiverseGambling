package com.chagui68.multiversegambling.game;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.economy.Pot;
import com.chagui68.multiversegambling.util.Text;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Juego en grupo con rondas encadenadas.
 *
 * <p>El ciclo lo lleva esta clase y es siempre el mismo, para que ningun juego
 * pueda saltarse el cobro ni la devolucion del dinero:</p>
 * <ol>
 *   <li>{@code ESPERA}: entra gente y apuesta. Al llegar al minimo se abre la veda.</li>
 *   <li>{@code APUESTAS}: cuenta atras visible; el que no apueste se queda fuera.</li>
 *   <li>{@code EN_JUEGO}: la subclase dirige la ronda y llama a {@link #endRound()}.</li>
 * </ol>
 *
 * <p>Los jugadores que se desconectan durante una ronda no recuperan su dinero: su
 * apuesta se queda en el bote y la pueden ganar los demas. Si se van antes de
 * empezar, se les devuelve integra.</p>
 */
public abstract class AbstractGroupGame extends AbstractGame {

    public enum Phase {
        ESPERA, APUESTAS, EN_JUEGO
    }

    protected final Pot pot;
    /** Jugadores apuntados para la proxima ronda que aun no han apostado. */
    protected final Set<UUID> waiting = new LinkedHashSet<>();
    /** Jugadores que se fueron en mitad de la ronda: siguen en el bote, pero no ganan. */
    protected final Set<UUID> gone = new LinkedHashSet<>();

    protected Phase phase = Phase.ESPERA;
    protected int timer;
    protected int round;

    protected AbstractGroupGame(MultiverseGamblingPlugin plugin, GameMeta meta) {
        super(plugin, meta);
        this.pot = new Pot(plugin.economy());
    }

    // ------------------------------------------------------------------ entrada

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
        UUID id = player.getUniqueId();

        if (phase == Phase.EN_JUEGO) {
            if (waiting.add(id)) {
                message(player, "grupo.apuntado-proxima", "juego", name());
            } else {
                message(player, "grupo.ya-apuntado");
            }
            return;
        }

        int seated = pot.size() + waiting.size();
        if (maxPlayers() > 0 && seated >= maxPlayers() && !pot.contains(id) && !waiting.contains(id)) {
            message(player, "grupo.lleno", "maximo", maxPlayers());
            return;
        }

        plugin.guis().openBetSelector(player, this, bet -> placeBet(player, bet));
    }

    /** Registra (o reemplaza) la apuesta del jugador. */
    protected final void placeBet(Player player, double amount) {
        if (phase == Phase.EN_JUEGO) {
            message(player, "grupo.ronda-en-curso");
            return;
        }
        UUID id = player.getUniqueId();
        if (!pot.add(player, amount)) {
            message(player, "economia.sin-saldo", "apuesta", plugin.economy().format(amount));
            return;
        }
        waiting.remove(id);
        gone.remove(id);
        message(player, "grupo.apuesta-registrada",
                "cantidad", plugin.economy().format(amount), "juego", name());
        onBetPlaced(player, amount);
        broadcastLobby();
        if (phase == Phase.ESPERA && pot.size() >= minPlayers()) {
            startBettingWindow();
        }
    }

    // -------------------------------------------------------------- ciclo de vida

    /** Llamado una vez por tick por el registro de juegos. */
    public final void tick() {
        switch (phase) {
            case ESPERA -> {
                if (pot.size() >= minPlayers()) {
                    startBettingWindow();
                }
            }
            case APUESTAS -> {
                if (pot.size() < minPlayers()) {
                    phase = Phase.ESPERA;
                    timer = 0;
                    broadcast("grupo.ronda-cancelada", "minimo", minPlayers());
                    return;
                }
                timer--;
                if (timer <= 0) {
                    beginRound();
                } else if (timer <= 5) {
                    broadcast("grupo.cuenta-atras", "segundos", timer);
                    tickSound();
                } else if (timer % 10 == 0) {
                    broadcast("grupo.tiempo-restante", "segundos", timer);
                }
            }
            case EN_JUEGO -> tickRound();
        }
    }

    private void startBettingWindow() {
        phase = Phase.APUESTAS;
        timer = plugin.config().groupBettingSeconds();
        broadcast("grupo.apuestas-abiertas", "segundos", timer);
    }

    private void beginRound() {
        round++;
        phase = Phase.EN_JUEGO;
        timer = 0;
        broadcast("grupo.ronda-empezando", "ronda", round);
        onRoundStart();
    }

    /**
     * Dirige la ronda. La subclase decide cuando termina y debe llamar a
     * {@link #endRound()} despues de haber repartido el bote.
     */
    protected abstract void tickRound();

    /** Prepara el estado de una ronda nueva. */
    protected abstract void onRoundStart();

    /** Limpia el estado al terminar una ronda. */
    protected void onRoundEnd() {
    }

    /** Aviso opcional cuando alguien apuesta. */
    protected void onBetPlaced(Player player, double amount) {
    }

    /** Aviso cuando alguien se va en mitad de la ronda. */
    protected void onQuitDuringRound(UUID playerId) {
    }

    /**
     * Accion pedida desde un boton del chat, por ejemplo
     * {@code /casino accion disparar}. Es lo que permite jugar sin abrir menus.
     */
    @Override
    public void handleAction(Player player, String action, String[] args) {
        message(player, "grupo.accion-desconocida");
    }

    @Override
    public final boolean ownsPlayer(UUID playerId) {
        return contains(playerId);
    }

    /** @return true si el jugador esta apostando en este juego ahora mismo. */
    public final boolean contains(UUID playerId) {
        return pot.contains(playerId) || waiting.contains(playerId);
    }

    /** Etiqueta del boton de chat listo para enviar. */
    protected final Component chatButton(String label, String action, String hover) {
        return Text.button(label, "/casino accion " + action, hover);
    }

    /** Cierra la ronda: devuelve lo que no se haya liquidado y vuelve a la espera. */
    protected final void endRound() {
        pot.refundAll();
        gone.clear();
        phase = Phase.ESPERA;
        timer = 0;
        onRoundEnd();
        broadcast("grupo.ronda-terminada");
        promptWaiting();
    }

    /** Anula la ronda en curso y devuelve todo el dinero. */
    public final void abortRound() {
        if (phase == Phase.EN_JUEGO) {
            broadcast("grupo.ronda-anulada");
        }
        pot.refundAll();
        waiting.clear();
        gone.clear();
        phase = Phase.ESPERA;
        timer = 0;
        onRoundEnd();
    }

    /** Reabre el selector de apuesta a quien esperaba su turno. */
    private void promptWaiting() {
        for (UUID id : new ArrayList<>(waiting)) {
            Player player = plugin.getServer().getPlayer(id);
            if (player == null || !player.isOnline()) {
                waiting.remove(id);
                continue;
            }
            message(player, "grupo.tu-turno-de-apostar", "juego", name());
        }
    }

    @Override
    public void shutdown() {
        abortRound();
    }

    // ------------------------------------------------------------------ salidas

    /** Gestiona la desconexion de un jugador. */
    public final void handleQuit(UUID playerId) {
        waiting.remove(playerId);
        if (!pot.contains(playerId)) {
            gone.remove(playerId);
            return;
        }
        if (phase == Phase.EN_JUEGO) {
            // Su apuesta sigue en el bote pero ya no puede ganarla.
            gone.add(playerId);
            onQuitDuringRound(playerId);
        } else {
            pot.remove(playerId);
            gone.remove(playerId);
            broadcastLobby();
        }
    }

    @Override
    public int activePlayers() {
        return pot.size() + waiting.size();
    }

    @Override
    public List<String> statusLore() {
        List<String> lore = new ArrayList<>();
        if (phase == Phase.EN_JUEGO) {
            lore.add("&eRonda " + round + " en curso");
        } else if (phase == Phase.APUESTAS) {
            lore.add("&aApuestas abiertas &7(" + timer + "s)");
        } else {
            lore.add("&7Esperando jugadores");
        }
        lore.add("&7En sala: &f" + activePlayers() + "&7/&f" + maxPlayers());
        lore.add("&7Bote: &6" + plugin.economy().shortFormat(pot.total()));
        lore.add("&7Minimo para empezar: &f" + minPlayers());
        return lore;
    }

    // --------------------------------------------------------------- utilidades

    protected final boolean isGone(UUID playerId) {
        return gone.contains(playerId);
    }

    /** Participantes que siguen presentes y por tanto pueden ganar. */
    protected final List<UUID> contenders() {
        List<UUID> out = new ArrayList<>();
        for (UUID id : pot.participants()) {
            if (!gone.contains(id) && plugin.getServer().getPlayer(id) != null) {
                out.add(id);
            }
        }
        return out;
    }

    protected final Player online(UUID playerId) {
        return plugin.getServer().getPlayer(playerId);
    }

    protected final String playerName(UUID playerId) {
        Player player = online(playerId);
        if (player != null) {
            return player.getName();
        }
        String name = plugin.getServer().getOfflinePlayer(playerId).getName();
        return name == null ? playerId.toString().substring(0, 8) : name;
    }

    protected final void broadcast(String key, Object... replacements) {
        Component component = plugin.messages().component(key, replacements);
        for (UUID id : audience()) {
            Player player = online(id);
            if (player != null) {
                player.sendMessage(component);
            }
        }
    }

    /** Anuncia sin prefijo, para texto ya compuesto. */
    /** Envia un texto ya compuesto a un jugador concreto de la sala. */
    protected final void tell(UUID playerId, String legacyText) {
        tell(playerId, Text.c(legacyText));
    }

    /** Envia un componente (util para textos con botones) a un jugador de la sala. */
    protected final void tell(UUID playerId, Component component) {
        Player player = online(playerId);
        if (player != null) {
            player.sendMessage(component);
        }
    }

    protected final void broadcastRaw(String legacyText) {
        Component component = Text.c(legacyText);
        for (UUID id : audience()) {
            Player player = online(id);
            if (player != null) {
                player.sendMessage(component);
            }
        }
    }

    protected final void actionBarAll(String legacyText) {
        Component component = Text.c(legacyText);
        for (UUID id : audience()) {
            Player player = online(id);
            if (player != null) {
                player.sendActionBar(component);
            }
        }
    }

    protected final void soundAll(Sound sound, float volume, float pitch) {
        for (UUID id : audience()) {
            Player player = online(id);
            if (player != null) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        }
    }

    private Set<UUID> audience() {
        Set<UUID> ids = new LinkedHashSet<>(pot.participants());
        ids.addAll(waiting);
        return ids;
    }

    protected final void broadcastLobby() {
        if (phase == Phase.ESPERA && pot.size() < minPlayers()) {
            broadcast("grupo.esperando-jugadores",
                    "actual", pot.size(), "minimo", minPlayers());
        }
    }

    private void tickSound() {
        soundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.4f);
    }

    /** Texto de cabecera de ronda. */
    protected final String roundHeader() {
        return "&8&m     &r &6" + name() + " &7· ronda &f" + round + " &8&m     ";
    }
}
