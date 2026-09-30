package com.chagui68.multiversegambling.games.group;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.economy.Wager;
import com.chagui68.multiversegambling.game.AbstractGame;
import com.chagui68.multiversegambling.game.GameCategory;
import com.chagui68.multiversegambling.game.GameMeta;
import com.chagui68.multiversegambling.gui.Gui;
import com.chagui68.multiversegambling.util.Items;
import com.chagui68.multiversegambling.util.Text;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Duelo 1 contra 1.
 *
 * <p>Se reta a otro jugador por una cantidad exacta. Los dos ponen el mismo dinero
 * y una moneda decide quien se lo lleva todo. Si el retado no acepta a tiempo, el
 * retador recupera su dinero automaticamente: nadie se queda con nada en garantia.</p>
 */
public final class DuelGame extends AbstractGame {

    private final Map<UUID, Challenge> pending = new HashMap<>();

    /** Un reto esperando respuesta. */
    private static final class Challenge {
        final UUID challenger;
        final UUID target;
        final double amount;
        final Wager wager;
        int ticksLeft;

        Challenge(UUID challenger, UUID target, double amount, Wager wager, int ticksLeft) {
            this.challenger = challenger;
            this.target = target;
            this.amount = amount;
            this.wager = wager;
            this.ticksLeft = ticksLeft;
        }
    }

    public DuelGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("duelo", "Duelo 1v1", GameCategory.GRUPO, Material.IRON_SWORD)
                .desc("&7Reta a otro jugador por una cantidad.",
                        "&7Los dos ponen lo mismo y una moneda",
                        "&7decide quien se lo lleva todo.")
                .players(2, 2)
                .build());
        // Este juego se controla solo: no pasa por la sala de espera de los demas.
        plugin.sessions().register(id(), this::tick);
    }

    @Override
    public boolean ownsPlayer(UUID playerId) {
        Challenge challenge = pending.get(playerId);
        return challenge != null;
    }

    @Override
    public List<String> statusLore() {
        if (pending.isEmpty()) {
            return List.of("&7Sin retos pendientes");
        }
        return List.of("&eRetos pendientes: &f" + pending.size());
    }

    /** Abre el menu para elegir rival. */
    @Override
    public void open(Player player) {
        new RivalGui(plugin, player, this).show();
    }

    /** Reta a otro jugador. */
    public void challenge(Player challenger, Player target, double amount) {
        if (challenger.getUniqueId().equals(target.getUniqueId())) {
            message(challenger, "duelo.a-ti-mismo");
            return;
        }
        if (!plugin.config().gameEnabled(id())) {
            message(challenger, "juegos.desactivado", "juego", name());
            return;
        }
        if (pending.containsKey(challenger.getUniqueId())
                || pending.containsKey(target.getUniqueId())) {
            message(challenger, "duelo.ya-ocupado");
            return;
        }
        double stake = Math.max(minBet(), Math.min(maxBet(), amount));
        if (!plugin.economy().has(target.getUniqueId(), stake)) {
            message(challenger, "duelo.rival-sin-saldo", "jugador", target.getName());
            return;
        }
        Wager wager = plugin.economy().stake(challenger, stake);
        if (wager == null) {
            message(challenger, "economia.sin-saldo", "apuesta", plugin.economy().format(stake));
            return;
        }
        Challenge challenge = new Challenge(challenger.getUniqueId(), target.getUniqueId(), stake, wager,
                plugin.config().duelTimeoutSeconds() * 20);
        pending.put(target.getUniqueId(), challenge);

        message(challenger, "duelo.reto-enviado",
                "jugador", target.getName(), "cantidad", plugin.economy().format(stake));
        target.sendMessage(Text.c("&8» &f" + challenger.getName() + " &7te reta por &6"
                        + plugin.economy().format(stake) + "&7. Tienes &f"
                        + plugin.config().duelTimeoutSeconds() + "s&7.")
                .append(Text.c(" "))
                .append(Text.button("&a&lACEPTAR", "/casino accion aceptar", "&7Aceptar el duelo"))
                .append(Text.c(" "))
                .append(Text.button("&c&lRECHAZAR", "/casino accion rechazar", "&7Rechazar el duelo")));
        target.playSound(target.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.8f, 1.4f);
    }

    @Override
    public void handleAction(Player player, String action, String[] args) {
        switch (action) {
            case "aceptar" -> accept(player);
            case "rechazar" -> decline(player);
            default -> message(player, "grupo.accion-desconocida");
        }
    }

    private void accept(Player target) {
        Challenge challenge = pending.remove(target.getUniqueId());
        if (challenge == null) {
            message(target, "duelo.sin-reto");
            return;
        }
        Wager defenderWager = plugin.economy().stake(target, challenge.amount);
        if (defenderWager == null) {
            message(target, "economia.sin-saldo", "apuesta", plugin.economy().format(challenge.amount));
            refund(challenge.wager);
            Player challenger = online(challenge.challenger);
            if (challenger != null) {
                message(challenger, "duelo.reto-sin-fondos");
            }
            return;
        }

        Player challenger = online(challenge.challenger);
        // Tirada verificable: el ganador se decide aqui y con el mismo generador que todo lo demas.
        boolean challengerWins = plugin.fair().roll(challenge.challenger) < 0.5;
        // El duelo es jugador contra jugador: la casa solo se lleva la comision
        // que configure el servidor (por defecto ninguna).
        double total = challenge.amount * 2 * (1.0 - plugin.config().groupHouseCut());

        if (challengerWins) {
            challenge.wager.payAbsolute(total);
            defenderWager.lose();
        } else {
            defenderWager.payAbsolute(total);
            challenge.wager.lose();
        }
        plugin.stats().record(challenge.challenger, id(), challenge.amount,
                challengerWins ? total : 0);
        plugin.stats().record(target.getUniqueId(), id(), challenge.amount,
                challengerWins ? 0 : total);

        Player loser = challengerWins ? target : challenger;
        Player winner = challengerWins ? challenger : target;

        announceBoth(winner, loser, total);
        if (challenger != null) {
            info(challenger, title());
            info(challenger, (challengerWins ? "&aGanaste" : "&cPerdiste") + " &7el duelo contra &f"
                    + target.getName() + "&7 por &6" + plugin.economy().format(challenge.amount));
        }
        info(target, title());
        info(target, (challengerWins ? "&cPerdiste" : "&aGanaste") + " &7el duelo contra &f"
                + playerName(challenge.challenger) + "&7 por &6"
                + plugin.economy().format(challenge.amount));
    }

    private void announceBoth(Player winner, Player loser, double total) {
        if (winner != null) {
            winner.showTitle(Title.title(Text.c("&6&lGANASTE EL DUELO"),
                    Text.c("&f" + plugin.economy().format(total)), Title.Times.times(
                            java.time.Duration.ofMillis(150),
                            java.time.Duration.ofMillis(2000),
                            java.time.Duration.ofMillis(300))));
            winner.playSound(winner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        }
        if (loser != null) {
            loser.showTitle(Title.title(Text.c("&c&lPERDISTE"),
                    Text.c("&7La moneda no te acompano"), Title.Times.times(
                            java.time.Duration.ofMillis(150),
                            java.time.Duration.ofMillis(1600),
                            java.time.Duration.ofMillis(300))));
            loser.playSound(loser.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.9f);
        }
    }

    private void decline(Player target) {
        Challenge challenge = pending.remove(target.getUniqueId());
        if (challenge == null) {
            message(target, "duelo.sin-reto");
            return;
        }
        refund(challenge.wager);
        message(target, "duelo.rechazado");
        Player challenger = online(challenge.challenger);
        if (challenger != null) {
            message(challenger, "duelo.reto-rechazado-por", "jugador", target.getName());
        }
    }

    /** Reloj propio del duelo: expira los retos sin respuesta. */
    private void tick() {
        if (pending.isEmpty()) {
            return;
        }
        List<UUID> expired = new ArrayList<>();
        for (Map.Entry<UUID, Challenge> entry : pending.entrySet()) {
            Challenge challenge = entry.getValue();
            challenge.ticksLeft--;
            if (challenge.ticksLeft <= 0) {
                expired.add(entry.getKey());
            }
        }
        for (UUID target : expired) {
            Challenge challenge = pending.remove(target);
            if (challenge == null) {
                continue;
            }
            refund(challenge.wager);
            Player challenger = online(challenge.challenger);
            if (challenger != null) {
                message(challenger, "duelo.reto-expirado", "jugador", playerName(target));
            }
            Player targetPlayer = online(target);
            if (targetPlayer != null) {
                message(targetPlayer, "duelo.reto-caducado");
            }
        }
    }

    private String playerName(UUID playerId) {
        Player player = online(playerId);
        if (player != null) {
            return player.getName();
        }
        String name = plugin.getServer().getOfflinePlayer(playerId).getName();
        return name == null ? playerId.toString().substring(0, 8) : name;
    }

    private Player online(UUID playerId) {
        return plugin.getServer().getPlayer(playerId);
    }

    private static final class RivalGui extends Gui {

        private final DuelGame game;

        RivalGui(MultiverseGamblingPlugin plugin, Player player, DuelGame game) {
            super(plugin, player, 5, "&8Duelo &7· &6Elige rival");
            this.game = game;
        }

        @Override
        protected void render() {
            clearActions();
            fill(Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());

            set(4, Items.of(Material.IRON_SWORD)
                    .name("&6Duelo 1 contra 1")
                    .lore(
                            "&7Elige a un jugador y una cantidad.",
                            "&7El rival debe aceptar en &f"
                                    + plugin.config().duelTimeoutSeconds() + "s&7.",
                            "&7El ganador se lleva las dos apuestas.",
                            "",
                            "&7Si el rival no acepta, recuperas tu dinero.")
                    .glow(true)
                    .build());

            int slot = 10;
            for (Player target : plugin.getServer().getOnlinePlayers()) {
                if (target.getUniqueId().equals(player().getUniqueId())) {
                    continue;
                }
                if (slot > 34) {
                    break;
                }
                if (slot == 17 || slot == 26) {
                    slot = slot + 2;
                }
                double balance = plugin.economy().balance(target.getUniqueId());
                set(slot, Items.of(Material.PLAYER_HEAD)
                        .name("&f" + target.getName())
                        .lore(
                                "&7Saldo: &f" + plugin.economy().format(balance),
                                "",
                                "&ePulsa para retarle")
                        .build(), e -> {
                    close();
                    plugin.guis().openBetSelector(player(), game, amount ->
                            game.challenge(player(), target, amount));
                });
                slot++;
            }

            set(40, Items.of(Material.BARRIER)
                    .name("&cCerrar")
                    .build(), e -> close());
        }

        @Override
        public String sessionId() {
            return "duelo";
        }
    }
}
