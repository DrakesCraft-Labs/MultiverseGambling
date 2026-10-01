package com.chagui68.multiversegambling.util;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Logger;

/**
 * The small JSON files the casino keeps in its data folder: the wallet, the statistics,
 * the seeds and the language choices.
 *
 * <p>Reading is deliberately forgiving. These files are written by an asynchronous task,
 * so a killed server can leave one half written, and an administrator may edit one by
 * hand until it stops being JSON. Neither case may stop the plugin from enabling, so an
 * unreadable file is moved aside as {@code <name>.corrupt} and the plugin continues with
 * an empty one; the old copy stays on disk to be inspected. Writing is equally patient:
 * a failure is logged and never thrown, so one unwritable file cannot stop the others
 * from being saved.</p>
 */
public final class JsonStore {

    private JsonStore() {
    }

    /**
     * Reads one file.
     *
     * @param logger where a broken file is reported
     * @return the parsed value, or {@code null} when the file is missing or unusable
     */
    public static <T> T read(Logger logger, File file, Gson gson, Type type) {
        if (!file.isFile()) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            return gson.fromJson(reader, type);
        } catch (IOException | JsonParseException error) {
            quarantine(logger, file, error);
            return null;
        }
    }

    /**
     * Writes one file, creating the data folder when it is missing. Safe to call from an
     * asynchronous thread.
     */
    public static void write(Logger logger, File file, Gson gson, Type type, Object value) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                logger.warning("Could not create the plugin data folder: " + parent.getPath());
            }
            try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                gson.toJson(value, type, writer);
            }
        } catch (IOException | RuntimeException error) {
            logger.warning("Could not save " + file.getName() + ": " + error.getMessage());
        }
    }

    /**
     * Moves an unreadable file out of the way so the next save starts from a clean one.
     */
    private static void quarantine(Logger logger, File file, Throwable error) {
        File broken = file.toPath().resolveSibling(file.getName() + ".corrupt").toFile();
        boolean moved = !broken.exists() && file.renameTo(broken);
        logger.warning("Could not read " + file.getName() + " (" + error.getMessage() + "); "
                + (moved ? "the broken file was moved to " + broken.getName()
                        : "the broken file was left in place")
                + " and the plugin continues with an empty one.");
    }
}
