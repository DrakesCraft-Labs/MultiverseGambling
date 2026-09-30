package com.freebuff.casino.economy;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.bukkit.entity.Player;

/**
 * El bote comun de los juegos en grupo.
 *
 * <p>Guarda una {@link Wager} por jugador, de modo que el dinero ya esta fuera de
 * los monederos cuando empieza la partida. Repartir el bote vacia las apuestas,
 * asi que ningun camino de codigo puede pagar dos veces.</p>
 */
public final class Pot {

    private final EconomyManager economy;
    private final Map<UUID, Wager> wagers = new LinkedHashMap<>();

    public Pot(EconomyManager economy) {
        this.economy = economy;
    }

    /** Cobra la apuesta y la mete en el bote. */
    public boolean add(Player player, double amount) {
        Wager wager = economy.stake(player, amount);
        if (wager == null) {
            return false;
        }
        Wager previous = wagers.put(player.getUniqueId(), wager);
        if (previous != null) {
            previous.refund();
        }
        return true;
    }

    /** Añade una apuesta ya creada (por ejemplo, la de un jugador que se desconecto). */
    public void addRaw(UUID playerId, Wager wager) {
        wagers.put(playerId, wager);
    }

    /** Saca la apuesta del bote y devuelve el dinero si aun no se habia liquidado. */
    public void remove(UUID playerId) {
        Wager wager = wagers.remove(playerId);
        if (wager != null && !wager.isSettled()) {
            wager.refund();
        }
    }

    public boolean contains(UUID playerId) {
        return wagers.containsKey(playerId);
    }

    public Wager get(UUID playerId) {
        return wagers.get(playerId);
    }

    public double amountOf(UUID playerId) {
        Wager wager = wagers.get(playerId);
        return wager == null ? 0 : wager.amount();
    }

    public double total() {
        double total = 0;
        for (Wager wager : wagers.values()) {
            if (!wager.isSettled()) {
                total += wager.amount();
            }
        }
        return total;
    }

    public int size() {
        return wagers.size();
    }

    public boolean isEmpty() {
        return wagers.isEmpty();
    }

    public Set<UUID> participants() {
        return new LinkedHashSet<>(wagers.keySet());
    }

    public Map<UUID, Double> amounts() {
        Map<UUID, Double> out = new LinkedHashMap<>();
        wagers.forEach((id, wager) -> out.put(id, wager.amount()));
        return out;
    }

    /** Devuelve todo el dinero y vacia el bote. */
    public void refundAll() {
        settle((id, wager) -> wager.refund());
    }

    /** Todo el bote para un ganador. */
    public void payAllTo(UUID winner) {
        payAllTo(winner, 0);
    }

    /** Todo el bote para un ganador, con una comision para la casa. */
    public void payAllTo(UUID winner, double houseCut) {
        double total = total();
        double prize = total * (1.0 - Math.max(0, Math.min(0.5, houseCut)));
        settle((id, wager) -> {
            if (id.equals(winner)) {
                wager.payAbsolute(prize);
            } else {
                wager.lose();
            }
        });
    }

    /** Reparte el bote entre varios ganadores a partes iguales. */
    public void shareAmong(Set<UUID> winners) {
        if (winners.isEmpty()) {
            refundAll();
            return;
        }
        double share = total() / winners.size();
        settle((id, wager) -> {
            if (winners.contains(id)) {
                wager.payAbsolute(share);
            } else {
                wager.lose();
            }
        });
    }

    /**
     * Reparte el bote segun lo que devuelva {@code prizeFor}: multiplicador sobre
     * lo apostado, o 0 para perder. La cantidad se calcula antes de liquidar nada.
     */
    public void payoutByMultiplier(java.util.function.ToDoubleFunction<UUID> prizeFor) {
        Map<UUID, Double> prizes = new LinkedHashMap<>();
        wagers.forEach((id, wager) -> prizes.put(id, wager.amount() * prizeFor.applyAsDouble(id)));
        settle((id, wager) -> {
            Double prize = prizes.get(id);
            if (prize == null || prize <= 0) {
                wager.lose();
            } else {
                wager.payAbsolute(prize);
            }
        });
    }

    /** Aplica un resultado a cada apuesta y vacia el bote. */
    public void settle(BiConsumer<UUID, Wager> settlement) {
        wagers.forEach(settlement);
        wagers.clear();
    }

    /** Marca todas las apuestas como perdidas sin pagar a nadie. */
    public void burn() {
        settle((id, wager) -> wager.lose());
    }
}
