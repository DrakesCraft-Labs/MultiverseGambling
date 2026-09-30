package com.chagui68.multiversegambling.gui;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.game.Game;
import com.chagui68.multiversegambling.util.Items;
import com.chagui68.multiversegambling.util.Text;
import java.util.function.DoubleConsumer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Selector de apuesta comun a todos los juegos.
 *
 * <p>Centralizarlo evita el clasico fallo de que un juego acepte una apuesta por
 * encima del saldo o por debajo del minimo. Aqui se acota siempre.</p>
 */
public final class BetSelectorGui extends Gui {

    private static final ItemStack FILLER =
            Items.of(Material.BLACK_STAINED_GLASS_PANE).build();

    private final Game game;
    private final DoubleConsumer onConfirm;
    private double bet;

    public BetSelectorGui(MultiverseGamblingPlugin plugin, Player player, Game game, double initial, DoubleConsumer onConfirm) {
        super(plugin, player, 5, "&8Apuesta &7· &6" + game.name());
        this.game = game;
        this.onConfirm = onConfirm;
        this.bet = clamp(initial);
    }

    private double balance() {
        return plugin.economy().balance(player().getUniqueId());
    }

    private double upperLimit() {
        return Math.max(game.minBet(), Math.min(game.maxBet(), balance()));
    }

    private double clamp(double value) {
        double rounded = Math.rint(value * 100.0) / 100.0;
        return Math.max(game.minBet(), Math.min(upperLimit(), rounded));
    }

    private void adjust(double factor) {
        bet = clamp(bet * factor);
        refresh();
    }

    private void setTo(double value) {
        bet = clamp(value);
        refresh();
    }

    @Override
    protected void render() {
        clearActions();
        fill(FILLER);

        double balance = balance();
        double limit = upperLimit();
        boolean afford = balance + 1e-9 >= game.minBet();

        // Cabecera: ficha con la apuesta actual.
        set(4, Items.of(Material.GOLD_INGOT)
                .name("&6Apuesta: &f" + plugin.economy().format(bet))
                .lore(
                        "&7Juego: &f" + game.name(),
                        "&7Saldo: &f" + plugin.economy().format(balance),
                        "&7Minimo: &f" + plugin.economy().format(game.minBet()),
                        "&7Maximo: &f" + plugin.economy().format(Math.min(game.maxBet(), balance)),
                        "",
                        "&7Sube o baja la apuesta con los botones.")
                .glow(true)
                .build());

        set(10, button(Material.RED_DYE, "&cMitad", "&7Divide la apuesta entre 2"), e -> adjust(0.5));
        set(11, button(Material.GOLD_NUGGET, "&eBajar", "&7Resta un 10%"), e -> adjust(0.9));
        set(12, button(Material.LIME_DYE, "&aSubir", "&7Suma un 10%"), e -> adjust(1.1));
        set(13, button(Material.GOLD_BLOCK, "&6Doble", "&7Multiplica la apuesta por 2"), e -> adjust(2.0));
        set(14, button(Material.IRON_NUGGET, "&7Minimo", "&7Apuesta lo mas bajo permitido"),
                e -> setTo(game.minBet()));
        set(15, button(Material.DIAMOND, "&bMitad del saldo", "&7Apuesta la mitad de lo que tienes"),
                e -> setTo(balance / 2.0));
        set(16, button(Material.EMERALD_BLOCK, "&aTodo", "&7Apuesta todo tu saldo"), e -> setTo(balance));

        set(22, Items.of(Material.PAPER)
                .name("&fCantidad seleccionada")
                .lore(
                        "&7Apostando: &6" + plugin.economy().format(bet),
                        "&7Tope actual: &f" + plugin.economy().format(limit))
                .build());

        if (afford) {
            set(40, Items.of(Material.LIME_CONCRETE)
                    .name("&a&lCONFIRMAR APUESTA")
                    .lore(
                            "&7Vas a apostar &6" + plugin.economy().format(bet),
                            "&7en &f" + game.name() + "&7.",
                            "",
                            "&ePulsa para jugar")
                    .glow(true)
                    .build(), this::confirm);
        } else {
            set(40, Items.of(Material.RED_CONCRETE)
                    .name("&c&lSIN SALDO SUFICIENTE")
                    .lore(
                            "&7Necesitas al menos &f" + plugin.economy().format(game.minBet()),
                            "&7y tienes &f" + plugin.economy().format(balance) + "&7.")
                    .build());
        }

        set(36, button(Material.BARRIER, "&cVolver al menu", "&7Cierra este menu"), e -> {
            close();
            plugin.guis().openHub(player());
        });
    }

    private ItemStack button(Material material, String name, String lore) {
        return Items.of(material).name(name).lore(lore).build();
    }

    private void confirm(InventoryClickEvent event) {
        double amount = bet;
        close();
        onConfirm.accept(amount);
    }

    @Override
    public String sessionId() {
        return game.id();
    }

    @Override
    protected void onClose() {
        // Cerrar el selector sin confirmar no cuesta nada: no se ha cobrado aun.
    }
}
