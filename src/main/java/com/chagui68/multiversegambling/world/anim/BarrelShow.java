package com.chagui68.multiversegambling.world.anim;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.Rng;
import com.chagui68.multiversegambling.util.Text;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A giant revolver for the russian roulette: the cylinder faces the audience with its
 * chambers in a ring, the loaded ones showing a brass bullet, so everyone can see the
 * odds while they wait for the trigger.
 *
 * <p>Every pull spins the cylinder, slows it down and stops it with one chamber under
 * the hammer at the top; after a heartbeat the hammer falls and the revolver either
 * fires, with a flash at the muzzle, or clicks. Whether it fires was already decided by
 * the provably fair generator; the show picks a chamber that agrees with it.</p>
 */
public final class BarrelShow extends ArenaShow {

    /** Ticks the cylinder spins on every pull. */
    private static final int SPIN_TICKS = 20;
    /** Ticks between the cylinder stopping and the hammer falling. */
    private static final int SUSPENSE_TICKS = 6;

    private final int chambers;
    private final int bullets;
    private final float radius;
    private final Vector3f axle = new Vector3f(0, 4.2f, 0);

    private final List<ItemDisplay> body = new ArrayList<>();
    private final List<ItemDisplay> holes = new ArrayList<>();
    private final List<ItemDisplay> rounds = new ArrayList<>();
    private Location anchor;
    private BlockDisplay hammer;
    private double angle;
    private double spinFrom;
    private double spinArc;
    private int spinStart = -1;
    private int age;
    private boolean pendingFire;
    private boolean pending;

    public BarrelShow(MultiverseGamblingPlugin plugin, ArenaStage stage, int chambers, int bullets, int ticks) {
        super(plugin, stage, ticks);
        this.chambers = Math.max(2, chambers);
        this.bullets = Math.max(0, Math.min(bullets, this.chambers - 1));
        this.radius = (float) Math.max(1.5, this.chambers * 0.27);
    }

    @Override
    protected void onStart() {
        anchor = local(axle.x, axle.y, axle.z);
        Location ground = local(0, 0, 0);
        float frame = radius + 0.95f;
        // The frame of the gun round the cylinder, the barrel to the right, the grip below.
        block(ground, Material.GRAY_CONCRETE, Props.box(new Vector3f(0, axle.y, -0.75f),
                new Vector3f(frame * 2, frame * 2, 0.5f)));
        block(ground, Material.IRON_BLOCK, Props.box(new Vector3f(frame + 2.0f, axle.y + radius, -0.45f),
                new Vector3f(4.2f, 0.7f, 0.7f)));
        block(ground, Material.BLACK_CONCRETE, Props.box(new Vector3f(frame + 4.12f, axle.y + radius, -0.45f),
                new Vector3f(0.06f, 0.42f, 0.42f)));
        block(ground, Material.DARK_OAK_PLANKS, Props.box(new Vector3f(-frame - 0.6f, axle.y - frame - 0.6f, -0.6f),
                Props.roll(Math.toRadians(-28)), new Vector3f(1.3f, 3.2f, 0.7f)));
        block(ground, Material.POLISHED_BLACKSTONE_BRICKS, Props.box(new Vector3f(0, 0.4f, -0.4f),
                new Vector3f(4.5f, 0.8f, 2.6f)));
        block(ground, Material.POLISHED_DEEPSLATE, Props.box(new Vector3f(0, (axle.y - frame) / 2 + 0.4f, -0.7f),
                new Vector3f(0.8f, axle.y - frame - 0.8f, 0.6f)));

        for (int plate = 0; plate < 3; plate++) {
            body.add(item(anchor, Material.POLISHED_DEEPSLATE, bodyPose(0.0, plate)));
        }
        for (int chamber = 0; chamber < chambers; chamber++) {
            holes.add(item(anchor, Material.BLACK_CONCRETE, chamberPose(0.0, chamber, 0.85f, 0.1f, 0.62f)));
            ItemDisplay round = loaded(chamber)
                    ? item(anchor, Material.GOLD_BLOCK, chamberPose(0.0, chamber, 0.55f, 0.22f, 0.7f))
                    : null;
            rounds.add(round);
        }
        item(anchor, Material.IRON_BLOCK, Props.centred(new Vector3f(0, 0, 0.66f), Props.none(),
                new Vector3f(0.5f, 0.5f, 0.2f)));
        hammer = block(anchor, Material.REDSTONE_BLOCK, hammerPose(0.0f));
        text(local(0, axle.y + frame + 1.3f, 0), Text.c("&c&l" + bullets + "&7/&f" + chambers + "  &8│  &e"
                + Text.percent((double) bullets / chambers)), Props.scaled(1.6f), Display.Billboard.VERTICAL);
        angle = Rng.next() * WheelMath.TAU;
        place(angle, 0);
    }

    /** Somebody pulled the trigger: the cylinder spins and the shot either fires or does not. */
    public void pull(boolean fires) {
        if (holes.isEmpty()) {
            return;
        }
        // A chamber that agrees with the result comes to rest under the hammer.
        List<Integer> candidates = new ArrayList<>();
        for (int chamber = 0; chamber < chambers; chamber++) {
            if (loaded(chamber) == fires) {
                candidates.add(chamber);
            }
        }
        int target = candidates.isEmpty() ? 0 : candidates.get(Rng.intBetween(0, candidates.size() - 1));
        double rest = -WheelMath.angleOf(target, chambers);
        spinFrom = angle;
        spinArc = WheelMath.TAU + WheelMath.normalize(rest - angle);
        spinStart = age;
        pendingFire = fires;
        pending = true;
        Props.animate(hammer, hammerPose(0.35f), 3);
        playSound(Sound.BLOCK_CHAIN_PLACE, 1.0f, 0.6f);
    }

    @Override
    protected void onAnimate(int age) {
        this.age = age;
        if (spinStart < 0) {
            return;
        }
        int since = age - spinStart;
        if (since <= SPIN_TICKS) {
            double eased = WheelMath.easeQuad(since / (double) SPIN_TICKS);
            angle = spinFrom + spinArc * eased;
            place(angle, 1);
            int notch = (int) Math.floor(WheelMath.normalize(angle) / (WheelMath.TAU / chambers));
            if (since % 2 == 0 && since < SPIN_TICKS) {
                playSound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 0.7f + notch * 0.05f);
            }
            return;
        }
        if (pending && since >= SPIN_TICKS + SUSPENSE_TICKS) {
            pending = false;
            spinStart = -1;
            Props.animate(hammer, hammerPose(-0.25f), 1);
            later(3, () -> Props.animate(hammer, hammerPose(0.0f), 4));
            if (pendingFire) {
                fire();
            } else {
                particles(Particle.SMOKE, 0, axle.y + radius, 0.9, 6, 0.1, 0.01);
                playSound(Sound.BLOCK_DISPENSER_FAIL, 1.0f, 1.4f);
                playSound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.9f, 0.5f);
            }
        }
    }

    private void fire() {
        float frame = radius + 0.95f;
        double muzzleX = frame + 4.3;
        double muzzleY = axle.y + radius;
        particles(Particle.EXPLOSION, muzzleX, muzzleY, -0.45, 2, 0.1, 0.0);
        particles(Particle.FLAME, muzzleX, muzzleY, -0.45, 30, 0.25, 0.12);
        particles(Particle.LARGE_SMOKE, muzzleX + 0.6, muzzleY, -0.45, 20, 0.3, 0.05);
        particles(Particle.LAVA, 0, axle.y + radius, 0.8, 6, 0.2, 0.0);
        for (ItemDisplay plate : body) {
            if (plate.isValid()) {
                plate.setGlowColorOverride(org.bukkit.Color.RED);
                plate.setGlowing(true);
            }
        }
        later(12, () -> body.forEach(plate -> {
            if (plate.isValid()) {
                plate.setGlowing(false);
            }
        }));
        playSound(Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.3f);
        playSound(Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1.0f, 0.6f);
    }

    private void place(double spin, int ticks) {
        for (int plate = 0; plate < body.size(); plate++) {
            Props.animate(body.get(plate), bodyPose(spin, plate), ticks);
        }
        for (int chamber = 0; chamber < chambers; chamber++) {
            Props.animate(holes.get(chamber), chamberPose(spin, chamber, 0.85f, 0.1f, 0.62f), ticks);
            ItemDisplay round = rounds.get(chamber);
            if (round != null) {
                Props.animate(round, chamberPose(spin, chamber, 0.55f, 0.22f, 0.7f), ticks);
            }
        }
    }

    private boolean loaded(int chamber) {
        return chamber < bullets;
    }

    private Transformation bodyPose(double spin, int plate) {
        float side = 2 * (radius + 0.72f);
        Quaternionf rotation = Props.roll(spin + plate * Math.PI / 6);
        return Props.centred(new Vector3f(), rotation, new Vector3f(side, side, 1.2f));
    }

    private Transformation chamberPose(double spin, int chamber, float size, float depth, float front) {
        double at = spin + WheelMath.angleOf(chamber, chambers);
        Quaternionf rotation = Props.roll(at);
        Vector3f centre = rotation.transform(new Vector3f(0, radius, front));
        return Props.centred(centre, rotation, new Vector3f(size, size, depth));
    }

    private Transformation hammerPose(float lift) {
        Vector3f tip = new Vector3f(0, radius + 1.2f + lift, 0.6f);
        return Props.box(tip, Props.roll(Math.PI / 4), new Vector3f(0.5f, 0.5f, 0.25f));
    }
}
