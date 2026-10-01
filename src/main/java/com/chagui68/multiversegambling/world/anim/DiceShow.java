package com.chagui68.multiversegambling.world.anim;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.Rng;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A hand of dice thrown on a felt table: the dice fly in from the side, tumble and
 * bounce, and settle in a row one after the other with their pips facing the audience.
 *
 * <p>The faces come from the provably fair rolls of the game: the show only paints
 * them. While a die is in the air it is a blank tumbling cube; the pips are pressed on
 * its front face the moment it lands.</p>
 */
public final class DiceShow extends ArenaShow {

    /** Pips of each face, as offsets inside the 3 x 3 grid of the die. */
    private static final int[][][] PIPS = {
            {},                                                     // 0, unused
            {{0, 0}},                                               // 1
            {{-1, -1}, {1, 1}},                                     // 2
            {{-1, -1}, {0, 0}, {1, 1}},                             // 3
            {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}},                   // 4
            {{-1, -1}, {1, -1}, {0, 0}, {-1, 1}, {1, 1}},           // 5
            {{-1, -1}, {-1, 0}, {-1, 1}, {1, -1}, {1, 0}, {1, 1}}   // 6
    };

    /** Blocks between the middles of two dice. */
    private static final float SPACING = 1.9f;
    private static final float DIE = 0.9f;
    /** Height of the felt. */
    private static final float FELT_Y = 1.35f;
    private static final float DIE_Z = 0.3f;

    private final int[] faces;
    private final boolean[] landed;
    private final double[] tumbleX;
    private final double[] tumbleZ;
    private final List<ItemDisplay> dice = new ArrayList<>();
    private final List<BlockDisplay> pips = new ArrayList<>();
    private Location anchor;

    /**
     * @param faces the hand that was rolled, one value from 1 to 6 per die
     * @param ticks frames the throw lasts
     */
    public DiceShow(MultiverseGamblingPlugin plugin, ArenaStage stage, int[] faces, int ticks) {
        super(plugin, stage, ticks);
        this.faces = faces.clone();
        this.landed = new boolean[faces.length];
        this.tumbleX = new double[faces.length];
        this.tumbleZ = new double[faces.length];
        for (int i = 0; i < faces.length; i++) {
            tumbleX[i] = 0.6 + Rng.next() * 0.8;
            tumbleZ[i] = 0.5 + Rng.next() * 0.9;
        }
    }

    @Override
    protected void onStart() {
        anchor = local(0, 0, 0);
        float width = Math.max(6.0f, faces.length * SPACING + 2.0f);
        // Felt, rim and legs.
        block(anchor, Material.GREEN_WOOL, Props.box(new Vector3f(0, FELT_Y - 0.15f, 0), new Vector3f(width, 0.3f, 3.6f)));
        block(anchor, Material.DARK_OAK_PLANKS, Props.box(new Vector3f(0, FELT_Y - 0.05f, -1.95f), new Vector3f(width + 0.6f, 0.5f, 0.3f)));
        block(anchor, Material.DARK_OAK_PLANKS, Props.box(new Vector3f(0, FELT_Y - 0.05f, 1.95f), new Vector3f(width + 0.6f, 0.5f, 0.3f)));
        for (int side : new int[]{-1, 1}) {
            block(anchor, Material.DARK_OAK_PLANKS,
                    Props.box(new Vector3f(side * (width / 2 + 0.15f), FELT_Y - 0.05f, 0), new Vector3f(0.3f, 0.5f, 3.6f)));
            for (int end : new int[]{-1, 1}) {
                block(anchor, Material.POLISHED_BLACKSTONE, Props.box(
                        new Vector3f(side * (width / 2 - 0.6f), (FELT_Y - 0.3f) / 2, end * 1.3f),
                        new Vector3f(0.4f, FELT_Y - 0.3f, 0.4f)));
            }
        }
        block(anchor, Material.GOLD_BLOCK, Props.box(new Vector3f(0, FELT_Y + 0.21f, 1.95f), new Vector3f(width + 0.6f, 0.06f, 0.32f)));
        for (int die = 0; die < faces.length; die++) {
            dice.add(item(anchor, Material.WHITE_CONCRETE, diePose(throwStart(die), Props.none())));
        }
        playSound(Sound.ENTITY_ITEM_PICKUP, 0.8f, 0.6f);
    }

    @Override
    protected void onFrame(int elapsed, int duration) {
        for (int die = 0; die < faces.length; die++) {
            if (landed[die]) {
                continue;
            }
            int settle = settleFrame(die, duration);
            if (elapsed >= settle) {
                land(die);
                continue;
            }
            double flight = Math.min(1, elapsed / (double) settle);
            Vector3f from = throwStart(die);
            Vector3f to = restingSpot(die);
            Vector3f spot = new Vector3f(from).lerp(to, (float) WheelMath.ease(flight));
            // Two bounces on the felt, each lower than the one before.
            double bounce = Math.abs(Math.sin(flight * Math.PI * 2.5)) * 2.4 * (1 - flight);
            spot.y = (float) Math.max(to.y, to.y + bounce + (from.y - to.y) * (1 - flight) * (1 - flight));
            double spin = (1 - flight) * 12;
            Quaternionf tumble = new Quaternionf().rotateX((float) (spin * tumbleX[die]))
                    .rotateZ((float) (spin * tumbleZ[die]));
            Props.animate(dice.get(die), diePose(spot, tumble), 1);
            if (bounce < 0.08 && elapsed % 3 == 0) {
                playSound(Sound.BLOCK_WOOD_HIT, 0.5f, 1.4f);
            }
        }
    }

    /**
     * The die stops square on the felt and shows its face.
     */
    private void land(int die) {
        if (landed[die]) {
            return;
        }
        landed[die] = true;
        Vector3f spot = restingSpot(die);
        ItemDisplay cube = dice.get(die);
        Props.animate(cube, diePose(spot, Props.none()), 3);
        float pitch = DIE * 0.28f;
        for (int[] pip : pips(faces[die])) {
            Vector3f centre = new Vector3f(spot.x + pip[0] * pitch, spot.y - pip[1] * pitch, spot.z + DIE / 2 + 0.02f);
            pips.add(block(anchor, Material.BLACK_CONCRETE, Props.box(centre, new Vector3f(0.17f, 0.17f, 0.04f))));
        }
        particles(Particle.CRIT, spot.x, spot.y, spot.z, 8, 0.3, 0.05);
        playSound(Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 0.9f + die * 0.15f);
    }

    @Override
    protected void onSettle() {
        for (int die = 0; die < faces.length; die++) {
            land(die);
        }
        for (ItemDisplay cube : dice) {
            if (cube.isValid()) {
                cube.setGlowColorOverride(Color.fromRGB(0xFFD700));
                cube.setGlowing(true);
            }
        }
        celebrate(0, FELT_Y + 1.0, DIE_Z);
        playSound(Sound.BLOCK_NOTE_BLOCK_BELL, 0.9f, 1.3f);
    }

    /** Die {@code index} lands at that share of the throw, left to right. */
    private int settleFrame(int index, int duration) {
        return (int) (duration * (0.4 + 0.4 * index / Math.max(1.0, faces.length - 1.0)));
    }

    private Vector3f throwStart(int die) {
        return new Vector3f(5.5f + die * 0.3f, 4.2f, 1.2f);
    }

    private Vector3f restingSpot(int die) {
        return new Vector3f(centerOf(die), FELT_Y + DIE / 2, DIE_Z);
    }

    private Transformation diePose(Vector3f centre, Quaternionf rotation) {
        return Props.centred(centre, rotation, new Vector3f(DIE, DIE, DIE));
    }

    /** Offsets of the pips of a face inside the 3 x 3 grid of its die. */
    static int[][] pips(int face) {
        return PIPS[Math.max(1, Math.min(6, face))];
    }

    private float centerOf(int index) {
        return (index - (faces.length - 1) / 2.0f) * SPACING;
    }
}
