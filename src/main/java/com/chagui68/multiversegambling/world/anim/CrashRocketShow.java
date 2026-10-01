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
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Crash as a rocket launch: a rocket made of blocks takes off from the corner of a
 * chart and climbs along the curve of the multiplier, leaving a trail of lights that
 * turns from green to red as it gets dangerous, with the multiplier counting up in big
 * letters over the board.
 *
 * <p>Cashing out sends the rocket off the top of the board in gold; a crash blows it
 * into pieces. The game drives it frame by frame with {@link #climb(double)}, which is
 * where the real multiplier comes from; where the rocket is drawn depends on the
 * multiplier only (see {@link CrashCurve}), so the board never gives the crash point
 * away.</p>
 */
public final class CrashRocketShow extends ArenaShow {

    /** Most trail lights kept on the board. */
    private static final int MAX_TRAIL = 140;
    /** Multipliers marked on the side of the chart. */
    private static final double[] MARKS = {1.5, 2, 3, 5, 10, 50};

    /**
     * One block of the rocket, in the frame of the rocket: {@code y} along its nose.
     */
    private record Part(Material material, Vector3f offset, Vector3f size) {
    }

    private static final List<Part> MODEL = List.of(
            new Part(Material.WHITE_CONCRETE, new Vector3f(0, 0, 0), new Vector3f(0.5f, 1.5f, 0.5f)),
            new Part(Material.RED_CONCRETE, new Vector3f(0, 0.92f, 0), new Vector3f(0.38f, 0.36f, 0.38f)),
            new Part(Material.RED_CONCRETE, new Vector3f(0, 1.2f, 0), new Vector3f(0.2f, 0.22f, 0.2f)),
            new Part(Material.RED_CONCRETE, new Vector3f(0, 0.42f, 0), new Vector3f(0.54f, 0.14f, 0.54f)),
            new Part(Material.LIGHT_BLUE_CONCRETE, new Vector3f(0, 0.12f, 0.24f), new Vector3f(0.22f, 0.22f, 0.06f)),
            new Part(Material.RED_CONCRETE, new Vector3f(-0.34f, -0.6f, 0), new Vector3f(0.14f, 0.5f, 0.44f)),
            new Part(Material.RED_CONCRETE, new Vector3f(0.34f, -0.6f, 0), new Vector3f(0.14f, 0.5f, 0.44f)),
            new Part(Material.GRAY_CONCRETE, new Vector3f(0, -0.82f, 0), new Vector3f(0.36f, 0.16f, 0.36f)));

    private final double maxMultiplier;
    private final List<BlockDisplay> rocket = new ArrayList<>();
    private final List<BlockDisplay> trail = new ArrayList<>();
    private Location anchor;
    private TextDisplay counter;
    private double current = 1.0;
    private int climbs;
    private boolean over;
    private boolean cashed;
    private int flyAway;

    public CrashRocketShow(MultiverseGamblingPlugin plugin, ArenaStage stage, double maxMultiplier, int ticks) {
        super(plugin, stage, ticks);
        this.maxMultiplier = Math.max(1.5, maxMultiplier);
    }

    @Override
    protected void onStart() {
        anchor = local(0, 0, 0);
        double left = CrashCurve.LEFT - 0.8;
        double right = CrashCurve.LEFT + CrashCurve.WIDTH + 0.8;
        double top = CrashCurve.BOTTOM + CrashCurve.HEIGHT + 1.2;
        float width = (float) (right - left);
        float middle = (float) ((left + right) / 2);
        // The chart: a dark board, a golden frame, the two axes and a launch pad.
        block(anchor, Material.BLACK_CONCRETE, Props.box(new Vector3f(middle, (float) top / 2, -0.7f),
                new Vector3f(width, (float) top, 0.2f)));
        block(anchor, Material.GOLD_BLOCK, Props.box(new Vector3f(middle, (float) top, -0.7f),
                new Vector3f(width + 0.3f, 0.15f, 0.35f)));
        for (double x : new double[]{left, right}) {
            block(anchor, Material.GOLD_BLOCK, Props.box(new Vector3f((float) x, (float) top / 2, -0.7f),
                    new Vector3f(0.15f, (float) top, 0.35f)));
        }
        float axisX = (float) CrashCurve.LEFT - 0.35f;
        float floor = (float) CrashCurve.BOTTOM - 0.55f;
        block(anchor, Material.WHITE_CONCRETE, Props.box(new Vector3f(middle, floor, -0.55f),
                new Vector3f(width - 0.6f, 0.07f, 0.05f)));
        block(anchor, Material.WHITE_CONCRETE, Props.box(new Vector3f(axisX, (float) (floor + top) / 2, -0.55f),
                new Vector3f(0.07f, (float) (top - floor - 0.4f), 0.05f)));
        for (double mark : MARKS) {
            if (mark > maxMultiplier) {
                continue;
            }
            float y = (float) CrashCurve.y(mark);
            block(anchor, Material.GRAY_CONCRETE, Props.box(new Vector3f(middle, y, -0.58f),
                    new Vector3f(width - 0.6f, 0.03f, 0.02f)));
            text(local(axisX - 0.55f, y - 0.12f, -0.5f), Text.c("&7" + Text.multiplier(mark)),
                    Props.scaled(0.7f), Display.Billboard.FIXED);
        }
        block(anchor, Material.POLISHED_BLACKSTONE_BRICKS, Props.box(
                new Vector3f((float) CrashCurve.LEFT, 0.4f, 0), new Vector3f(1.6f, 0.8f, 1.6f)));
        block(anchor, Material.YELLOW_CONCRETE, Props.box(
                new Vector3f((float) CrashCurve.LEFT, 0.83f, 0), new Vector3f(1.2f, 0.06f, 1.2f)));

        for (Part part : MODEL) {
            rocket.add(block(anchor, part.material(), partPose(part, rocketSpot(1.0), 0.0)));
        }
        counter = text(local(middle, top + 0.35, -0.6), Text.c("&a&l1.00x"), Props.scaled(3.2f),
                Display.Billboard.FIXED);
        counter.setBackgroundColor(Props.argb(160, 0, 0, 0));
        playSound(Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 0.7f);
    }

    /** Moves the rocket to where the multiplier is right now. */
    public void climb(double multiplier) {
        if (over || rocket.isEmpty()) {
            return;
        }
        current = Math.max(1.0, Math.min(maxMultiplier, multiplier));
        climbs++;
        Vector3f spot = rocketSpot(current);
        double heading = CrashCurve.heading(current);
        for (int i = 0; i < MODEL.size(); i++) {
            Props.animate(rocket.get(i), partPose(MODEL.get(i), spot, heading), 1);
        }
        Vector3f tail = tailOf(spot, heading);
        particles(Particle.FLAME, tail.x, tail.y, tail.z, 3, 0.08, 0.02);
        if (climbs % 2 == 0) {
            particles(Particle.SMOKE, tail.x, tail.y, tail.z, 2, 0.1, 0.01);
            dropTrail(tail);
            if (counter != null && counter.isValid()) {
                counter.text(Text.c(colour(current) + "&l" + Text.multiplier(current)));
            }
        }
        if (climbs % 10 == 0) {
            playSound(Sound.BLOCK_NOTE_BLOCK_PLING, 0.35f, (float) Math.min(2.0, 0.8 + CrashCurve.progress(current)));
        }
    }

    /** The player got out in time: the rocket shoots off the top of the board in gold. */
    public void cashOut() {
        if (over) {
            return;
        }
        over = true;
        cashed = true;
        flyAway = 1;
        for (BlockDisplay part : rocket) {
            if (part.isValid()) {
                part.setGlowColorOverride(org.bukkit.Color.fromRGB(0xFFD700));
                part.setGlowing(true);
            }
        }
        if (counter != null && counter.isValid()) {
            counter.text(Text.c("&6&l✔ " + Text.multiplier(current)));
            Props.animate(counter, Props.scaled(3.8f), 4);
        }
        Vector3f spot = rocketSpot(current);
        celebrate(spot.x, spot.y, spot.z);
        playSound(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
    }

    /** The curve burst: the rocket blows apart where it stands. */
    public void burst() {
        if (over) {
            return;
        }
        over = true;
        Vector3f spot = rocketSpot(current);
        for (BlockDisplay part : rocket) {
            if (!part.isValid()) {
                continue;
            }
            part.setBlock((Rng.chance(0.5) ? Material.ORANGE_CONCRETE : Material.BLACK_CONCRETE).createBlockData());
            Vector3f debris = new Vector3f(spot).add((float) ((Rng.next() - 0.5) * 5), (float) (Rng.next() * 2 - 3.0),
                    (float) (Rng.next() * 2));
            Quaternionf tumble = new Quaternionf().rotateXYZ((float) (Rng.next() * 3), (float) (Rng.next() * 3),
                    (float) (Rng.next() * 3));
            Props.animate(part, Props.box(debris, tumble, new Vector3f(0.25f, 0.25f, 0.25f)), 14);
        }
        for (BlockDisplay light : trail) {
            if (light.isValid()) {
                light.setBlock(Material.RED_CONCRETE.createBlockData());
            }
        }
        if (counter != null && counter.isValid()) {
            counter.text(Text.c("&c&l✖ " + Text.multiplier(current)));
        }
        particles(Particle.EXPLOSION_EMITTER, spot.x, spot.y, spot.z, 1, 0.0, 0.0);
        particles(Particle.LAVA, spot.x, spot.y, spot.z, 20, 0.5, 0.1);
        particles(Particle.LARGE_SMOKE, spot.x, spot.y, spot.z, 25, 0.6, 0.05);
        playSound(Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
    }

    @Override
    protected void onAnimate(int age) {
        if (!cashed || flyAway <= 0 || flyAway > 30 || rocket.isEmpty()) {
            return;
        }
        // Off it goes: straight up and out of the top of the board.
        flyAway++;
        Vector3f spot = rocketSpot(current).add(0, flyAway * 0.45f, 0);
        for (int i = 0; i < MODEL.size(); i++) {
            Props.animate(rocket.get(i), partPose(MODEL.get(i), spot, 0.0), 1);
        }
        Vector3f tail = tailOf(spot, 0.0);
        particles(Particle.FIREWORK, tail.x, tail.y, tail.z, 2, 0.05, 0.02);
    }

    @Override
    protected void onSettle() {
        if (!over) {
            burst();
        }
    }

    @Override
    protected void onCancel() {
        over = true;
    }

    private void dropTrail(Vector3f tail) {
        if (trail.size() >= MAX_TRAIL) {
            discard(trail.remove(0));
        }
        trail.add(block(anchor, trailColour(current),
                Props.box(new Vector3f(tail.x, tail.y, -0.45f), new Vector3f(0.2f, 0.2f, 0.06f))));
    }

    private Vector3f rocketSpot(double multiplier) {
        return new Vector3f((float) CrashCurve.x(multiplier), (float) CrashCurve.y(multiplier) + 0.25f, 0.0f);
    }

    private static Vector3f tailOf(Vector3f spot, double heading) {
        return Props.roll(-heading).transform(new Vector3f(0, -1.0f, 0)).add(spot);
    }

    private static Transformation partPose(Part part, Vector3f spot, double heading) {
        Quaternionf rotation = Props.roll(-heading);
        Vector3f centre = rotation.transform(new Vector3f(part.offset())).add(spot);
        return Props.box(centre, rotation, part.size());
    }

    private static Material trailColour(double multiplier) {
        if (multiplier >= 10) {
            return Material.RED_CONCRETE;
        }
        if (multiplier >= 3) {
            return Material.ORANGE_CONCRETE;
        }
        return multiplier >= 1.5 ? Material.YELLOW_CONCRETE : Material.LIME_CONCRETE;
    }

    private static String colour(double multiplier) {
        if (multiplier >= 10) {
            return "&c";
        }
        if (multiplier >= 3) {
            return "&6";
        }
        return multiplier >= 1.5 ? "&e" : "&a";
    }
}
