package com.chagui68.multiversegambling.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The data files are written from an asynchronous task, so a crash can leave one half
 * written; whatever the file says, the plugin has to start. A hand edited file that
 * stopped being JSON counts the same.
 */
class JsonStoreTest {

    private static final Type MAP_TYPE = new TypeToken<Map<String, Double>>() {
    }.getType();

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Logger logger = quietLogger();

    private static Logger quietLogger() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        return logger;
    }

    @Test
    void readsAFileThatWasWrittenCorrectly(@TempDir Path folder) throws IOException {
        File file = folder.resolve("balances.json").toFile();
        Files.writeString(file.toPath(), "{\"1\": 250.5}", StandardCharsets.UTF_8);
        Map<String, Double> balances = JsonStore.read(logger, file, gson, MAP_TYPE);
        assertEquals(Double.valueOf(250.5), balances.get("1"));
    }

    @Test
    void aMissingFileIsSimplyEmpty(@TempDir Path folder) {
        File file = folder.resolve("balances.json").toFile();
        assertNull(JsonStore.read(logger, file, gson, MAP_TYPE));
        assertFalse(folder.resolve("balances.json.corrupt").toFile().exists(),
                "nothing should be moved aside for a file that does not exist");
    }

    @Test
    void aHalfWrittenFileIsMovedAsideAndIgnored(@TempDir Path folder) throws IOException {
        File file = folder.resolve("balances.json").toFile();
        // Exactly what a killed server leaves behind: the beginning of an object.
        Files.writeString(file.toPath(), "{\"1\": 250.5", StandardCharsets.UTF_8);
        assertNull(JsonStore.read(logger, file, gson, MAP_TYPE));
        assertTrue(folder.resolve("balances.json.corrupt").toFile().exists(),
                "the broken file was thrown away instead of being kept");
        assertFalse(file.exists(), "the broken file is still in the way of the next save");
    }

    @Test
    void aFileFullOfNonsenseDoesNotThrow(@TempDir Path folder) throws IOException {
        File file = folder.resolve("stats.json").toFile();
        Files.writeString(file.toPath(), "not json at all", StandardCharsets.UTF_8);
        assertNull(JsonStore.read(logger, file, gson, MAP_TYPE));
        assertTrue(folder.resolve("stats.json.corrupt").toFile().exists());
    }

    @Test
    void writingCreatesTheFolderAndReadsBackClean(@TempDir Path folder) {
        File file = folder.resolve("nested").resolve("balances.json").toFile();
        JsonStore.write(logger, file, gson, MAP_TYPE, Map.of("1", 12.0));
        assertTrue(file.isFile(), "the data folder was not created");
        Map<String, Double> balances = JsonStore.read(logger, file, gson, MAP_TYPE);
        assertEquals(Double.valueOf(12.0), balances.get("1"));
        assertFalse(file.toPath().resolveSibling("balances.json.corrupt").toFile().exists());
    }
}
