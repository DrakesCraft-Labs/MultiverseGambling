package com.chagui68.multiversegambling.util;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** Todo el texto del plugin pasa por aqui: color, placeholders y formato de numeros. */
public final class Text {

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();

    private Text() {
    }

    /** Convierte "&aHola" en un Component. */
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

    /** Sustituye {clave} por su valor: {@code fill("Hola {player}", "player", nombre)}. */
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

    /** Numero con separador de miles y sin decimales si no hacen falta. */
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

    /** Barra de progreso tipo chat, util para ventanas de apuestas. */
    public static String bar(double ratio, int length, String filled, String empty) {
        int done = (int) Math.round(Math.max(0, Math.min(1, ratio)) * length);
        return filled.repeat(done) + empty.repeat(length - done);
    }

    /**
     * Boton de chat: al pulsarlo ejecuta un comando. Es lo que permite jugar
     * otra vez sin salir del chat.
     */
    public static Component button(String label, String command, String hover) {
        Component component = c(label).clickEvent(ClickEvent.runCommand(command));
        if (hover != null && !hover.isEmpty()) {
            component = component.hoverEvent(HoverEvent.showText(c(hover)));
        }
        return component;
    }

    /** Quita los codigos de color para medir o registrar texto plano. */
    public static String strip(String legacy) {
        return legacy == null ? "" : legacy.replaceAll("(?i)&[0-9a-fk-or]", "");
    }
}
