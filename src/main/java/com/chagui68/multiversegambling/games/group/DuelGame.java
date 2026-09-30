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
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Duel, one against one.
 *
 * <p>A player challenges another one for an exact amount. Both stake the same money
 * and a single roll decides who takes it all. If the challenged player does not
 * accept in time the challenger is refunded automatically: nobody holds a deposit.</p>
 */
public final class DuelGame extends AbstractGame {

    private final Map<UUID, Challenge> pending = new HashMap<>();

    /**
     * A challenge waiting for an answer.
     */
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
        super(plugin, GameMeta.builder("duel", "Duel 1v1", GameCategory.GROUP, Material.IRON_SWORD)
                .desc("&7Challenge another player for an amount.",
                        "&7Both stake the same and one roll",
                        "&7decides who takes it all.")
                .players(2, 2)
                .build());
        // This game runs by itself: it never uses the shared waiting room.
        plugin.sessions().register(id(), this::tick);
    }

    @Override
    public boolean ownsPlayer(UUID playerId) {
        Challenge challenge = pending.get(playerId);
        return challenge != null;
    }

    @Override
    public List<String> statusLore(CommandSender viewer) {
        if (pending.isEmpty()) {
            return List.of(label(viewer, "panel.status.no-challenges"));
        }
        return List.of(label(viewer, "panel.status.pending-challenges", "amount", pending.size()));
    }

    /**
     * Opens the menu to pick a rival.
     */
    @Override
    public void open(Player player) {
        new RivalGui(plugin, player, this).show();
    }

    /**
     * Challenges another player.
     */
    public void challenge(Player challenger, Player target, double amount) {
        if (challenger.getUniqueId().equals(target.getUniqueId())) {
            message(challenger, "duel.self-duel");
            return;
        }
        if (!plugin.config().gameEnabled(id())) {
            message(challenger, "games.disabled", "game", name());
            return;
        }
        if (pending.containsKey(challenger.getUniqueId())
                || pending.containsKey(target.getUniqueId())) {
            message(challenger, "duel.already-busy");
            return;
        }
        double stake = Math.max(minBet(), Math.min(maxBet(), amount));
        if (!plugin.economy().has(target.getUniqueId(), stake)) {
            message(challenger, "duel.rival-cannot-afford", "player", target.getName());
            return;
        }
        Wager wager = plugin.economy().stake(challenger, stake);
        if (wager == null) {
            message(challenger, "economy.not-enough-money", "bet", plugin.economy().format(stake));
            return;
        }
        Challenge challenge = new Challenge(challenger.getUniqueId(), target.getUniqueId(), stake, wager,
                plugin.config().duelTimeoutSeconds() * 20);
        pending.put(target.getUniqueId(), challenge);

        message(challenger, "duel.challenge-sent",
                "player", target.getName(), "amount", plugin.economy().format(stake));
        target.sendMessage(Text.c(plugin.messages().forSender(target, "duel.challenge-received",
                        "player", challenger.getName(),
                        "amount", plugin.economy().format(stake),
                        "seconds", plugin.config().duelTimeoutSeconds()))
                .append(Text.c(" "))
                .append(Text.button(plugin.messages().forSender(target, "duel.accept"),
                        "/mvgam action accept", plugin.messages().forSender(target, "duel.accept-hover")))
                .append(Text.c(" "))
                .append(Text.button(plugin.messages().forSender(target, "duel.decline"),
                        "/mvgam action decline", plugin.messages().forSender(target, "duel.decline-hover"))));
        target.playSound(target.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.8f, 1.4f);
    }

    @Override
    public void handleAction(Player player, String action, String[] args) {
        switch (action) {
            case "accept" -> accept(player);
            case "decline" -> decline(player);
            default -> message(player, "group.unknown-action");
        }
    }

    private void accept(Player target) {
        Challenge challenge = pending.remove(target.getUniqueId());
        if (challenge == null) {
            message(target, "duel.no-challenge");
            return;
        }
        Wager defenderWager = plugin.economy().stake(target, challenge.amount);
        if (defenderWager == null) {
            message(target, "economy.not-enough-money", "bet", plugin.economy().format(challenge.amount));
            refund(challenge.wager);
            Player challenger = online(challenge.challenger);
            if (challenger != null) {
                message(challenger, "duel.challenge-no-funds");
            }
            return;
        }

        Player challenger = online(challenge.challenger);
        // Provably fair roll: the winner is decided here with the same generator as everything else.
        boolean challengerWins = plugin.fair().roll(challenge.challenger) < 0.5;
        // A duel is player against player, so the house only keeps the commission
        // the server configures (none by default).
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
            info(challenger, title(challenger));
            message(challenger, challengerWins ? "duel.won-against" : "duel.lost-against",
                    "player", target.getName(),
                    "amount", plugin.economy().format(challenge.amount));
        }
        info(target, title(target));
        message(target, challengerWins ? "duel.lost-against" : "duel.won-against",
                "player", playerName(challenge.challenger),
                "amount", plugin.economy().format(challenge.amount));
    }

    private void announceBoth(Player winner, Player loser, double total) {
        if (winner != null) {
            winner.showTitle(Title.title(
                    Text.c(plugin.messages().forSender(winner, "duel.win-title")),
                    Text.c("&f" + plugin.economy().format(total)), Title.Times.times(
                            java.time.Duration.ofMillis(150),
                            java.time.Duration.ofMillis(2000),
                            java.time.Duration.ofMillis(300))));
            winner.playSound(winner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        }
        if (loser != null) {
            loser.showTitle(Title.title(
                    Text.c(plugin.messages().forSender(loser, "duel.lose-title")),
                    Text.c(plugin.messages().forSender(loser, "duel.lose-subtitle")), Title.Times.times(
                            java.time.Duration.ofMillis(150),
                            java.time.Duration.ofMillis(1600),
                            java.time.Duration.ofMillis(300))));
            loser.playSound(loser.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.9f);
        }
    }

    private void decline(Player target) {
        Challenge challenge = pending.remove(target.getUniqueId());
        if (challenge == null) {
            message(target, "duel.no-challenge");
            return;
        }
        refund(challenge.wager);
        message(target, "duel.declined");
        Player challenger = online(challenge.challenger);
        if (challenger != null) {
            message(challenger, "duel.challenge-declined", "player", target.getName());
        }
    }

    /**
     * The duel keeps its own clock: unanswered challenges expire.
     */
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
                message(challenger, "duel.challenge-expired", "player", playerName(target));
            }
            Player targetPlayer = online(target);
            if (targetPlayer != null) {
                message(targetPlayer, "duel.challenge-timed-out");
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

    private final class RivalGui extends Gui {

        private final DuelGame game;

        RivalGui(MultiverseGamblingPlugin plugin, Player player, DuelGame game) {
            super(plugin, player, 5, plugin.messages().forSender(player, "panel.duel.title",
                    "game", displayName(player)));
            this.game = game;
        }

        @Override
        protected void render() {
            clearActions();
            fill(Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());

            set(4, Items.of(Material.IRON_SWORD)
                    .name(label(player(), "panel.duel.info"))
                    .lore(labelLore(player(), "panel.duel.info-lore",
                            "seconds", plugin.config().duelTimeoutSeconds()))
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
                        .name(label(player(), "panel.duel.player", "player", target.getName()))
                        .lore(labelLore(player(), "panel.duel.player-lore",
                                "balance", plugin.economy().format(balance)))
                        .build(), e -> {
                    close();
                    plugin.guis().openBetSelector(player(), game, amount ->
                            game.challenge(player(), target, amount));
                });
                slot++;
            }

            set(40, Items.of(Material.BARRIER)
                    .name(label(player(), "panel.common.close"))
                    .build(), e -> close());
        }

        @Override
        public String sessionId() {
            return "duel";
        }
    }
}
