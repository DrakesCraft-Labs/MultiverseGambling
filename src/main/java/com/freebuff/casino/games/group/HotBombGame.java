package com.freebuff.casino.games.group;

import com.freebuff.casino.CasinoPlugin;
import com.freebuff.casino.engine.Rng;
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
 * Bomba caliente.
 *
 * <p>La bomba va pasando de mano en mano y explota cuando menos te lo esperas. Quien
 * la tenga en ese momento se queda fuera y su dinero engrosa el bote, que se lleva
 * el ultimo en pie. La mecha se sortea con el generador verificable, asi que no se
 * puede saber de antemano cuanto va a durar.</p>
 */
public final class HotBombGame extends AbstractGroupGame {

    private final Set<UUID> alive = new LinkedHashSet<>();
    private UUID holder;
    private int fuseTicks;
    private int passTicks;

    public HotBombGame(CasinoPlugin plugin) {
        super(plugin, GameMeta.builder("bomba-caliente", "Bomba Caliente", GameCategory.GRUPO, Material.TNT)
                .desc("&7La bomba pasa de mano en mano.",
                        "&7Al que le pille con ella, fuera.",
                        "&7El ultimo en pie se lleva el bote.")
                .players(2, 12)
                .build());
    }

    @Override
    protected void onRoundStart() {
        timer = 0;
        alive.clear();
        alive.addAll(pot.participants());
        holder = pickHolder(null);
        fuseTicks = rollFuse();
        passTicks = 20;
        broadcastRaw(roundHeader());
        broadcastRaw("&7Bote: &6" + plugin.economy().format(pot.total())
                + " &8| &7jugadores: &f" + alive.size());
        broadcastRaw("&cLa bomba empieza en manos de &f" + playerName(holder) + "&c.");
        broadcastRaw("&7Mecha encendida: entre &f" + (int) plugin.config().hotBombMinSeconds()
                + " &7y &f" + (int) plugin.config().hotBombMaxSeconds() + " &7segundos.");
        soundAll(Sound.ENTITY_TNT_PRIMED, 0.8f, 1.0f);
    }

    private int rollFuse() {
        double min = plugin.config().hotBombMinSeconds();
        double max = plugin.config().hotBombMaxSeconds();
        double seconds = min + plugin.fair().roll(FairnessService.HOUSE) * (max - min);
        return Math.max(20, (int) (seconds * 20));
    }

    private UUID pickHolder(UUID except) {
        List<UUID> candidates = new ArrayList<>();
        for (UUID id : alive) {
            if (except == null || !id.equals(except)) {
                candidates.add(id);
            }
        }
        if (candidates.isEmpty()) {
            return except;
        }
        // Elegir al siguiente portador tambien es una tirada del casino.
        return candidates.get(plugin.fair().rollInt(FairnessService.HOUSE, candidates.size()));
    }

    @Override
    protected void tickRound() {
        timer++;
        if (alive.size() <= 1) {
            settle();
            return;
        }

        passTicks--;
        if (passTicks <= 0) {
            passTicks = Math.max(8, 20 - timer / 100);
            UUID next = pickHolder(holder);
            if (!next.equals(holder)) {
                UUID previous = holder;
                holder = next;
                tell(previous, "&7Pasaste la bomba a &f" + playerName(holder) + "&7.");
                Player target = online(holder);
                if (target != null) {
                    target.playSound(target.getLocation(), Sound.ENTITY_TNT_PRIMED, 0.9f, 1.4f);
                    target.showTitle(Title.title(Text.c("&c¡LA TIENES!"),
                            Text.c("&7Pasala rapido"), Title.Times.times(
                                    java.time.Duration.ofMillis(100),
                                    java.time.Duration.ofMillis(600),
                                    java.time.Duration.ofMillis(100))));
                }
            }
        }

        fuseTicks--;
        if (timer % 10 == 0) {
            Player current = online(holder);
            if (current != null) {
                // Solo el portador ve el aviso constante de que la lleva encima.
                current.sendActionBar(Text.c("&cBomba en tus manos &8| &7bote &6"
                        + plugin.economy().format(pot.total())));
            }
        }
        if (fuseTicks <= 0) {
            // Tirada verificable: decide si la mecha llega a su fin en este tick.
            if (plugin.fair().roll(FairnessService.HOUSE) < 0.25) {
                explode();
            } else {
                fuseTicks = 4;
            }
        }
    }

    private void explode() {
        UUID victim = holder;
        alive.remove(victim);
        soundAll(Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.9f);
        Player victimPlayer = online(victim);
        if (victimPlayer != null) {
            victimPlayer.showTitle(Title.title(Text.c("&c&lBOOM"),
                    Text.c("&7Estabas sosteniendola"), Title.Times.times(
                            java.time.Duration.ofMillis(100),
                            java.time.Duration.ofMillis(1400),
                            java.time.Duration.ofMillis(300))));
        }
        broadcastRaw("&c&lBOOM &8» &f" + playerName(victim)
                + " &7vuelo por los aires y su apuesta de &6"
                + plugin.economy().format(pot.amountOf(victim)) + " &7queda en el bote.");
        broadcastRaw("&7Bote ahora: &6" + plugin.economy().format(pot.total())
                + " &8| &7quedan &f" + alive.size());

        if (alive.size() <= 1) {
            settle();
            return;
        }
        holder = pickHolder(victim);
        fuseTicks = rollFuse();
        passTicks = 20;
        broadcastRaw("&7La bomba reaparece en manos de &f" + playerName(holder) + "&7.");
    }

    private void settle() {
        if (alive.isEmpty()) {
            // Nadie sobrevivio: el bote se queda para la casa.
            broadcastRaw("&cNadie sobrevivio; el bote pasa a la casa.");
            pot.burn();
            endRound();
            return;
        }
        UUID winner = alive.iterator().next();
        double total = pot.total();
        pot.payAllTo(winner);
        broadcastRaw("&8&m        &r &6BOMBA CALIENTE &8&m        ");
        broadcastRaw("&aGana &f" + playerName(winner) + " &acon &6"
                + plugin.economy().format(total) + " &adel bote.");
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
    protected void onQuitDuringRound(UUID playerId) {
        alive.remove(playerId);
        if (playerId.equals(holder) && !alive.isEmpty()) {
            holder = pickHolder(playerId);
        }
    }

    @Override
    protected void onRoundEnd() {
        alive.clear();
        holder = null;
        fuseTicks = 0;
        passTicks = 0;
    }
}
