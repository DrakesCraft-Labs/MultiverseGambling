package com.chagui68.multiversegambling.world.anim;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.Rng;
import com.chagui68.multiversegambling.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The dice game as a scoreboard: a bar from 0 to 100 split in the zone that wins and the
 * zone that loses, the target marked on it, a die that tumbles along the bar and a big
 * counter that spins through numbers until it stops on the real roll.
 *
 * <p>You can see at a glance how close the roll came to the line you needed. The roll
 * is drawn by the provably fair generator before the die is thrown; the show only
 * paints it.</p>
 */
public final class DiceTrackShow extends ArenaShow {

    /** Half length of the bar: 0 sits at {@code -HALF}, 100 at {@code +HALF}. */
    private static final float HALF = 6.5f;
    private static final float BAR_Y = 3.0f;
    private static final float DIE_Y = 3.85f;
    /** Share of the show the die spends travelling. */
    private static final double ROLL_SHARE = 0.78;

    private final double target;
    private final double result;
    private final boolean won;
    private final boolean over;

    private final List<BlockDisplay> bulbs = new ArrayList<>();
    private Location anchor;
    private ItemDisplay die;
    private TextDisplay counter;
    private boolean revealed;

    /**
     * @param over true when the bet wins above the target, false below it
     */
    public DiceTrackShow(MultiverseGamblingPlugin plugin, ArenaStage stage,
                         double target, double result, boolean won, boolean over, int ticks) {
        super(plugin, stage, ticks);
        this.target = clamp(target);
        this.result = clamp(result);
        this.won = won;
        this.over = over;
    }

    @Override
    protected void onStart() {
        anchor = local(0, 0, 0);
        // The board: a dark panel in a golden frame on two legs.
        block(anchor, Material.BLACK_CONCRETE, Props.box(new Vector3f(0, 3.7f, -0.35f), new Vector3f(15.4f, 4.8f, 0.3f)));
        block(anchor, Material.GOLD_BLOCK, Props.box(new Vector3f(0, 6.15f, -0.35f), new Vector3f(15.8f, 0.16f, 0.4f)));
        block(anchor, Material.GOLD_BLOCK, Props.box(new Vector3f(0, 1.25f, -0.35f), new Vector3f(15.8f, 0.16f, 0.4f)));
        for (int side : new int[]{-1, 1}) {
            block(anchor, Material.GOLD_BLOCK,
                    Props.box(new Vector3f(side * 7.85f, 3.7f, -0.35f), new Vector3f(0.16f, 5.06f, 0.4f)));
            block(anchor, Material.POLISHED_BLACKSTONE_BRICKS,
                    Props.box(new Vector3f(side * 6.0f, 0.6f, -0.35f), new Vector3f(0.5f, 1.2f, 0.5f)));
        }
        for (int i = 0; i <= 30; i++) {
            float x = -7.5f + i * 0.5f;
            bulbs.add(block(anchor, Material.GLOWSTONE, Props.box(new Vector3f(x, 6.15f, -0.1f),
                    new Vector3f(0.18f, 0.18f, 0.1f))));
        }

        // The bar: losing zone in red, winning zone in green, split at the target.
        float split = xOf(target);
        float left = -HALF;
        float right = HALF;
        Material low = over ? Material.RED_CONCRETE : Material.LIME_CONCRETE;
        Material high = over ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
        if (split - left > 0.01f) {
            block(anchor, low, Props.box(new Vector3f((left + split) / 2, BAR_Y, -0.1f),
                    new Vector3f(split - left, 0.5f, 0.2f)));
        }
        if (right - split > 0.01f) {
            block(anchor, high, Props.box(new Vector3f((split + right) / 2, BAR_Y, -0.1f),
                    new Vector3f(right - split, 0.5f, 0.2f)));
        }
        for (int value = 0; value <= 100; value += 25) {
            float x = xOf(value);
            block(anchor, Material.WHITE_CONCRETE, Props.box(new Vector3f(x, BAR_Y - 0.4f, -0.1f),
                    new Vector3f(0.05f, 0.25f, 0.05f)));
            text(local(x, BAR_Y - 1.0f, -0.15f), Text.c("&7" + value), Props.scaled(0.9f), Display.Billboard.FIXED);
        }
        // Target: a white line across the bar and its condition over it.
        block(anchor, Material.WHITE_CONCRETE, Props.box(new Vector3f(split, BAR_Y, 0.02f), new Vector3f(0.09f, 0.9f, 0.05f)));
        text(local(split, BAR_Y + 0.5f, 0.0f), Text.c("&f" + (over ? "> " : "< ") + format(target)),
                Props.scaled(0.8f), Display.Billboard.FIXED);

        die = item(anchor, Material.WHITE_CONCRETE, diePose(-HALF, Props.none(), 0.75f));
        counter = text(local(0, 4.75f, -0.1f), Text.c("&f00.00"), Props.scaled(2.6f), Display.Billboard.FIXED);
        playSound(Sound.ENTITY_ITEM_PICKUP, 0.8f, 0.7f);
    }

    @Override
    protected void onFrame(int elapsed, int duration) {
        if (revealed) {
            return;
        }
        double roll = Math.min(1, elapsed / (duration * ROLL_SHARE));
        if (roll >= 1) {
            reveal();
            return;
        }
        double eased = WheelMath.ease(roll);
        float x = (float) (-HALF + (xOf(result) + HALF) * eased);
        // Tumbles while it is fast, then rolls slower and slower.
        double spin = (1 - eased) * 9.0;
        Quaternionf tumble = new Quaternionf().rotateZ((float) (-spin * 2.2)).rotateX((float) (spin * 1.3));
        float hop = (float) (Math.abs(Math.sin(roll * Math.PI * 5)) * 0.9 * (1 - eased));
        Props.animate(die, diePose(x, tumble, 0.75f, hop), 1);
        if (elapsed % 2 == 0 && counter != null && counter.isValid()) {
            counter.text(Text.c("&f" + format(Rng.next() * 100)));
            playSound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.35f, (float) (0.9 + roll));
        }
    }

    private void reveal() {
        if (revealed) {
            return;
        }
        revealed = true;
        float landing = xOf(result);
        Props.animate(die, diePose(landing, Props.none(), 0.85f), 3);
        die.setGlowColorOverride(won ? Color.LIME : Color.RED);
        die.setGlowing(true);
        if (counter != null && counter.isValid()) {
            counter.text(Text.c((won ? "&a&l" : "&c&l") + format(result)));
            Props.animate(counter, Props.scaled(3.4f), 4);
        }
        block(anchor, won ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                Props.box(new Vector3f(landing, BAR_Y, 0.05f), new Vector3f(0.12f, 1.1f, 0.06f)))
                .setGlowing(true);
        if (won) {
            celebrate(landing, DIE_Y, 0.4);
        } else {
            particles(Particle.LARGE_SMOKE, landing, DIE_Y, 0.4, 12, 0.3, 0.02);
            playSound(Sound.BLOCK_ANVIL_LAND, 0.6f, 1.2f);
        }
    }

    @Override
    protected void onSettle() {
        reveal();
    }

    @Override
    protected void onAnimate(int age) {
        if (age % 3 != 0) {
            return;
        }
        int phase = age / 3;
        for (int i = 0; i < bulbs.size(); i++) {
            BlockDisplay bulb = bulbs.get(i);
            if (bulb.isValid()) {
                boolean lit = revealed ? (phase % 2 == 0) : (i + phase) % 4 == 0;
                Material on = revealed ? (won ? Material.LIME_CONCRETE : Material.RED_CONCRETE) : Material.GLOWSTONE;
                bulb.setBlock((lit ? on : Material.BLACK_CONCRETE).createBlockData());
            }
        }
    }

    private Transformation diePose(float x, Quaternionf rotation, float size) {
        return diePose(x, rotation, size, 0.0f);
    }

    private Transformation diePose(float x, Quaternionf rotation, float size, float hop) {
        return Props.centred(new Vector3f(x, DIE_Y + hop, 0.35f), rotation, new Vector3f(size, size, size));
    }

    /** Position along the bar of a number from 0 to 100. */
    private static float xOf(double value) {
        return (float) (value / 100.0 * (2 * HALF) - HALF);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%05.2f", value);
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(100, value));
    }
}
