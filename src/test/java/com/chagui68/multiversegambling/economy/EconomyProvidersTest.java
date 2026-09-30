package com.chagui68.multiversegambling.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

class EconomyProvidersTest {

    @Test
    void autoUsesTheBuiltInOrderWhenNothingIsConfigured() {
        assertEquals(List.of("sbank", "vault", "internal"),
                EconomyProviders.candidates("auto", null));
        assertEquals(List.of("sbank", "vault", "internal"),
                EconomyProviders.candidates("auto", List.of()));
        assertEquals(List.of("sbank", "vault", "internal"),
                EconomyProviders.candidates(null, List.of()));
    }

    @Test
    void theInternalWalletIsAlwaysTheLastResort() {
        for (List<String> order : List.of(
                List.of("vault"),
                List.of("sbank"),
                List.of("vault", "sbank"),
                List.of("something-else"))) {
            List<String> candidates = EconomyProviders.candidates("auto", order);
            assertEquals("internal", candidates.get(candidates.size() - 1),
                    "the casino must always end up with a wallet: " + order);
        }
    }

    @Test
    void theConfiguredOrderIsRespected() {
        assertEquals(List.of("vault", "sbank", "internal"),
                EconomyProviders.candidates("auto", List.of("vault", "sbank")));
        assertEquals(List.of("internal"),
                EconomyProviders.candidates("auto", List.of("internal")));
    }

    @Test
    void unknownAndRepeatedEnginesAreIgnored() {
        assertEquals(List.of("vault", "internal"),
                EconomyProviders.candidates("auto",
                        List.of("banana", "vault", "VAULT", " ", "vault")));
    }

    @Test
    void namingOneEngineStillKeepsTheFallback() {
        assertEquals(List.of("vault", "internal"), EconomyProviders.candidates("vault", null));
        assertEquals(List.of("sbank", "internal"), EconomyProviders.candidates("sbank", null));
        assertEquals(List.of("internal"), EconomyProviders.candidates("internal", null));
    }

    @Test
    void anUnknownEngineFallsBackWithoutThrowing() {
        assertEquals(List.of("internal"), EconomyProviders.candidates("banana", null));
    }

    @Test
    void acceptsTheUsualSpellings() {
        assertEquals(EconomyProviders.SBANK, EconomyProviders.normalise("sBank"));
        assertEquals(EconomyProviders.SBANK, EconomyProviders.normalise(" S-BANK "));
        assertEquals(EconomyProviders.SBANK, EconomyProviders.normalise("spearforge"));
        assertEquals(EconomyProviders.VAULT, EconomyProviders.normalise("Vault"));
        assertEquals(EconomyProviders.INTERNAL, EconomyProviders.normalise("wallet"));
        assertEquals(EconomyProviders.INTERNAL, EconomyProviders.normalise("built-in"));
        assertEquals(EconomyProviders.AUTO, EconomyProviders.normalise("AUTO"));
        assertTrue(EconomyProviders.isKnown("bank"));
        assertFalse(EconomyProviders.isKnown("essentials"));
        assertFalse(EconomyProviders.isKnown(null));
    }

    @Test
    void theDefaultOrderIsStable() {
        assertEquals("sbank", EconomyProviders.DEFAULT_ORDER.get(0));
        assertTrue(EconomyProviders.DEFAULT_ORDER.contains(EconomyProviders.VAULT));
        assertTrue(EconomyProviders.DEFAULT_ORDER.contains(EconomyProviders.INTERNAL));
    }
}
