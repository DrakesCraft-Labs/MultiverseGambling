package com.chagui68.multiversegambling.i18n;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Every message the code can ask for has to exist in both language files.
 *
 * <p>A key that is used but never defined renders as {@code [missing message: key]} in the
 * middle of a game, which is how a batch of keys once ended up filed under the wrong
 * section without anybody noticing until a player saw it. {@code LanguageFilesTest} keeps
 * the two files in step with each other; this one keeps them in step with the code.</p>
 *
 * <p>The sources are read from the project folder, so the test does nothing when they are
 * not there (the jar only ships the compiled classes).</p>
 */
class MessageKeyTest {

    /** "command.balance-updated": dot separated lowercase words. */
    private static final Pattern KEY = Pattern.compile("\"([a-z][a-z0-9-]*(?:\\.[a-z0-9-]+)+)\"");

    private static final List<String> NOT_MESSAGES = List.of(
            "com.", "org.", "net.", "java.", "io.");
    private static final List<String> NOT_MESSAGES_ENDINGS = List.of(
            ".yml", ".json", ".jar", ".java", ".png");
    /** Terrain settings of the casino world, not a message. */
    private static final Set<String> EXTERNAL = Set.of("minecraft:plains");

    @Test
    void everyMessageTheCodeAsksForIsDefined() throws IOException {
        Path sources = Path.of("src", "main", "java");
        Path config = Path.of("src", "main", "resources", "config.yml");
        assumeTrue(Files.isDirectory(sources), "sources are not available, skipping");

        Set<String> configKeys = config.toFile().isFile() ? flatten(Files.readString(config)) : Set.of();
        Set<String> used = usedKeys(sources, configKeys);
        assertTrue(used.size() > 300, "the scan found too few keys, it is probably broken: " + used.size());

        for (String file : List.of("en", "es")) {
            Set<String> defined = bundleKeys(file);
            Set<String> missing = new TreeSet<>();
            for (String key : used) {
                if (!defined.contains(key)) {
                    missing.add(key);
                }
            }
            assertTrue(missing.isEmpty(),
                    "lang/" + file + ".yml does not define these keys the code asks for: " + missing);
        }
    }

    private static Set<String> usedKeys(Path sources, Set<String> configKeys) {
        Set<String> used = new TreeSet<>();
        try (Stream<Path> files = Files.walk(sources)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = KEY.matcher(Files.readString(file));
                while (matcher.find()) {
                    String key = matcher.group(1);
                    if (NOT_MESSAGES.stream().anyMatch(key::startsWith)
                            || NOT_MESSAGES_ENDINGS.stream().anyMatch(key::endsWith)
                            || configKeys.contains(key)
                            || EXTERNAL.contains(key)
                            // Keys built by concatenation, such as "panel.coin-flip.side-".
                            || key.endsWith("-")) {
                        continue;
                    }
                    used.add(key);
                }
            }
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        return used;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> bundleKeys(String code) {
        try (InputStream stream = MessageKeyTest.class.getResourceAsStream("/lang/" + code + ".yml")) {
            assertTrue(stream != null, "lang/" + code + ".yml is not packaged");
            Map<String, Object> flat = new LinkedHashMap<>();
            flatten("", new Yaml().load(stream), flat);
            return new TreeSet<>(flat.keySet());
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static void flatten(String prefix, Object node, Map<String, Object> out) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                flatten(prefix + entry.getKey() + ".", entry.getValue(), out);
            }
            return;
        }
        String key = prefix.endsWith(".") ? prefix.substring(0, prefix.length() - 1) : prefix;
        out.put(key, node);
    }

    /** Same keys, read from a YAML text: config.yml is not on the classpath. */
    @SuppressWarnings("unchecked")
    private static Set<String> flatten(String yaml) {
        Map<String, Object> flat = new LinkedHashMap<>();
        flatten("", new Yaml().load(yaml), flat);
        return new TreeSet<>(flat.keySet());
    }
}
