package com.chagui68.multiversegambling.world.anim;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;

/**
 * A number line laid on the floor of an arena: 0 on the left, 100 on the right, with the
 * target of the bet marked in red and a marker that slides up the line and stops on the
 * number that really came up.
 *
 * <p>It is the dice game with a shape: you can see at a glance how close the roll came to
 * the line you needed, and the stretch travelled is left green when the bet won and red
 * when it did not.</p>
 */
public final class DiceTrackShow extends ArenaShow {

    /** Half length of the track, in blocks: 0 sits at {@code -HALF}, 100 at {@code +HALF}. */
    private static final int HALF = 10;
    /** Height of the marker over the track. */
    private static final double MARKER_HEIGHT = 1.2;

    private final double target;
    private final double result;
    private final boolean won;
    private ItemDisplay marker;

    public DiceTrackShow(MultiverseGamblingPlugin plugin, ArenaStage stage,
                         double target, double result, boolean won, int ticks) {
        super(plugin, stage, ticks);
        this.target = clamp(target);
        this.result = clamp(result);
        this.won = won;
    }

    @Override
    protected void onStart() {
        for (int step = -HALF; step <= HALF; step++) {
            tile(step, 0, 0, step % 5 == 0 ? Material.POLISHED_DEEPSLATE
                    : Material.POLISHED_BLACKSTONE);
        }
        tile((int) Math.round(xOf(target)), 0, 0, Material.RED_CONCRETE);
        marker = spawn(stage().at(-HALF, MARKER_HEIGHT, 0), ItemDisplay.class, display -> {
            display.setItemStack(new ItemStack(Material.FIREWORK_STAR));
            display.setBillboard(Display.Billboard.CENTER);
            display.setGravity(false);
            display.setInvulnerable(true);
        });
    }

    @Override
    protected void onFrame(int elapsed, int duration) {
        // The marker shoots up the line and creeps into its final number.
        double travelled = -HALF + (xOf(result) + HALF) * WheelMath.ease(progress(elapsed));
        if (marker != null && marker.isValid()) {
            marker.teleport(stage().at(travelled, MARKER_HEIGHT, 0));
        }
        if (elapsed % 3 == 0) {
            playSound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, (float) (1.0 + progress(elapsed)));
        }
    }

    @Override
    protected void onSettle() {
        int landing = (int) Math.round(xOf(result));
        int mark = (int) Math.round(xOf(target));
        int from = Math.min(landing, mark);
        int to = Math.max(landing, mark);
        for (int step = from; step <= to; step++) {
            tile(step, 0, 0, won ? Material.LIME_CONCRETE : Material.RED_CONCRETE);
        }
        tile(mark, 0, 0, Material.RED_CONCRETE);
        tile(landing, 1, 0, won ? Material.LIME_CONCRETE : Material.RED_CONCRETE);
        playSound(won ? Sound.BLOCK_NOTE_BLOCK_BELL : Sound.BLOCK_ANVIL_LAND, 0.8f, 1.1f);
    }

    /** Block position of a number from 0 to 100. */
    private double xOf(double value) {
        return value / 100.0 * (2 * HALF) - HALF;
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(100, value));
    }
}
