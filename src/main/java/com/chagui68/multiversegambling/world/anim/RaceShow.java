package com.chagui68.multiversegambling.world.anim;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.Sound;

/**
 * A race track laid on the floor of an arena: one lane per horse, with a coloured runner
 * that creeps towards the golden finish line as the race is simulated.
 *
 * <p>The track runs away from the watcher, so the whole field is watched from above. The
 * game feeds it the position of every runner on each step, so what is seen is the real
 * race and not a decoration.</p>
 */
public final class RaceShow extends ArenaShow {

    /** Half depth of the track, in blocks: the start is at {@code +DEPTH}, the finish at {@code -DEPTH}. */
    private static final int DEPTH = 6;

    private final List<Material> colours;
    private final double[] ratios;
    private int[] placed;

    /**
     * @param colours colour of the runner of each lane
     * @param ticks   frames the race lasts
     */
    public RaceShow(MultiverseGamblingPlugin plugin, ArenaStage stage, List<Material> colours, int ticks) {
        super(plugin, stage, ticks);
        this.colours = List.copyOf(colours);
        this.ratios = new double[colours.size()];
        this.placed = new int[colours.size()];
        for (int lane = 0; lane < placed.length; lane++) {
            placed[lane] = Integer.MIN_VALUE;
        }
    }

    @Override
    protected void onStart() {
        int lanes = colours.size();
        for (int lane = 0; lane < lanes; lane++) {
            int x = laneX(lane, lanes);
            for (int dz = -DEPTH; dz <= DEPTH; dz++) {
                tile(x, 0, dz, Material.GRAY_CONCRETE);
            }
            tile(x, 0, -DEPTH - 1, Material.GOLD_BLOCK);
        }
        placeRunners();
    }

    @Override
    protected void onFrame(int elapsed, int duration) {
        // The game pushes the real positions; the show only keeps them apart, so the
        // runners do not look like they are all drawn on top of each other.
        placeRunners();
    }

    /** Moves the runners: 0 at the start, 1 on the finish line. */
    public void progress(double[] ratios) {
        int lanes = Math.min(this.ratios.length, ratios.length);
        System.arraycopy(ratios, 0, this.ratios, 0, lanes);
        placeRunners();
    }

    /** The race is over: the winner is crowned on the finish line. */
    public void finish(int winner) {
        if (winner < 0 || winner >= colours.size()) {
            return;
        }
        ratios[winner] = 1.0;
        placeRunners();
        tile(laneX(winner, colours.size()), 1, -DEPTH - 1, Material.GOLD_BLOCK);
        playSound(Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.2f);
    }

    private void placeRunners() {
        int lanes = colours.size();
        for (int lane = 0; lane < lanes; lane++) {
            int x = laneX(lane, lanes);
            int dz = DEPTH - (int) Math.round(Math.max(0, Math.min(1, ratios[lane])) * 2 * DEPTH);
            if (placed[lane] == dz) {
                continue;
            }
            if (placed[lane] != Integer.MIN_VALUE) {
                tile(x, 1, placed[lane], Material.AIR);
            }
            tile(x, 1, dz, colours.get(lane));
            placed[lane] = dz;
        }
    }

    private static int laneX(int lane, int lanes) {
        return 2 * lane - (lanes - 1);
    }
}
