package com.chagui68.multiversegambling.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * The two shipped language files have to stay in step.
 *
 * <p>A missing key is not silent in game: it renders as {@code [missing message: key]},
 * which is exactly the defect this test catches before the jar is built. Placeholders
 * are checked too, so a translator cannot drop a {@code {bet}} and break a line.</p>
 */
class LanguageFilesTest {

    /** Every game of the catalogue: each one must have its own translated section. */
    private static final List<String> GAME_IDS = List.of(
            "roulette", "slots", "crash", "mines", "towers", "blackjack", "high-low", "dice",
            "plinko", "scratch", "lucky-wheel", "coin-flip", "color-roulette", "jackpot",
            "hot-bomb", "bomb-board", "russian-roulette", "race", "duel", "raffle",
            "dice-poker");

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");
    private static final Pattern SPANISH_ACCENT = Pattern.compile("[áéíóúñ¿¡ÁÉÍÓÚÑ]");

    private static final Map<String, String> ENGLISH = load("en.yml");
    private static final Map<String, String> SPANISH = load("es.yml");

    private static Map<String, String> load(String file) {
        try (InputStream stream = LanguageFilesTest.class.getResourceAsStream("/lang/" + file)) {
            assertNotNull(stream, "the bundle lang/" + file + " must be packaged with the plugin");
            Object root = new Yaml().load(stream);
            Map<String, String> flat = new LinkedHashMap<>();
            flatten("", root, flat);
            return flat;
        } catch (Exception error) {
            throw new AssertionError("could not read lang/" + file + ": " + error.getMessage(), error);
        }
    }

    private static void flatten(String prefix, Object node, Map<String, String> out) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                flatten(prefix + entry.getKey() + ".", entry.getValue(), out);
            }
            return;
        }
        if (node instanceof List<?> list) {
            StringBuilder joined = new StringBuilder();
            for (Object line : list) {
                joined.append(line).append('\n');
            }
            out.put(strip(prefix), joined.toString());
            return;
        }
        out.put(strip(prefix), String.valueOf(node));
    }

    private static String strip(String key) {
        return key.endsWith(".") ? key.substring(0, key.length() - 1) : key;
    }

    private static Set<String> placeholders(String value) {
        Set<String> found = new TreeSet<>();
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    @Test
    void bothLanguagesDeclareTheSameKeys() {
        // catalog.<game> only lives in es.yml: the English names are in the code.
        Set<String> missingInSpanish = new TreeSet<>(comparable(ENGLISH.keySet()));
        missingInSpanish.removeAll(comparable(SPANISH.keySet()));
        Set<String> missingInEnglish = new TreeSet<>(comparable(SPANISH.keySet()));
        missingInEnglish.removeAll(comparable(ENGLISH.keySet()));
        assertTrue(missingInSpanish.isEmpty(), "lang/es.yml is missing: " + missingInSpanish);
        assertTrue(missingInEnglish.isEmpty(), "lang/en.yml is missing: " + missingInEnglish);
    }

    /** Keys that both files have to share, leaving the catalogue overrides aside. */
    private static Set<String> comparable(Set<String> keys) {
        Set<String> out = new TreeSet<>();
        for (String key : keys) {
            if (!key.startsWith("catalog.")) {
                out.add(key);
            }
        }
        return out;
    }

    @Test
    void theSpanishCatalogueTranslatesEveryGame() {
        List<String> untranslated = new ArrayList<>();
        for (String id : GAME_IDS) {
            if (!SPANISH.containsKey("catalog." + id + ".name")
                    || !SPANISH.containsKey("catalog." + id + ".description")) {
                untranslated.add(id);
            }
        }
        assertTrue(untranslated.isEmpty(), "games without a Spanish catalogue entry: " + untranslated);
    }

    @Test
    void everyKeyKeepsItsPlaceholders() {
        List<String> broken = new ArrayList<>();
        for (Map.Entry<String, String> entry : ENGLISH.entrySet()) {
            String translated = SPANISH.get(entry.getKey());
            if (translated == null || entry.getKey().startsWith("catalog.")) {
                continue;
            }
            if (!placeholders(entry.getValue()).equals(placeholders(translated))) {
                broken.add(entry.getKey());
            }
        }
        assertTrue(broken.isEmpty(), "keys with different placeholders: " + broken);
    }

    @Test
    void everyGameHasItsOwnTranslatedSection() {
        List<String> untranslated = new ArrayList<>();
        for (String id : GAME_IDS) {
            boolean panel = ENGLISH.keySet().stream().anyMatch(key -> key.startsWith("panel." + id + "."));
            boolean round = ENGLISH.keySet().stream().anyMatch(key -> key.startsWith("group." + id + "."));
            if (!panel && !round) {
                untranslated.add(id);
            }
        }
        assertTrue(untranslated.isEmpty(), "games without a translated section: " + untranslated);
    }

    @Test
    void englishHasNoSpanishLeftovers() {
        List<String> accented = new ArrayList<>();
        for (Map.Entry<String, String> entry : ENGLISH.entrySet()) {
            if (SPANISH_ACCENT.matcher(entry.getValue()).find()) {
                accented.add(entry.getKey());
            }
        }
        assertTrue(accented.isEmpty(), "English keys with Spanish accents: " + accented);
    }

    @Test
    void sharedHeadersArePresent() {
        for (String key : List.of("prefix", "panel.header", "panel.round-header", "panel.common.close")) {
            assertTrue(ENGLISH.containsKey(key), "lang/en.yml lacks " + key);
            assertTrue(SPANISH.containsKey(key), "lang/es.yml lacks " + key);
        }
        assertEquals(21, GAME_IDS.size());
    }
}
