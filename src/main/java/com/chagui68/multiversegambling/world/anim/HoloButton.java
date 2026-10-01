package com.chagui68.multiversegambling.world.anim;

import com.chagui68.multiversegambling.util.Text;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.joml.Vector3f;

/**
 * A button floating in the air: a text display with a coloured background, and an
 * invisible interaction entity of the same size that takes the clicks.
 *
 * <p>Both right and left clicks press it. A button can belong to one player (the one
 * whose round it drives), in which case everybody else is told it is not theirs. The
 * button never decides anything by itself: it only calls back into the game, which
 * checks the state of the round exactly like it does for a menu click.</p>
 */
public final class HoloButton {

    /** Scoreboard tag of the hitbox of every button. */
    public static final String TAG = "mvgam_button";

    /** Milliseconds a button ignores clicks after one went through. */
    private static final long DEBOUNCE_MILLIS = 300;

    private static final Map<UUID, HoloButton> BY_HITBOX = new ConcurrentHashMap<>();

    private final ArenaShow show;
    private final TextDisplay label;
    private final Interaction hitbox;
    private final UUID owner;
    private final Consumer<Player> action;
    private final float scale;
    private Color background;
    private boolean enabled = true;
    private long lastPress;

    private HoloButton(ArenaShow show, TextDisplay label, Interaction hitbox, UUID owner, Consumer<Player> action,
                       float scale, Color background) {
        this.show = show;
        this.label = label;
        this.hitbox = hitbox;
        this.owner = owner;
        this.action = action;
        this.scale = scale;
        this.background = background;
    }

    /**
     * Places a button whose label sits on {@code spot} (the bottom middle of the text).
     *
     * @param owner the only player who can press it, or {@code null} for anybody
     */
    static HoloButton create(ArenaShow show, Location spot, Component text, Color background, float scale,
                             UUID owner, Consumer<Player> action) {
        TextDisplay label = show.spawn(spot, TextDisplay.class, display -> {
            display.text(text);
            display.setBillboard(Display.Billboard.VERTICAL);
            display.setBackgroundColor(background);
            display.setShadowed(true);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setLineWidth(200);
            display.setSeeThrough(false);
            display.setTransformation(Props.centred(new Vector3f(), scale));
        });
        float width = widthOf(text, scale);
        float height = heightOf(text, scale);
        Interaction hitbox = show.spawn(spot.clone().add(0, -0.04, 0), Interaction.class, entity -> {
            entity.setInteractionWidth(width);
            entity.setInteractionHeight(height);
            entity.setResponsive(true);
            entity.addScoreboardTag(TAG);
        });
        HoloButton button = new HoloButton(show, label, hitbox, owner, action, scale, background);
        BY_HITBOX.put(hitbox.getUniqueId(), button);
        show.track(button);
        return button;
    }

    /**
     * Handles a click on any entity.
     *
     * @return true when the entity was a button, so the event must be cancelled
     */
    public static boolean press(Player player, Entity entity) {
        if (entity == null || !entity.getScoreboardTags().contains(TAG)) {
            return false;
        }
        HoloButton button = BY_HITBOX.get(entity.getUniqueId());
        if (button == null || !button.alive()) {
            return true;
        }
        button.pressedBy(player);
        return true;
    }

    private void pressedBy(Player player) {
        long now = System.currentTimeMillis();
        if (now - lastPress < DEBOUNCE_MILLIS) {
            return;
        }
        lastPress = now;
        if (owner != null && !owner.equals(player.getUniqueId())) {
            player.sendActionBar(Text.c("&c✖"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
            return;
        }
        if (!enabled) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
            return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
        // A little push and a flash, so the click is felt.
        Props.animate(label, Props.centred(new Vector3f(0, -0.03f, 0), scale * 0.88f), 2);
        label.setBackgroundColor(Color.fromARGB(230, 255, 255, 255));
        show.later(3, () -> {
            if (label.isValid()) {
                Props.animate(label, Props.centred(new Vector3f(), scale), 3);
                label.setBackgroundColor(enabled ? background : Color.fromARGB(110, 40, 40, 40));
            }
        });
        action.accept(player);
    }

    /**
     * Changes the text, keeping the size of the hitbox.
     */
    public void text(Component text) {
        if (label.isValid()) {
            label.text(text);
        }
    }

    /**
     * Changes the colour behind the text.
     */
    public void background(Color colour) {
        this.background = colour;
        if (label.isValid() && enabled) {
            label.setBackgroundColor(colour);
        }
    }

    /**
     * A disabled button stays on screen, greyed out, and ignores clicks.
     */
    public void enabled(boolean enabled) {
        this.enabled = enabled;
        if (label.isValid()) {
            label.setBackgroundColor(enabled ? background : Color.fromARGB(110, 40, 40, 40));
            label.setTextOpacity(enabled ? (byte) -1 : (byte) 110);
        }
    }

    /**
     * Takes the button away before the end of the show.
     */
    public void remove() {
        BY_HITBOX.remove(hitbox.getUniqueId());
        if (label.isValid()) {
            label.remove();
        }
        if (hitbox.isValid()) {
            hitbox.remove();
        }
    }

    boolean alive() {
        return label.isValid() && hitbox.isValid();
    }

    void forget() {
        BY_HITBOX.remove(hitbox.getUniqueId());
    }

    /**
     * What a button needs to show its row of buttons: its text, colour, size and click.
     */
    public record Spec(Component text, Color background, float scale, Consumer<Player> action) {
    }

    /**
     * Width of the label (and of the hitbox) of a button with that text, in blocks. Bold
     * letters are a pixel wider, so every letter is counted at the bold width.
     */
    public static float widthOf(Component text, float scale) {
        int characters = Math.max(3, longestLine(Text.strip(plain(text))));
        return Math.max(0.7f, characters * 0.175f * scale + 0.3f);
    }

    static float heightOf(Component text, float scale) {
        int lines = Math.max(1, plain(text).split("\n").length);
        return lines * 0.27f * scale + 0.12f;
    }

    private static String plain(Component text) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(text);
    }

    private static int longestLine(String text) {
        int longest = 0;
        for (String line : text.split("\n")) {
            longest = Math.max(longest, line.length());
        }
        return longest;
    }

    /** Background colours used by the games, so every control looks the same. */
    public static final Color GREEN = Color.fromARGB(200, 30, 140, 50);
    public static final Color RED = Color.fromARGB(200, 170, 30, 30);
    public static final Color GOLD = Color.fromARGB(210, 190, 140, 10);
    public static final Color BLUE = Color.fromARGB(200, 30, 80, 170);
    public static final Color GREY = Color.fromARGB(190, 60, 60, 60);
    public static final Color PURPLE = Color.fromARGB(200, 110, 40, 160);
}
