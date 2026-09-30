package com.chagui68.multiversegambling.world.anim;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;

/**
 * A wheel drawn on an arena: a round table of coloured sectors inside a golden rim,
 * with a white ball that hurtles around it, slows down and settles on the winning
 * sector.
 *
 * <p>It is shared by every game decided by a wheel (the roulette, the lucky wheel and
 * the group colour roulette): the game only says which colour each sector has and which
 * one wins. The winner is always handed over already decided by the provably fair
 * generator, so a show only ever paints a result. The table is built when the spin
 * starts and removed when it stops, leaving the arena back to its plain platform.</p>
 */
public final class WheelShow extends ArenaShow {

    /**
     * Distance from the centre at which the sectors sit.
     */
    private static final double SECTOR_RADIUS = 8.0;
    /**
     * Golden rim, just outside the sectors.
     */
    private static final double RIM_RADIUS = 9.0;
    /**
     * Whole turns before the ball drops on the winner.
     */
    private static final int TURNS = 6;
    /**
     * Height of the ball over the table.
     */
    private static final double BALL_HEIGHT = 1.7;
    /**
     * Share of the show spent spinning; the rest is the ball sitting on the winner, so
     * the landing is seen before the table is taken down.
     */
    private static final double SPIN_SHARE = 0.75;

    private final List<Material> sectors;
    private final int winner;
    private ItemDisplay ball;
    private double startAngle;
    private double arc;

    /**
     * @param sectors colour of each sector, in the order they sit round the wheel
     * @param winner  index of the sector the ball has to land on
     * @param ticks   frames the spin lasts
     */
    public WheelShow(MultiverseGamblingPlugin plugin, ArenaStage stage,
                     List<Material> sectors, int winner, int ticks) {
        super(plugin, stage, ticks);
        this.sectors = List.copyOf(sectors);
        this.winner = sectors.isEmpty() ? 0 : Math.floorMod(winner, sectors.size());
    }

    @Override
    protected void onStart() {
        drawTable();
        ball = spawn(stage().at(0, BALL_HEIGHT, SECTOR_RADIUS), ItemDisplay.class, display -> {
            display.setItemStack(new ItemStack(Material.SNOWBALL));
            display.setBillboard(Display.Billboard.CENTER);
            display.setGravity(false);
            display.setInvulnerable(true);
        });
        // Start a bit further along the ring, so the ball does not always set off from
        // the same sector.
        startAngle = WheelMath.normalize(WheelMath.angleOf(0, sectors.size()) + WheelMath.TAU * 0.42);
        arc = WheelMath.sweep(startAngle, WheelMath.angleOf(winner, sectors.size()), TURNS);
    }

    @Override
    protected void onFrame(int elapsed, int duration) {
        double spin = Math.min(1, progress(elapsed) / SPIN_SHARE);
        placeBall(startAngle + arc * WheelMath.ease(spin));
        if (spin < 1 && elapsed % 2 == 0) {
            playSound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 0.9f + (float) spin);
        }
    }

    @Override
    protected void onSettle() {
        placeBall(WheelMath.angleOf(winner, sectors.size()));
        playSound(Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.4f);
    }

    private void placeBall(double angle) {
        if (ball == null || !ball.isValid()) {
            return;
        }
        ball.teleport(stage().at(
                WheelMath.x(angle, SECTOR_RADIUS), BALL_HEIGHT, WheelMath.z(angle, SECTOR_RADIUS)));
    }

    private void drawTable() {
        int centerX = stage().arena().centerX();
        int centerZ = stage().arena().centerZ();
        int y = stage().floorY() + 1;
        disc(0, SECTOR_RADIUS, Material.POLISHED_BLACKSTONE);
        ring(0, RIM_RADIUS, Material.GOLD_BLOCK);
        int count = sectors.size();
        for (int index = 0; index < count; index++) {
            double angle = WheelMath.angleOf(index, count);
            int x = centerX + (int) Math.round(WheelMath.x(angle, SECTOR_RADIUS));
            int z = centerZ + (int) Math.round(WheelMath.z(angle, SECTOR_RADIUS));
            paint().set(x, y, z, sectors.get(index));
        }
    }
}
