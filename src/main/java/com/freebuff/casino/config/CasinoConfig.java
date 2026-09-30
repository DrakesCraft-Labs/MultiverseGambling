package com.freebuff.casino.config;

import com.freebuff.casino.CasinoPlugin;
import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;

/** Acceso tipado a config.yml. Todos los valores tienen un por defecto sensato. */
public final class CasinoConfig {

    private final CasinoPlugin plugin;
    private FileConfiguration cfg;

    public CasinoConfig(CasinoPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        this.cfg = plugin.getConfig();
    }

    public FileConfiguration raw() {
        return cfg;
    }

    private double ranged(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private int ranged(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // ---------------------------------------------------------------- economia

    public String currencyName() {
        return cfg.getString("economia.moneda", "monedas");
    }

    public String currencyFormat() {
        return cfg.getString("economia.formato", "&6{amount} &7{moneda}");
    }

    public double startingBalance() {
        return Math.max(0, cfg.getDouble("economia.saldo-inicial", 1000.0));
    }

    /** auto, interno o vault. */
    public String economyProvider() {
        return cfg.getString("economia.proveedor", "auto");
    }

    // ------------------------------------------------------------------ reglas

    /** Ventaja de la casa por defecto de todos los juegos. */
    public double houseEdge() {
        return ranged(cfg.getDouble("juego.ventaja-casa", 0.02), 0.0, 0.5);
    }

    public double minBet() {
        return Math.max(0.01, cfg.getDouble("juego.apuesta-minima", 10.0));
    }

    public double maxBet() {
        return Math.max(minBet(), cfg.getDouble("juego.apuesta-maxima", 100000.0));
    }

    public double minBet(String gameId) {
        return Math.max(0.01, cfg.getDouble("juegos." + gameId + ".apuesta-minima", minBet()));
    }

    public double maxBet(String gameId) {
        return Math.max(minBet(gameId), cfg.getDouble("juegos." + gameId + ".apuesta-maxima", maxBet()));
    }

    public boolean gameEnabled(String gameId) {
        return cfg.getBoolean("juegos." + gameId + ".activo", true);
    }

    public boolean announceWins() {
        return cfg.getBoolean("juego.anunciar-premios", true);
    }

    public double announceThreshold() {
        return cfg.getDouble("juego.anunciar-desde", 50000.0);
    }

    // ------------------------------------------------------- juegos en solitario

    public boolean rouletteAmerican() {
        return cfg.getBoolean("juegos.ruleta.americana", false);
    }

    public int rouletteSpinTicks() {
        return Math.max(20, cfg.getInt("juegos.ruleta.ticks-giro", 60));
    }

    public int slotsSpinTicks() {
        return Math.max(10, cfg.getInt("juegos.tragamonedas.ticks-giro", 40));
    }

    public int minesTiles() {
        return ranged(cfg.getInt("juegos.minas.casillas", 25), 4, 54);
    }

    public int minesDefaultMines() {
        return Math.max(1, cfg.getInt("juegos.minas.minas-por-defecto", 3));
    }

    public int minesMaxMines() {
        return Math.max(1, cfg.getInt("juegos.minas.minas-maximas", 24));
    }

    public double crashDoubleEverySeconds() {
        return Math.max(1, cfg.getDouble("juegos.crash.segundos-para-doblar", 10.0));
    }

    public double crashMaxMultiplier() {
        return Math.max(1.5, cfg.getDouble("juegos.crash.multiplicador-maximo", 1000.0));
    }

    public int crashCountdownSeconds() {
        return ranged(cfg.getInt("juegos.crash.cuenta-atras", 5), 1, 30);
    }

    public int plinkoRows() {
        return ranged(cfg.getInt("juegos.plinko.filas", 12), 6, 20);
    }

    public double plinkoMaxMultiplier() {
        return Math.max(1, cfg.getDouble("juegos.plinko.multiplicador-maximo", 500.0));
    }

    public int highLowMaxChain() {
        return ranged(cfg.getInt("juegos.mayor-menor.pasos-maximos", 5), 1, 20);
    }

    public int towersLevels() {
        return ranged(cfg.getInt("juegos.torre.pisos", 9), 1, 25);
    }

    public int towersTiles() {
        return ranged(cfg.getInt("juegos.torre.casillas", 4), 2, 9);
    }

    public int towersBombs() {
        return ranged(cfg.getInt("juegos.torre.bombas", 1), 1, towersTiles() - 1);
    }

    public int blackjackDecks() {
        return ranged(cfg.getInt("juegos.blackjack.barajas", 6), 1, 8);
    }

    public boolean blackjackHitSoft17() {
        return cfg.getBoolean("juegos.blackjack.crupier-pide-con-17-blando", false);
    }

    public int scratchPicks() {
        return ranged(cfg.getInt("juegos.rasca.destapes", 3), 1, 9);
    }

    public List<Double> luckyWheelSegments() {
        List<Double> configured = cfg.getDoubleList("juegos.ruleta-suerte.segmentos");
        if (configured.isEmpty()) {
            return List.of(0.0, 1.5, 0.0, 2.0, 0.0, 3.0, 0.0, 1.5, 0.0, 5.0, 0.0, 20.0);
        }
        return configured;
    }

    // ------------------------------------------------------------ juegos en grupo

    public int groupBettingSeconds() {
        return ranged(cfg.getInt("grupo.segundos-de-apuestas", 20), 3, 300);
    }

    public int groupMaxPlayers() {
        return ranged(cfg.getInt("grupo.maximo-jugadores", 24), 2, 100);
    }

    public int groupCountdownSeconds() {
        return ranged(cfg.getInt("grupo.cuenta-atras", 5), 1, 30);
    }

    public double groupHouseCut() {
        return ranged(cfg.getDouble("grupo.comision-casa", 0.0), 0.0, 0.5);
    }

    public int bombBoardSize() {
        return ranged(cfg.getInt("grupo.tablero-bombas.casillas", 36), 9, 54);
    }

    public int bombBoardBombs() {
        return Math.max(1, cfg.getInt("grupo.tablero-bombas.bombas", 4));
    }

    public int bombBoardTurnSeconds() {
        return ranged(cfg.getInt("grupo.tablero-bombas.segundos-por-turno", 10), 3, 60);
    }

    public double hotBombMinSeconds() {
        return Math.max(1, cfg.getDouble("grupo.bomba-caliente.segundos-minimos", 5.0));
    }

    public double hotBombMaxSeconds() {
        return Math.max(hotBombMinSeconds(), cfg.getDouble("grupo.bomba-caliente.segundos-maximos", 30.0));
    }

    public int russianRouletteChambers() {
        return ranged(cfg.getInt("grupo.ruleta-rusa.recamaras", 6), 2, 12);
    }

    public int russianRouletteBullets() {
        return ranged(cfg.getInt("grupo.ruleta-rusa.balas", 1), 1, russianRouletteChambers() - 1);
    }

    public int horseRaceRunners() {
        return ranged(cfg.getInt("grupo.carrera.caballos", 8), 2, 16);
    }

    public int horseRaceSteps() {
        return ranged(cfg.getInt("grupo.carrera.pasos", 60), 20, 400);
    }

    public double raffleTicketPrice() {
        return Math.max(0.01, cfg.getDouble("grupo.rifa.precio-boleta", 100.0));
    }

    public int raffleMaxTickets() {
        return ranged(cfg.getInt("grupo.rifa.boletas-maximas", 20), 1, 500);
    }

    public int duelTimeoutSeconds() {
        return ranged(cfg.getInt("grupo.duelo.segundos-para-aceptar", 30), 5, 300);
    }

    // ------------------------------------------------------------------- extras

    public boolean provablyFairEnabled() {
        return cfg.getBoolean("azar.verificable", true);
    }

    public int statsSaveMinutes() {
        return ranged(cfg.getInt("datos.guardar-cada-minutos", 5), 1, 120);
    }
}
