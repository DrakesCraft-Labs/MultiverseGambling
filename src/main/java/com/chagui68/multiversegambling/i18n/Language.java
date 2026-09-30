package com.chagui68.multiversegambling.i18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Languages shipped with the plugin.
 *
 * <p>Every language has its own {@code lang/<code>.yml} file. Adding a new one is
 * a two step job: drop the file in {@code lang/} and add the constant here. Admins
 * can also drop extra files without touching the code, the plugin loads every
 * {@code .yml} it finds in the folder.</p>
 */
public enum Language {

    EN("en", "English", "English"),
    ES("es", "Español", "Spanish");

    private final String code;
    private final String nativeName;
    private final String englishName;

    Language(String code, String nativeName, String englishName) {
        this.code = code;
        this.nativeName = nativeName;
        this.englishName = englishName;
    }

    /**
     * Two letter code, also the name of the message file.
     */
    public String code() {
        return code;
    }

    /**
     * Name written in the language itself, for the language picker.
     */
    public String nativeName() {
        return nativeName;
    }

    public String englishName() {
        return englishName;
    }

    public String fileName() {
        return code + ".yml";
    }

    /**
     * Resolves loose input into a language: {@code "es"}, {@code "ES"},
     * {@code "es_es"}, {@code "es-AR"}, {@code "spanish"} and {@code "español"}
     * all give {@link #ES}.
     *
     * @return the language, or {@code null} when nothing matches
     */
    public static Language match(String input) {
        if (input == null) {
            return null;
        }
        String needle = input.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (needle.isEmpty()) {
            return null;
        }
        int cut = needle.indexOf('_');
        String base = cut > 0 ? needle.substring(0, cut) : needle;
        for (Language language : values()) {
            if (base.equals(language.code)
                    || needle.equals(language.nativeName.toLowerCase(Locale.ROOT))
                    || needle.equals(language.englishName.toLowerCase(Locale.ROOT))) {
                return language;
            }
        }
        return null;
    }

    /**
     * Like {@link #match(String)} but never fails: unknown input means English.
     */
    public static Language of(String input) {
        Language match = match(input);
        return match == null ? EN : match;
    }

    /**
     * True when the input names a language the plugin ships.
     */
    public static boolean isShipped(String input) {
        return match(input) != null;
    }

    /**
     * Every shipped code, in declaration order.
     */
    public static List<String> codes() {
        List<String> codes = new ArrayList<>(values().length);
        for (Language language : values()) {
            codes.add(language.code);
        }
        return List.copyOf(codes);
    }
}
