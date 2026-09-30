package com.chagui68.multiversegambling.config;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;

/** Typed access to config.yml. Every value has a sensible default. */
public final class MultiverseGamblingConfig {

    private final MultiverseGamblingPlugin plugin;
    private FileConfiguration cfg;

    public MultiverseGamblingConfig(MultiverseGamblingPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        this.cfg = plugin.getConfig();
    }

    private double ranged(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private int ranged(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // -------------------------------------------------------------------- economy

    public String currencyName() {
        return cfg.getString("economy.currency", "coins");
    }

    public String currencyFormat() {
        return cfg.getString("economy.format", "&6{amount} &7{currency}");
    }

    public double startingBalance() {
        return Math.max(0, cfg.getDouble("economy.starting-balance", 1000.0));
    }

    /** auto, internal or vault. */
    public String economyProvider() {
        return cfg.getString("economy.provider", "auto");
    }

    // --------------------------------------------------------------------- rules

    /** Default house edge of every game. */
    public double houseEdge() {
        return ranged(cfg.getDouble("game.house-edge", 0.02), 0.0, 0.5);
    }

    public double minBet() {
        return Math.max(0.01, cfg.getDouble("game.min-bet", 10.0));
    }

    public double maxBet() {
        return Math.max(minBet(), cfg.getDouble("game.max-bet", 100000.0));
    }

    public double minBet(String gameId) {
        return Math.max(0.01, cfg.getDouble("games." + gameId + ".min-bet", minBet()));
    }

    public double maxBet(String gameId) {
        return Math.max(minBet(gameId), cfg.getDouble("games." + gameId + ".max-bet", maxBet()));
    }

    public boolean gameEnabled(String gameId) {
        return cfg.getBoolean("games." + gameId + ".enabled", true);
    }

    public boolean announceWins() {
        return cfg.getBoolean("game.announce-wins", true);
    }

    public double announceThreshold() {
        return cfg.getDouble("game.announce-threshold", 50000.0);
    }

    // ------------------------------------------------------------------ language

    /** Language used when a player has no preference of their own. */
    public String defaultLanguage() {
        return cfg.getString("language.default", "en");
    }

    /** When true a player who never picked a language follows their Minecraft client. */
    public boolean languageFollowClient() {
        return cfg.getBoolean("language.follow-client", true);
    }

    // --------------------------------------------------------------- solo games

    public boolean rouletteAmerican() {
        return cfg.getBoolean("games.roulette.american", false);
    }

    public int rouletteSpinTicks() {
        return Math.max(20, cfg.getInt("games.roulette.spin-ticks", 60));
    }

    public int slotsSpinTicks() {
        return Math.max(10, cfg.getInt("games.slots.spin-ticks", 40));
    }

    public int minesTiles() {
        return ranged(cfg.getInt("games.mines.tiles", 25), 4, 54);
    }

    public int minesDefaultMines() {
        return Math.max(1, cfg.getInt("games.mines.default-mines", 3));
    }

    public int minesMaxMines() {
        return Math.max(1, cfg.getInt("games.mines.max-mines", 24));
    }

    public double crashDoubleEverySeconds() {
        return Math.max(1, cfg.getDouble("games.crash.double-every-seconds", 10.0));
    }

    public double crashMaxMultiplier() {
        return Math.max(1.5, cfg.getDouble("games.crash.max-multiplier", 1000.0));
    }

    public int crashCountdownSeconds() {
        return ranged(cfg.getInt("games.crash.countdown", 5), 1, 30);
    }

    public int plinkoRows() {
        return ranged(cfg.getInt("games.plinko.rows", 12), 6, 20);
    }

    public double plinkoMaxMultiplier() {
        return Math.max(1, cfg.getDouble("games.plinko.max-multiplier", 500.0));
    }

    public int highLowMaxChain() {
        return ranged(cfg.getInt("games.high-low.max-steps", 5), 1, 20);
    }

    public int towersLevels() {
        return ranged(cfg.getInt("games.towers.levels", 9), 1, 25);
    }

    public int towersTiles() {
        return ranged(cfg.getInt("games.towers.tiles", 4), 2, 9);
    }

    public int towersBombs() {
        return ranged(cfg.getInt("games.towers.bombs", 1), 1, towersTiles() - 1);
    }

    public int blackjackDecks() {
        return ranged(cfg.getInt("games.blackjack.decks", 6), 1, 8);
    }

    public boolean blackjackHitSoft17() {
        return cfg.getBoolean("games.blackjack.dealer-hits-soft-17", false);
    }

    public int scratchPicks() {
        return ranged(cfg.getInt("games.scratch.picks", 3), 1, 9);
    }

    public List<Double> luckyWheelSegments() {
        List<Double> configured = cfg.getDoubleList("games.lucky-wheel.segments");
        if (configured.isEmpty()) {
            return List.of(0.0, 1.5, 0.0, 2.0, 0.0, 3.0, 0.0, 1.5, 0.0, 5.0, 0.0, 20.0);
        }
        return configured;
    }

    // -------------------------------------------------------------- group games

    public int groupBettingSeconds() {
        return ranged(cfg.getInt("group.betting-seconds", 20), 3, 300);
    }

    public int groupMaxPlayers() {
        return ranged(cfg.getInt("group.max-players", 24), 2, 100);
    }

    public int groupCountdownSeconds() {
        return ranged(cfg.getInt("group.countdown", 5), 1, 30);
    }

    public double groupHouseCut() {
        return ranged(cfg.getDouble("group.house-commission", 0.0), 0.0, 0.5);
    }

    public int bombBoardSize() {
        return ranged(cfg.getInt("group.bomb-board.tiles", 36), 9, 54);
    }

    public int bombBoardBombs() {
        return Math.max(1, cfg.getInt("group.bomb-board.bombs", 4));
    }

    public int bombBoardTurnSeconds() {
        return ranged(cfg.getInt("group.bomb-board.seconds-per-turn", 10), 3, 60);
    }

    public double hotBombMinSeconds() {
        return Math.max(1, cfg.getDouble("group.hot-bomb.min-seconds", 5.0));
    }

    public double hotBombMaxSeconds() {
        return Math.max(hotBombMinSeconds(), cfg.getDouble("group.hot-bomb.max-seconds", 30.0));
    }

    public int russianRouletteChambers() {
        return ranged(cfg.getInt("group.russian-roulette.chambers", 6), 2, 12);
    }

    public int russianRouletteBullets() {
        return ranged(cfg.getInt("group.russian-roulette.bullets", 1), 1, russianRouletteChambers() - 1);
    }

    public int horseRaceRunners() {
        return ranged(cfg.getInt("group.race.horses", 8), 2, 16);
    }

    public int horseRaceSteps() {
        return ranged(cfg.getInt("group.race.steps", 60), 20, 400);
    }

    public double raffleTicketPrice() {
        return Math.max(0.01, cfg.getDouble("group.raffle.ticket-price", 100.0));
    }

    public int raffleMaxTickets() {
        return ranged(cfg.getInt("group.raffle.max-tickets", 20), 1, 500);
    }

    public int duelTimeoutSeconds() {
        return ranged(cfg.getInt("group.duel.accept-seconds", 30), 5, 300);
    }

    // --------------------------------------------------------------- casino world

    /** True when the plugin should create and load the dedicated casino world. */
    public boolean worldEnabled() {
        return cfg.getBoolean("world.enabled", true);
    }

    public String worldName() {
        return cfg.getString("world.name", "multiverse_gambling");
    }

    /** Side of the square in blocks; also the size of the world border. */
    public int worldSize() {
        return ranged(cfg.getInt("world.size", 500), 200, 2000);
    }

    /** Build the plaza, the arenas and the roads on startup when they are missing. */
    public boolean worldBuildStructures() {
        return cfg.getBoolean("world.build-structures", true);
    }

    /** Send every player to the casino world as soon as they join the server. */
    public boolean worldTeleportOnJoin() {
        return cfg.getBoolean("world.teleport-on-join", false);
    }

    // -------------------------------------------------------------------- extras

    public boolean provablyFairEnabled() {
        return cfg.getBoolean("fairness.provably-fair", true);
    }

    public int statsSaveMinutes() {
        return ranged(cfg.getInt("data.save-every-minutes", 5), 1, 120);
    }
}
