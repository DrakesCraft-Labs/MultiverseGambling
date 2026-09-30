package com.freebuff.casino.command;

import com.freebuff.casino.CasinoPlugin;
import com.freebuff.casino.game.Game;
import com.freebuff.casino.game.GameCategory;
import com.freebuff.casino.stats.PlayerStats;
import com.freebuff.casino.stats.StatsStore;
import com.freebuff.casino.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** {@code /casino}: abre el menu y expone saldo, ranking, auditoria y administracion. */
public final class CasinoCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of(
            "menu", "juegos", "jugar", "accion", "saldo", "stats", "top", "verificar",
            "reload", "dar", "quitar", "set", "cancelar", "info");

    private final CasinoPlugin plugin;

    public CasinoCommand(CasinoPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                plugin.messages().send(sender, "comando.solo-jugadores");
                return true;
            }
            if (!player.hasPermission("casino.play")) {
                plugin.messages().send(player, "general.sin-permiso");
                return true;
            }
            plugin.guis().openHub(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "menu", "abrir" -> {
                return withPlayer(sender, player -> plugin.guis().openHub(player));
            }
            case "juegos", "lista" -> {
                return listGames(sender, args);
            }
            case "jugar", "apostar" -> {
                return playGame(sender, args);
            }
            case "saldo", "balance" -> {
                return balance(sender, args);
            }
            case "stats", "estadisticas" -> {
                return stats(sender, args);
            }
            case "top", "ranking" -> {
                return top(sender, args);
            }
            case "verificar", "azar", "fair" -> {
                return verify(sender, args);
            }
            case "info" -> {
                return info(sender);
            }
            case "reload", "recargar" -> {
                if (!sender.hasPermission("casino.admin")) {
                    plugin.messages().send(sender, "general.sin-permiso");
                    return true;
                }
                plugin.reloadAll();
                plugin.messages().send(sender, "comando.recargado", "juegos", plugin.games().enabled().size());
                return true;
            }
            case "dar", "quitar", "set" -> {
                return adminMoney(sender, sub, args);
            }
            case "accion" -> {
                return action(sender, args);
            }
            case "cancelar" -> {
                return cancel(sender, args);
            }
            default -> {
                plugin.messages().send(sender, "comando.desconocido", "subcomando", sub);
                return true;
            }
        }
    }

    // ------------------------------------------------------------------ acciones

    private boolean withPlayer(CommandSender sender, java.util.function.Consumer<Player> action) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "comando.solo-jugadores");
            return true;
        }
        if (!player.hasPermission("casino.play")) {
            plugin.messages().send(player, "general.sin-permiso");
            return true;
        }
        action.accept(player);
        return true;
    }

    /**
     * Punto de entrada de los botones de chat. Cada juego en grupo decide que
     * acciones acepta, con lo que un juego nuevo no necesita tocar el comando.
     */
    private boolean action(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "comando.solo-jugadores");
            return true;
        }
        if (args.length < 2) {
            plugin.messages().send(player, "comando.uso", "uso", "/casino accion <accion>");
            return true;
        }
        var game = plugin.games().activeGameOf(player.getUniqueId());
        if (game.isEmpty()) {
            plugin.messages().send(player, "grupo.sin-mesa");
            return true;
        }
        String[] rest = java.util.Arrays.copyOfRange(args, 2, args.length);
        try {
            game.get().handleAction(player, args[1].toLowerCase(Locale.ROOT), rest);
        } catch (RuntimeException error) {
            plugin.getLogger().severe("Error procesando la accion '" + args[1] + "': " + error);
            error.printStackTrace();
        }
        return true;
    }

    private boolean listGames(CommandSender sender, String[] args) {
        GameCategory filter = null;
        if (args.length > 1) {
            filter = args[1].toLowerCase(Locale.ROOT).startsWith("g") ? GameCategory.GRUPO
                    : (args[1].toLowerCase(Locale.ROOT).startsWith("s") ? GameCategory.SOLO : null);
        }
        plugin.messages().sendRaw(sender, "&8&m        &r &6Catalogo del casino &8&m        ");
        for (GameCategory category : GameCategory.values()) {
            if (filter != null && category != filter) {
                continue;
            }
            plugin.messages().sendRaw(sender, "&6" + category.label() + " &7· " + category.description());
            for (Game game : plugin.games().byCategory(category)) {
                String state = game.enabled() ? "&a●" : "&c●";
                plugin.messages().sendRaw(sender, "  " + state + " &f" + game.name()
                        + " &7(" + game.id() + ") &8- &7" + plugin.economy().shortFormat(game.minBet())
                        + " a " + plugin.economy().shortFormat(game.maxBet()));
            }
        }
        plugin.messages().send(sender, "comando.jugar-ayuda");
        return true;
    }

    private boolean playGame(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "comando.solo-jugadores");
            return true;
        }
        if (!player.hasPermission("casino.play")) {
            plugin.messages().send(player, "general.sin-permiso");
            return true;
        }
        if (args.length < 2) {
            if (args.length == 0) {
                plugin.guis().openHub(player);
                return true;
            }
            plugin.guis().openHub(player);
            return true;
        }
        String query = String.join("_", java.util.Arrays.copyOfRange(args, 1, args.length));
        Game game = plugin.games().search(query).orElse(null);
        if (game == null) {
            plugin.messages().send(player, "comando.juego-desconocido", "juego", query);
            return true;
        }
        game.open(player);
        return true;
    }

    private boolean balance(CommandSender sender, String[] args) {
        if (args.length > 1) {
            @SuppressWarnings("deprecation")
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            if (target.getName() == null && !target.hasPlayedBefore()) {
                plugin.messages().send(sender, "comando.jugador-desconocido", "jugador", args[1]);
                return true;
            }
            plugin.messages().send(sender, "comando.saldo-otro",
                    "jugador", String.valueOf(target.getName()),
                    "saldo", plugin.economy().format(plugin.economy().balance(target.getUniqueId())));
            return true;
        }
        return withPlayer(sender, player -> plugin.messages().send(player, "comando.saldo",
                "saldo", plugin.economy().format(plugin.economy().balance(player.getUniqueId()))));
    }

    private boolean stats(CommandSender sender, String[] args) {
        Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
        Player player = target;
        if (player == null && sender instanceof Player self) {
            player = self;
        }
        if (player == null) {
            plugin.messages().send(sender, "comando.jugador-desconocido",
                    "jugador", args.length > 1 ? args[1] : "-");
            return true;
        }
        PlayerStats stats = plugin.stats().of(player.getUniqueId());
        plugin.messages().sendRaw(sender, "&8&m        &r &6Estadisticas de " + player.getName() + " &8&m        ");
        plugin.messages().sendRaw(sender, "&7Partidas: &f" + stats.games
                + " &8| &7Victorias: &f" + stats.wins + " &8(" + Text.percent(stats.winRate()) + ")");
        plugin.messages().sendRaw(sender, "&7Apostado: &f" + plugin.economy().format(stats.wagered)
                + " &8| &7Recuperado: &f" + plugin.economy().format(stats.returned));
        plugin.messages().sendRaw(sender, (stats.profit() >= 0 ? "&7Beneficio: &a" : "&7Beneficio: &c")
                + plugin.economy().format(stats.profit())
                + " &8| &7Retorno real: &f" + Text.percent(stats.rtp()));
        plugin.messages().sendRaw(sender, "&7Mayor premio: &f" + plugin.economy().format(stats.biggestWin)
                + " &8| &7Juego favorito: &f" + stats.favouriteGame());
        return true;
    }

    private boolean top(CommandSender sender, String[] args) {
        String mode = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "beneficio";
        List<StatsStore.TopEntry> entries = switch (mode) {
            case "apostado", "wagered" -> plugin.stats().topByWagered(10);
            case "premio", "win" -> plugin.stats().topByBiggestWin(10);
            default -> plugin.stats().topByProfit(10);
        };
        plugin.messages().sendRaw(sender, "&8&m        &r &6Ranking &7(" + mode + ") &8&m        ");
        if (entries.isEmpty()) {
            plugin.messages().sendRaw(sender, "&7Todavia no hay datos.");
            return true;
        }
        int position = 1;
        for (StatsStore.TopEntry entry : entries) {
            double value = switch (mode) {
                case "apostado", "wagered" -> entry.stats().wagered;
                case "premio", "win" -> entry.stats().biggestWin;
                default -> entry.stats().profit();
            };
            String colour = position == 1 ? "&6" : (position <= 3 ? "&e" : "&7");
            plugin.messages().sendRaw(sender, colour + position + ". &f" + entry.name()
                    + " &8- &f" + plugin.economy().format(value));
            position++;
        }
        return true;
    }

    private boolean verify(CommandSender sender, String[] args) {
        if (args.length > 1 && sender instanceof Player player) {
            plugin.fair().setClientSeed(player, args[1]);
            plugin.messages().send(player, "azar.semilla-cambiada",
                    "semilla", plugin.fair().clientSeed(player.getUniqueId()));
            return true;
        }
        UUID id = sender instanceof Player player ? player.getUniqueId() : new UUID(0, 0);
        plugin.messages().sendRaw(sender, "&8&m        &r &6Azar verificable &8&m        ");
        for (String line : plugin.fair().auditLines(id)) {
            plugin.messages().sendRaw(sender, line);
        }
        plugin.messages().send(sender, "azar.explicacion");
        return true;
    }

    private boolean info(CommandSender sender) {
        plugin.messages().sendRaw(sender, "&8&m        &r &6FreebuffCasino &8&m        ");
        plugin.messages().sendRaw(sender, "&7Economia: &f" + plugin.economy().provider().name());
        plugin.messages().sendRaw(sender, "&7Juegos: &f" + plugin.games().all().size()
                + " &7(" + plugin.games().enabled().size() + " activos)");
        plugin.messages().sendRaw(sender, "&7Ventaja de la casa: &f" + Text.percent(plugin.config().houseEdge()));
        plugin.messages().sendRaw(sender, "&7Azar verificable: &f"
                + (plugin.fair().enabled() ? "activado" : "desactivado"));
        plugin.messages().sendRaw(sender, "&7Secreto actual: &f" + plugin.fair().serverSeedHash());
        return true;
    }

    private boolean adminMoney(CommandSender sender, String mode, String[] args) {
        if (!sender.hasPermission("casino.admin")) {
            plugin.messages().send(sender, "general.sin-permiso");
            return true;
        }
        if (args.length < 3) {
            plugin.messages().send(sender, "comando.uso", "uso", "/casino " + mode + " <jugador> <cantidad>");
            return true;
        }
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getName() == null && !target.hasPlayedBefore()) {
            plugin.messages().send(sender, "comando.jugador-desconocido", "jugador", args[1]);
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[2].replace(",", "."));
        } catch (NumberFormatException error) {
            plugin.messages().send(sender, "comando.cantidad-invalida", "valor", args[2]);
            return true;
        }
        UUID id = target.getUniqueId();
        switch (mode) {
            case "dar" -> plugin.economy().deposit(id, Math.abs(amount));
            case "quitar" -> plugin.economy().provider().withdraw(id, Math.abs(amount));
            default -> plugin.economy().set(id, Math.max(0, amount));
        }
        plugin.messages().send(sender, "comando.saldo-actualizado",
                "jugador", String.valueOf(target.getName()),
                "saldo", plugin.economy().format(plugin.economy().balance(id)));
        return true;
    }

    private boolean cancel(CommandSender sender, String[] args) {
        if (!sender.hasPermission("casino.admin")) {
            plugin.messages().send(sender, "general.sin-permiso");
            return true;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "comando.uso", "uso", "/casino cancelar <jugador>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "comando.jugador-desconocido", "jugador", args[1]);
            return true;
        }
        plugin.sessions().cancel(target.getUniqueId());
        plugin.messages().send(sender, "comando.sesion-cancelada", "jugador", target.getName());
        return true;
    }

    // -------------------------------------------------------------- completado

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(SUBS, args[0]);
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "jugar" -> filter(plugin.games().all().stream().map(Game::id).toList(), args[1]);
                case "juegos", "lista" -> filter(List.of("solo", "grupo"), args[1]);
                case "top", "ranking" -> filter(List.of("beneficio", "apostado", "premio"), args[1]);
                case "verificar" -> filter(List.of("<semilla>"), args[1]);
                case "dar", "quitar", "set", "cancelar", "saldo", "stats" ->
                        filter(onlineNames(), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3 && List.of("dar", "quitar", "set").contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(List.of("100", "1000", "10000"), args[2]);
        }
        return List.of();
    }

    private List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    private List<String> filter(List<String> options, String prefix) {
        String needle = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(needle)) {
                out.add(option);
            }
        }
        return out;
    }
}
