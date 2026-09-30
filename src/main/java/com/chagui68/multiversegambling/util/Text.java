package com.chagui68.multiversegambling.util;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** All plugin text goes through here: colours, placeholders and number formatting. */
public final class Text {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    private Text() {
    }

    /** Turns "&aHello" into a Component. */
    public static Component c(String legacy) {
        return LEGACY.deserialize(legacy == null ? "" : legacy);
    }

    public static List<Component> lines(List<String> raw) {
        List<Component> out = new ArrayList<>(raw.size());
        for (String line : raw) {
            out.add(c(line));
        }
        return out;
    }

    public static List<Component> lines(String... raw) {
        return lines(List.of(raw));
    }

    /** Replaces {key} with its value: {@code fill("Hi {player}", "player", name)}. */
    public static String fill(String template, Object... replacements) {
        if (template == null) {
            return "";
        }
        if (replacements == null || replacements.length < 2) {
            return template;
        }
        String out = template;
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            out = out.replace("{" + replacements[i] + "}", String.valueOf(replacements[i + 1]));
        }
        return out;
    }

    /** Same as {@link #fill(String, Object...)} for a whole block of lines. */
    public static List<String> fillAll(List<String> templates, Object... replacements) {
        List<String> out = new ArrayList<>(templates.size());
        for (String template : templates) {
            out.add(fill(template, replacements));
        }
        return out;
    }

    /** Number with thousand separators and no decimals when they are not needed. */
    public static String number(double value) {
        if (Math.abs(value - Math.rint(value)) < 1e-9) {
            return String.format("%,.0f", value);
        }
        return String.format("%,.2f", value);
    }

    public static String multiplier(double value) {
        return String.format("%,.2f", value) + "x";
    }

    public static String percent(double ratio) {
        return String.format("%.1f%%", ratio * 100.0);
    }

    /** Chat progress bar, handy for the betting windows. */
    public static String bar(double ratio, int length, String filled, String empty) {
        int done = (int) Math.round(Math.max(0, Math.min(1, ratio)) * length);
        return filled.repeat(done) + empty.repeat(length - done);
    }

    /**
     * Chat button: clicking it runs a command. This is what lets a player play
     * again without leaving the chat.
     */
    public static Component button(String label, String command, String hover) {
        Component component = c(label).clickEvent(ClickEvent.runCommand(command));
        if (hover != null && !hover.isEmpty()) {
            component = component.hoverEvent(HoverEvent.showText(c(hover)));
        }
        return component;
    }

    /** Strips colour codes so text can be measured or logged in plain form. */
    public static String strip(String legacy) {
        return legacy == null ? "" : legacy.replaceAll("(?i)&[0-9a-fk-or]", "");
    }
}
