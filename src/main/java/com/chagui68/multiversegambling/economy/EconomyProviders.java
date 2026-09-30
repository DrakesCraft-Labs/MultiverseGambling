package com.chagui68.multiversegambling.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Registry of the economy engines the casino can use, and the order in which they are
 * tried.
 *
 * <p>Deliberately free of Bukkit: this is the piece that decides, from the configuration
 * alone, that "auto" means "sbank, then vault, then the internal wallet" and that naming a
 * single engine still leaves the internal wallet as the last resort. The hooking itself
 * lives in {@link SbankEconomy}, {@link VaultEconomy} and {@link InternalEconomy}.</p>
 */
public final class EconomyProviders {

    /**
     * Try every engine, in the configured order.
     */
    public static final String AUTO = "auto";
    /**
     * The economy the server registers through Vault.
     */
    public static final String VAULT = "vault";
    /**
     * The bank accounts of the sBank plugin.
     */
    public static final String SBANK = "sbank";
    /**
     * The wallet that ships with the casino.
     */
    public static final String INTERNAL = "internal";

    /**
     * Order used when the configuration does not name one: the bank first, because a
     * server that runs sBank keeps its players' money there, then whatever Vault offers,
     * and the internal wallet as the safety net.
     */
    public static final List<String> DEFAULT_ORDER = List.of(SBANK, VAULT, INTERNAL);

    private EconomyProviders() {
    }

    /**
     * Engines to try, in order, for the configured mode.
     *
     * @param mode      value of {@code economy.provider}
     * @param autoOrder value of {@code economy.auto-order}, may be null or empty
     * @return at least one engine, always ending with {@link #INTERNAL} so the plugin
     * always has a wallet to fall back to
     */
    public static List<String> candidates(String mode, List<String> autoOrder) {
        String wanted = mode == null || mode.isBlank() ? AUTO : normalise(mode);
        if (wanted == null) {
            return List.of(INTERNAL);
        }
        List<String> candidates = new ArrayList<>();
        if (AUTO.equals(wanted)) {
            List<String> order = autoOrder == null || autoOrder.isEmpty() ? DEFAULT_ORDER : autoOrder;
            for (String entry : order) {
                String engine = normalise(entry);
                if (engine != null && !candidates.contains(engine)) {
                    candidates.add(engine);
                }
            }
            if (candidates.isEmpty()) {
                candidates.addAll(DEFAULT_ORDER);
            }
        } else {
            candidates.add(wanted);
        }
        candidates.remove(INTERNAL);
        candidates.add(INTERNAL);
        return List.copyOf(candidates);
    }

    /**
     * Maps loose input to an engine name, accepting the usual spellings and aliases.
     *
     * @return the engine, or {@code null} when nothing matches
     */
    public static String normalise(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case AUTO -> AUTO;
            case VAULT, "vault-economy", "server" -> VAULT;
            case SBANK, "s-bank", "spearforge", "bank" -> SBANK;
            case INTERNAL, "wallet", "builtin", "built-in", "own" -> INTERNAL;
            default -> null;
        };
    }

    /**
     * True when the input names one of the engines this plugin knows.
     */
    public static boolean isKnown(String value) {
        return normalise(value) != null;
    }
}
