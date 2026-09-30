package com.chagui68.multiversegambling;

import com.chagui68.multiversegambling.command.MultiverseGamblingCommand;
import com.chagui68.multiversegambling.config.Messages;
import com.chagui68.multiversegambling.config.MultiverseGamblingConfig;
import com.chagui68.multiversegambling.economy.EconomyManager;
import com.chagui68.multiversegambling.fair.FairnessService;
import com.chagui68.multiversegambling.game.GameCategory;
import com.chagui68.multiversegambling.game.GameRegistry;
import com.chagui68.multiversegambling.games.group.BombBoardGame;
import com.chagui68.multiversegambling.games.group.ColorRouletteGame;
import com.chagui68.multiversegambling.games.group.DicePokerGame;
import com.chagui68.multiversegambling.games.group.DuelGame;
import com.chagui68.multiversegambling.games.group.HorseRaceGame;
import com.chagui68.multiversegambling.games.group.HotBombGame;
import com.chagui68.multiversegambling.games.group.JackpotGame;
import com.chagui68.multiversegambling.games.group.RaffleGame;
import com.chagui68.multiversegambling.games.group.RussianRouletteGame;
import com.chagui68.multiversegambling.games.solo.BlackjackGame;
import com.chagui68.multiversegambling.games.solo.ClassicRouletteGame;
import com.chagui68.multiversegambling.games.solo.CoinFlipGame;
import com.chagui68.multiversegambling.games.solo.CrashGame;
import com.chagui68.multiversegambling.games.solo.DiceGame;
import com.chagui68.multiversegambling.games.solo.HighLowGame;
import com.chagui68.multiversegambling.games.solo.LuckyWheelGame;
import com.chagui68.multiversegambling.games.solo.MinesGame;
import com.chagui68.multiversegambling.games.solo.PlinkoGame;
import com.chagui68.multiversegambling.games.solo.ScratchCardGame;
import com.chagui68.multiversegambling.games.solo.SlotsGame;
import com.chagui68.multiversegambling.games.solo.TowersGame;
import com.chagui68.multiversegambling.gui.GuiListener;
import com.chagui68.multiversegambling.gui.GuiManager;
import com.chagui68.multiversegambling.i18n.LanguageStore;
import com.chagui68.multiversegambling.listener.PlayerListener;
import com.chagui68.multiversegambling.session.SessionManager;
import com.chagui68.multiversegambling.stats.StatsStore;
import com.chagui68.multiversegambling.world.CasinoWorldManager;
import com.chagui68.multiversegambling.world.anim.ArenaShow;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Chance and betting engine for Paper.
 *
 * <p>All the money flows through {@link EconomyManager}, every playable roll comes
 * from the {@code engine} package (pinned by unit tests) and all the suspense runs
 * on the single clock of {@link SessionManager}. Text is resolved per player by
 * {@link Messages}, so the whole plugin can be switched between languages in game.</p>
 */
public final class MultiverseGamblingPlugin extends JavaPlugin {

    private MultiverseGamblingConfig config;
    private Messages messages;
    private LanguageStore languages;
    private EconomyManager economy;
    private FairnessService fair;
    private StatsStore stats;
    private SessionManager sessions;
    private GuiManager guis;
    private GameRegistry games;
    private CasinoWorldManager world;
    private BukkitTask autosave;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        config = new MultiverseGamblingConfig(this);
        messages = new Messages(this);
        languages = new LanguageStore(this);
        economy = new EconomyManager(this);
        economy.setup();
        fair = new FairnessService(this);
        stats = new StatsStore(this);
        sessions = new SessionManager(this);
        guis = new GuiManager(this);
        games = new GameRegistry(this);

        registerGames();

        world = new CasinoWorldManager(this);
        world.setup();

        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        MultiverseGamblingCommand command = new MultiverseGamblingCommand(this);
        PluginCommand pluginCommand = getCommand("mvgam");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        } else {
            getLogger().severe("Could not register /mvgam; check plugin.yml");
        }

        sessions.start();
        scheduleAutosave();

        long solo = games.byCategory(GameCategory.SOLO).size();
        long group = games.byCategory(GameCategory.GROUP).size();
        getLogger().info("Catalogue loaded: " + solo + " solo games and " + group
                + " group games (" + games.enabled().size() + " enabled).");
        getLogger().info("Provably fair rolls: " + (fair.enabled() ? "enabled" : "disabled")
                + " | current secret " + fair.serverSeedHash().substring(0, 16) + "...");
        getLogger().info("Languages available: " + messages.localeList()
                + " | default " + messages.defaultLocale() + ".");
    }

    @Override
    public void onDisable() {
        if (games != null) {
            games.shutdownAll();
        }
        if (sessions != null) {
            sessions.shutdown();
        }
        // Shows whose scenery was waiting to be taken down would lose their delayed
        // cleanup with the scheduler, so they are cleared here instead.
        ArenaShow.clearAll();
        if (autosave != null) {
            autosave.cancel();
        }
        if (economy != null) {
            economy.save();
        }
        if (stats != null) {
            stats.save();
        }
        if (fair != null) {
            fair.save();
        }
        if (languages != null) {
            languages.save();
        }
        getLogger().info("Plugin disabled; money from unfinished games was refunded.");
    }

    /**
     * The whole catalogue. Adding a game is adding one line here.
     */
    private void registerGames() {
        // --- Solo ---
        games.register(new ClassicRouletteGame(this));
        games.register(new SlotsGame(this));
        games.register(new CrashGame(this));
        games.register(new MinesGame(this));
        games.register(new TowersGame(this));
        games.register(new BlackjackGame(this));
        games.register(new HighLowGame(this));
        games.register(new DiceGame(this));
        games.register(new PlinkoGame(this));
        games.register(new LuckyWheelGame(this));
        games.register(new ScratchCardGame(this));
        games.register(new CoinFlipGame(this));

        // --- Group ---
        games.register(new ColorRouletteGame(this));
        games.register(new JackpotGame(this));
        games.register(new HotBombGame(this));
        games.register(new BombBoardGame(this));
        games.register(new RussianRouletteGame(this));
        games.register(new HorseRaceGame(this));
        games.register(new DuelGame(this));
        games.register(new RaffleGame(this));
        games.register(new DicePokerGame(this));
    }

    private void scheduleAutosave() {
        long ticks = Math.max(1, config.statsSaveMinutes()) * 60L * 20L;
        autosave = getServer().getScheduler().runTaskTimer(this, () -> {
            stats.saveAsync();
            economy.saveAsync();
            fair.saveAsync();
        }, ticks, ticks);
    }

    /**
     * Reloads config, messages and languages without touching balances or live games.
     */
    public void reloadAll() {
        reloadConfig();
        config.reload();
        messages.reload();
        economy.setup();
        if (autosave != null) {
            autosave.cancel();
        }
        scheduleAutosave();
    }

    public MultiverseGamblingConfig config() {
        return config;
    }

    public Messages messages() {
        return messages;
    }

    public LanguageStore languages() {
        return languages;
    }

    public EconomyManager economy() {
        return economy;
    }

    public FairnessService fair() {
        return fair;
    }

    public StatsStore stats() {
        return stats;
    }

    public SessionManager sessions() {
        return sessions;
    }

    public GuiManager guis() {
        return guis;
    }

    public GameRegistry games() {
        return games;
    }

    public CasinoWorldManager world() {
        return world;
    }
}
