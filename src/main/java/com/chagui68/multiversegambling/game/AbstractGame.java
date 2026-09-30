package com.chagui68.multiversegambling.game;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.economy.Wager;
import com.chagui68.multiversegambling.util.Text;
import java.time.Duration;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Shared base for every game: stakes, messages, sound and announcements. */
public abstract class AbstractGame implements Game {

    protected final MultiverseGamblingPlugin plugin;
    private final GameMeta meta;

    protected AbstractGame(MultiverseGamblingPlugin plugin, GameMeta meta) {
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

    // ---------------------------------------------------------------- localisation

    /** Game name in the language of the viewer; lang files may override {@code catalog.<id>.name}. */
    @Override
    public String displayName(CommandSender viewer) {
        return plugin.messages().gameName(viewer, id(), name());
    }

    /** Description in the language of the viewer, falling back to the built in one. */
    @Override
    public List<String> displayDescription(CommandSender viewer) {
        return plugin.messages().gameDescription(viewer, id(), description());
    }

    // ------------------------------------------------------------------ helpers

    // These helpers are public on purpose: the nested menus of each game (classes
    // inside another class) need them and are not subclasses of this one.

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
     * Settles a solo bet: pays out, records the statistics and announces the win when
     * the prize is worth mentioning.
     *
     * @param multiplier multiplier over what was staked; 0 or less means a loss
     * @return the money returned to the player
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

    /** Gives the stake back without counting it as a played round. */
    public double refund(Wager wager) {
        if (wager == null) {
            return 0;
        }
        wager.refund();
        return wager.amount();
    }

    /** Header line with the game name, in the language of the reader. */
    public String title(CommandSender viewer) {
        return plugin.messages().forSender(viewer, "panel.header", "game", displayName(viewer));
    }

    /** Panel label (item name, button or title) in the language of the reader. */
    public String label(CommandSender viewer, String key, Object... replacements) {
        return plugin.messages().forSender(viewer, key, replacements);
    }

    /** Panel lore block in the language of the reader. */
    public List<String> labelLore(CommandSender viewer, String key, Object... replacements) {
        return plugin.messages().loreFor(viewer, key, replacements);
    }

    /** On screen title with its subtitle, both built from language keys. */
    public void showTitle(Player player, String titleKey, String subtitleKey, Object... replacements) {
        if (player == null) {
            return;
        }
        player.showTitle(Title.title(
                plugin.messages().componentPlainFor(player, titleKey),
                plugin.messages().componentPlainFor(player, subtitleKey, replacements),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2500),
                        Duration.ofMillis(400))));
    }

    /** Action bar line built from a language key, in the language of the reader. */
    public void actionBarKey(Player player, String key, Object... replacements) {
        if (player == null) {
            return;
        }
        player.sendActionBar(plugin.messages().componentPlainFor(player, key, replacements));
    }
}
