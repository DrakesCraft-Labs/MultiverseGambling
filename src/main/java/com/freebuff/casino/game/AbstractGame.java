package com.freebuff.casino.game;

import com.freebuff.casino.CasinoPlugin;
import com.freebuff.casino.economy.Wager;
import com.freebuff.casino.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** Base comun de todos los juegos: cobros, mensajes, sonido y anuncios. */
public abstract class AbstractGame implements Game {

    protected final CasinoPlugin plugin;
    private final GameMeta meta;

    protected AbstractGame(CasinoPlugin plugin, GameMeta meta) {
        this.plugin = plugin;
        this.meta = meta;
    }

    @Override
    public final GameMeta meta() {
        return meta;
    }

    @Override
    public boolean enabled() {
        return plugin.config().gameEnabled(id());
    }

    @Override
    public double minBet() {
        return plugin.config().minBet(id());
    }

    @Override
    public double maxBet() {
        return plugin.config().maxBet(id());
    }

    // -------------------------------------------------------------- utilidades

    // Estos ayudantes son publicos a proposito: los menus anidados de cada juego
    // (clases dentro de otra clase) los necesitan y no son subclases de esta.

    public void message(Player player, String key, Object... replacements) {
        plugin.messages().send(player, key, replacements);
    }

    public void info(Player player, String legacyText) {
        plugin.messages().sendRaw(player, legacyText);
    }

    public void actionBar(Player player, String legacyText) {
        player.sendActionBar(Text.c(legacyText));
    }

    public void sound(Player player, Sound sound, float volume, float pitch) {
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    public Component money(double amount) {
        return Text.c(plugin.economy().format(amount));
    }

    /**
     * Liquida una apuesta en solitario: paga, anota estadisticas y anuncia si el
     * premio es digno de mencion.
     *
     * @param multiplier multiplicador sobre lo apostado; 0 o menos es perder
     * @return el dinero devuelto al jugador
     */
    public double settle(Player player, Wager wager, double multiplier) {
        double payout = wager.amount() * Math.max(0, multiplier);
        if (payout <= 0) {
            wager.lose();
        } else {
            wager.payAbsolute(payout);
        }
        plugin.stats().record(player.getUniqueId(), id(), wager.amount(), payout);
        plugin.games().announceWin(player, wager.amount(), payout);
        return payout;
    }

    /** Devuelve la apuesta sin contar como jugada (cancelaciones y abandonos). */
    public double refund(Wager wager) {
        if (wager == null) {
            return 0;
        }
        wager.refund();
        return wager.amount();
    }

    /** Texto de cabecera con el nombre del juego. */
    public String title() {
        return "&8&m        &r &6" + name() + " &8&m        ";
    }
}
