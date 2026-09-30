package com.chagui68.multiversegambling;

import com.chagui68.multiversegambling.command.MultiverseGamblingCommand;
import com.chagui68.multiversegambling.config.MultiverseGamblingConfig;
import com.chagui68.multiversegambling.config.Messages;
import com.chagui68.multiversegambling.economy.EconomyManager;
import com.chagui68.multiversegambling.fair.FairnessService;
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
import com.chagui68.multiversegambling.listener.PlayerListener;
import com.chagui68.multiversegambling.session.SessionManager;
import com.chagui68.multiversegambling.stats.StatsStore;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Motor de azar y apuestas para Paper.
 *
 * <p>Todo el dinero pasa por {@link EconomyManager}, todo el azar jugable sale del
 * paquete {@code engine} (probado con tests) y todo el suspense pasa por el reloj
 * unico de {@link SessionManager}.</p>
 */
public final class MultiverseGamblingPlugin extends JavaPlugin {

    private MultiverseGamblingConfig config;
    private Messages messages;
    private EconomyManager economy;
    private FairnessService fair;
    private StatsStore stats;
    private SessionManager sessions;
    private GuiManager guis;
    private GameRegistry games;
    private BukkitTask autosave;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        config = new MultiverseGamblingConfig(this);
        messages = new Messages(this);
        economy = new EconomyManager(this);
        economy.setup();
        fair = new FairnessService(this);
        stats = new StatsStore(this);
        sessions = new SessionManager(this);
        guis = new GuiManager(this);
        games = new GameRegistry(this);

        registerGames();

        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        MultiverseGamblingCommand command = new MultiverseGamblingCommand(this);
        PluginCommand pluginCommand = getCommand("casino");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        } else {
            getLogger().severe("No se pudo registrar el comando /casino: revisa plugin.yml");
        }

        sessions.start();
        scheduleAutosave();

        long solo = games.byCategory(com.chagui68.multiversegambling.game.GameCategory.SOLO).size();
        long grupo = games.byCategory(com.chagui68.multiversegambling.game.GameCategory.GRUPO).size();
        getLogger().info("Catalogo cargado: " + solo + " juegos en solitario y " + grupo + " en grupo ("
                + games.enabled().size() + " activos).");
        getLogger().info("Azar verificable: " + (fair.enabled() ? "activado" : "desactivado")
                + " | secreto actual " + fair.serverSeedHash().substring(0, 16) + "...");
    }

    @Override
    public void onDisable() {
        if (games != null) {
            games.shutdownAll();
        }
        if (sessions != null) {
            sessions.shutdown();
        }
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
        getLogger().info("Plugin cerrado; se devolvio el dinero de las partidas a medias.");
    }

    /** El catalogo entero. Añadir un juego nuevo es añadir una linea aqui. */
    private void registerGames() {
        // --- En solitario ---
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

        // --- En grupo ---
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

    /** Recarga config y mensajes sin reiniciar la economia ni las partidas. */
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
}
