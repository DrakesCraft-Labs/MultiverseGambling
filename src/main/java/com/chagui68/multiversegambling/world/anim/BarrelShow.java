package com.chagui68.multiversegambling.world.anim;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.Rng;
import org.bukkit.Material;
import org.bukkit.Sound;

/**
 * The cylinder of the revolver as a prop: a ring of chambers drawn on the arena floor,
 * the loaded ones in red, so everyone can see the odds while they decide whether to pull
 * the trigger.
 *
 * <p>Every pull turns the cylinder a few notches. Whether the shot fires was already
 * decided by the provably fair generator; the show only turns it into a bang or a click.
 * The chambers move around the ring so the red ones are never in the same place twice.</p>
 */
public final class BarrelShow extends ArenaShow {

    /** Radius of the ring of chambers, in blocks. */
    private static final int RADIUS = 3;

    private final int chambers;
    private final int bullets;
    private int offset;

    public BarrelShow(MultiverseGamblingPlugin plugin, ArenaStage stage, int chambers, int bullets, int ticks) {
        super(plugin, stage, ticks);
        this.chambers = Math.max(2, chambers);
        this.bullets = Math.max(0, Math.min(bullets, this.chambers - 1));
    }

    @Override
    protected void onStart() {
        draw(false);
    }

    /** Somebody pulled the trigger: the cylinder turns and the shot either fires or does not. */
    public void pull(boolean fires) {
        offset = (offset + 1 + Rng.intBetween(0, chambers - 1)) % chambers;
        draw(fires);
        if (fires) {
            playSound(Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
        } else {
            playSound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 0.6f);
        }
    }

    private void draw(boolean fires) {
        for (int chamber = 0; chamber < chambers; chamber++) {
            double angle = WheelMath.angleOf(chamber, chambers);
            int dx = (int) Math.round(WheelMath.x(angle, RADIUS));
            int dz = (int) Math.round(WheelMath.z(angle, RADIUS));
            boolean loaded = Math.floorMod(chamber - offset, chambers) < bullets;
            tile(dx, 1, dz, loaded ? Material.RED_CONCRETE : Material.IRON_BLOCK);
        }
        // The middle is the barrel end, in front of the hammer.
        tile(0, 1, 0, fires ? Material.RED_CONCRETE : Material.POLISHED_DEEPSLATE);
        tile(0, 2, 0, fires ? Material.ORANGE_CONCRETE : Material.AIR);
    }
}
