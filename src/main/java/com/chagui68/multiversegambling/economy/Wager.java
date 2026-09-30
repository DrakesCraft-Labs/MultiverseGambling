package com.chagui68.multiversegambling.economy;

import java.util.UUID;

/**
 * A bet already taken out of the player's wallet.
 *
 * <p>It exists to make the classic casino bug impossible: paying the same round twice.
 * All the money of a round goes through here, and settling twice does nothing the
 * second time.</p>
 */
public final class Wager {

    private final EconomyManager economy;
    private final UUID playerId;
    private final double amount;
    private boolean settled;

    Wager(EconomyManager economy, UUID playerId, double amount) {
        this.economy = economy;
        this.playerId = playerId;
        this.amount = amount;
    }

    public UUID playerId() {
        return playerId;
    }

    public double amount() {
        return amount;
    }

    public boolean isSettled() {
        return settled;
    }

    /** Pays {@code amount * multiplier}. Returns the money taken when it wins. */
    public boolean pay(double multiplier) {
        if (multiplier <= 0) {
            lose();
            return false;
        }
        return payAbsolute(amount * multiplier);
    }

    /** Returns the capital (push or voided round). */
    public boolean refund() {
        return payAbsolute(amount);
    }

    /** Pays an exact amount, ignoring the original bet. */
    public boolean payAbsolute(double payout) {
        if (settled) {
            return false;
        }
        settled = true;
        return economy.provider().deposit(playerId, Math.max(0, payout));
    }

    /** Marks the bet as lost: the money already left the wallet. */
    public void lose() {
        settled = true;
    }

    /** Net profit of this bet, or 0 when it has not been settled yet. */
    public double profit() {
        return settled ? -amount : 0;
    }
}
