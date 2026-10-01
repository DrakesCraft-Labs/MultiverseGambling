package com.chagui68.multiversegambling.world.anim;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.Rng;
import com.chagui68.multiversegambling.util.Text;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;
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
 * A wheel built from display entities: a ring of coloured tiles with their labels, a
 * dark back plate, a hub, a ring of blinking bulbs and either a ball (a roulette) or a
 * pointer (a wheel of fortune).
 *
 * <p>It is shared by every game decided by a wheel: the classic roulette and the group
 * colour roulette lean back like a table and throw a ball against the spin; the lucky
 * wheel, the jackpot and the raffle stand up and stop under a pointer. The game only
 * says which colour each sector has, what it is called and which one wins. The winner
 * is always handed over already decided by the provably fair generator, so a show only
 * ever paints a result.</p>
 *
 * <p>Every moving piece is turned through its transformation with a one tick
 * interpolation, so the client draws the spin smoothly between server ticks. The
 * pieces are spawned at the middle of the wheel, so turning a tile round the axle is a
 * rotation plus a short translation, and the angle per step is kept small enough for
 * the interpolation to stay on the circle.</p>
 */
public final class WheelShow extends ArenaShow {

    /**
     * How the wheel is presented.
     */
    public enum Style {
        /** Leaning back like a table, with a ball thrown against the spin. */
        ROULETTE,
        /** Standing up, stopping under a pointer at the top. */
        FORTUNE
    }

    /** Fewest tiles a wheel is drawn with, so few sectors still make a round wheel. */
    private static final int MIN_TILES = 36;
    /** Radial depth of a tile. */
    private static final float TILE_DEPTH = 1.5f;
    /** Thickness of a tile. */
    private static final float TILE_THICKNESS = 0.14f;
    /** Bulbs round the rim. */
    private static final int BULBS = 32;
    /** Share of the show spent spinning; the rest is the result on display. */
    private static final double SPIN_SHARE = 0.8;
    /** Share of the show after which the ball has dropped into its pocket. */
    private static final double BALL_SHARE = 0.6;

    private final List<Material> sectors;
    private final int winner;
    private List<String> labels = List.of();
    private double[] weights;
    private Style style = Style.FORTUNE;

    // Geometry, fixed when the show starts.
    private float radius;
    private Vector3f hub;
    private Quaternionf tilt;
    private int[] tileSector;
    private double[] sectorAngle;
    private double slot;

    private final List<BlockDisplay> tiles = new ArrayList<>();
    private final List<TextDisplay> tileLabels = new ArrayList<>();
    private final List<Integer> labelSector = new ArrayList<>();
    private final List<BlockDisplay> bulbs = new ArrayList<>();
    private Location anchor;
    private ItemDisplay ball;
    private BlockDisplay pointer;
    private double startAngle;
    private double wheelArc;
    private double ballStart;
    private double ballArc;
    private double landingOffset;
    private double wheelAngle;
    private int lastClick = Integer.MIN_VALUE;
    private int pointerKick;

    /**
     * @param sectors colour of each sector, in the order they sit round the wheel
     * @param winner  index of the sector the wheel has to stop on
     * @param ticks   frames the spin lasts
     */
    public WheelShow(MultiverseGamblingPlugin plugin, ArenaStage stage,
                     List<Material> sectors, int winner, int ticks) {
        super(plugin, stage, ticks);
        this.sectors = List.copyOf(sectors);
        this.winner = sectors.isEmpty() ? 0 : Math.floorMod(winner, sectors.size());
    }

    /**
     * Text written on each sector: pocket numbers, multipliers, player names. Empty
     * strings leave a sector blank.
     */
    public WheelShow labels(List<String> labels) {
        this.labels = labels == null ? List.of() : List.copyOf(labels);
        return this;
    }

    /**
     * Size of each sector, when they are not all equal (a bigger stake in the jackpot
     * is a bigger slice).
     */
    public WheelShow weights(double[] weights) {
        this.weights = weights == null || weights.length != sectors.size() ? null : weights.clone();
        return this;
    }

    public WheelShow style(Style style) {
        this.style = style == null ? Style.FORTUNE : style;
        return this;
    }

    // ------------------------------------------------------------------ setup

    @Override
    protected void onStart() {
        if (sectors.isEmpty()) {
            return;
        }
        boolean roulette = style == Style.ROULETTE;
        radius = roulette ? 5.0f : 4.6f;
        // The lowest point of a leaning wheel is radius * cos(tilt) under its hub: the hub
        // is raised so the rim clears the pedestal instead of sinking into the floor.
        hub = new Vector3f(0, roulette ? 5.6f : 5.4f, 0);
        // A roulette leans back so its face looks up at the audience; a wheel of fortune
        // stands straight up.
        tilt = Props.pitch(roulette ? -Math.toRadians(40) : 0.0);
        anchor = local(hub.x, hub.y, hub.z);

        int[] perSector = WheelMath.allocate(weights, sectors.size(), MIN_TILES);
        int total = 0;
        for (int count : perSector) {
            total += count;
        }
        slot = WheelMath.TAU / total;
        tileSector = new int[total];
        sectorAngle = new double[sectors.size()];
        int tile = 0;
        for (int sector = 0; sector < sectors.size(); sector++) {
            sectorAngle[sector] = slot * (tile + (perSector[sector] - 1) / 2.0);
            for (int i = 0; i < perSector[sector]; i++) {
                tileSector[tile++] = sector;
            }
        }
        // A random start, so the wheel never sets off from the same place twice. The
        // landing point inside the winning sector is jittered too: it is only paint.
        startAngle = Rng.next() * WheelMath.TAU;
        int sectorTiles = perSector[winner];
        landingOffset = (Rng.next() - 0.5) * 0.7 * slot * Math.max(0, sectorTiles - 1);
        double turns = Math.max(1.0, Math.min(3.0, duration() / 40.0));
        double target = reference() - sectorAngle[winner] - landingOffset;
        wheelArc = WheelMath.TAU * Math.floor(turns) + WheelMath.normalize(target - startAngle);
        wheelAngle = startAngle;
        if (roulette) {
            ballStart = Rng.next() * WheelMath.TAU;
            double wheelAtLanding = startAngle + wheelArc * WheelMath.easeQuad(BALL_SHARE / SPIN_SHARE);
            double landing = wheelAtLanding + sectorAngle[winner] + landingOffset;
            // The ball runs the other way: its arc is negative.
            ballArc = -(WheelMath.TAU * 2 + WheelMath.normalize(ballStart - landing));
        }

        buildStand(roulette);
        buildBackPlate();
        float width = (float) (2 * Math.PI * (radius - TILE_DEPTH / 2) / total * 1.06);
        for (int index = 0; index < total; index++) {
            Material colour = sectors.get(tileSector[index]);
            // Odd tiles sit a hair forward, so the slight overlap that closes the gaps
            // between neighbours never flickers.
            float depth = index % 2 == 0 ? 0.0f : 0.006f;
            tiles.add(block(anchor, colour, tilePose(index, startAngle, width, depth, 1.0f)));
        }
        buildLabels();
        buildHubAndRim();
        if (roulette) {
            ball = item(anchor, Material.SNOWBALL, ballPose(ballStart, radius + 0.05f, 0.3f));
            ball.setBillboard(Display.Billboard.CENTER);
        } else {
            pointer = block(anchor, Material.GOLD_BLOCK, pointerPose(0.0));
        }

        playSound(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 0.8f);
    }

    /**
     * World angle (in the wheel plane) the winner has to stop at: the top for a wheel of
     * fortune, where the pointer is; anywhere for a roulette, the ball finds it.
     */
    private double reference() {
        return 0.0;
    }

    private void buildStand(boolean roulette) {
        Location ground = local(0, 0, 0);
        if (roulette) {
            // A pedestal under the tilted wheel, on a wide round foot.
            block(ground, Material.POLISHED_BLACKSTONE_BRICKS,
                    Props.box(new Vector3f(0, 0.15f, 0), new Vector3f(4.2f, 0.3f, 4.2f)));
            block(ground, Material.POLISHED_BLACKSTONE_BRICKS, Props.box(new Vector3f(0, 0.15f, 0),
                    Props.yaw(Math.PI / 4), new Vector3f(4.2f, 0.3f, 4.2f)));
            block(ground, Material.GOLD_BLOCK,
                    Props.box(new Vector3f(0, 0.35f, 0), new Vector3f(2.4f, 0.1f, 2.4f)));
            block(ground, Material.POLISHED_DEEPSLATE,
                    Props.box(new Vector3f(0, hub.y / 2, -0.4f), new Vector3f(1.1f, hub.y, 1.1f)));
        } else {
            // Two legs spread like an easel and a heavy base.
            block(ground, Material.POLISHED_BLACKSTONE_BRICKS,
                    Props.box(new Vector3f(0, 0.25f, -0.4f), new Vector3f(5.0f, 0.5f, 2.0f)));
            for (int side : new int[]{-1, 1}) {
                Quaternionf lean = Props.roll(side * Math.toRadians(14));
                block(ground, Material.POLISHED_DEEPSLATE, Props.box(
                        new Vector3f(side * 1.1f, hub.y / 2, -0.45f), lean, new Vector3f(0.45f, hub.y + 0.6f, 0.45f)));
            }
            block(ground, Material.GOLD_BLOCK,
                    Props.box(new Vector3f(0, 0.55f, 0.5f), new Vector3f(5.0f, 0.1f, 0.1f)));
        }
    }

    private void buildBackPlate() {
        float side = 2 * (radius + 0.1f);
        for (int i = 0; i < 3; i++) {
            Quaternionf rotation = new Quaternionf(tilt).mul(Props.roll(i * Math.PI / 6));
            Vector3f centre = tilt.transform(new Vector3f(0, 0, -TILE_THICKNESS - 0.04f));
            block(anchor, Material.BLACK_CONCRETE, Props.box(centre, rotation, new Vector3f(side, side, 0.08f)));
        }
    }

    private void buildHubAndRim() {
        float inner = radius - TILE_DEPTH;
        float side = 2 * inner;
        for (int i = 0; i < 3; i++) {
            Quaternionf rotation = new Quaternionf(tilt).mul(Props.roll(i * Math.PI / 6));
            Vector3f centre = tilt.transform(new Vector3f(0, 0, 0.05f));
            block(anchor, i == 0 ? Material.POLISHED_BLACKSTONE : Material.POLISHED_DEEPSLATE,
                    Props.box(centre, rotation, new Vector3f(side, side, 0.12f)));
        }
        // Gold cap and spokes on the hub.
        for (int i = 0; i < 4; i++) {
            Quaternionf rotation = new Quaternionf(tilt).mul(Props.roll(i * Math.PI / 4));
            Vector3f centre = tilt.transform(new Vector3f(0, 0, 0.13f));
            block(anchor, Material.GOLD_BLOCK, Props.box(centre, rotation, new Vector3f(0.18f, side * 0.92f, 0.05f)));
        }
        item(anchor, Material.GOLD_BLOCK,
                Props.centred(tilt.transform(new Vector3f(0, 0, 0.3f)), tilt, new Vector3f(0.9f, 0.9f, 0.3f)));
        for (int i = 0; i < BULBS; i++) {
            double angle = WheelMath.TAU * i / BULBS;
            Vector3f centre = onWheel(angle, radius + 0.32f, 0.0f);
            Quaternionf rotation = new Quaternionf(tilt).mul(Props.roll(angle));
            bulbs.add(block(anchor, i % 2 == 0 ? Material.GLOWSTONE : Material.GOLD_BLOCK,
                    Props.box(centre, rotation, new Vector3f(0.34f, 0.34f, 0.34f))));
        }
    }

    private void buildLabels() {
        if (labels.isEmpty()) {
            return;
        }
        int total = tileSector.length;
        for (int sector = 0; sector < sectors.size() && sector < labels.size(); sector++) {
            String label = labels.get(sector);
            if (label == null || label.isBlank()) {
                continue;
            }
            int tilesOfSector = 0;
            for (int owner : tileSector) {
                if (owner == sector) {
                    tilesOfSector++;
                }
            }
            double arc = (radius - TILE_DEPTH / 2) * WheelMath.TAU * tilesOfSector / total;
            int visible = Text.strip(label).length();
            float scale = (float) Math.max(0.35, Math.min(1.3, arc * 0.8 / Math.max(1, visible * 0.16)));
            TextDisplay text = text(anchor, Text.c(textColour(sectors.get(sector)) + label),
                    labelPose(sectorAngle[sector] + startAngle, scale), Display.Billboard.FIXED);
            tileLabels.add(text);
            labelSector.add(sector);
        }
    }

    // ----------------------------------------------------------------- frames

    @Override
    protected void onFrame(int elapsed, int duration) {
        if (tiles.isEmpty()) {
            return;
        }
        double spinFrames = duration * SPIN_SHARE;
        double spin = Math.min(1, elapsed / spinFrames);
        wheelAngle = startAngle + wheelArc * WheelMath.easeQuad(spin);
        placeTiles(wheelAngle, 1);

        double speed = 1 - spin;
        if (ball != null) {
            double ballFrames = duration * BALL_SHARE;
            double travel = Math.min(1, elapsed / ballFrames);
            double angle;
            float ballRadius;
            if (travel < 1) {
                angle = ballStart + ballArc * WheelMath.easeQuad(travel);
                // The ball spirals in from the rim as it loses speed.
                ballRadius = radius + 0.05f - (float) (0.6 * travel * travel);
            } else {
                angle = wheelAngle + sectorAngle[winner] + landingOffset;
                ballRadius = radius - TILE_DEPTH / 2;
            }
            float lift = travel < 1 ? 0.3f : 0.22f;
            Props.animate(ball, ballPose(angle, ballRadius, lift), 1);
            int pocket = (int) Math.floor(WheelMath.normalize(angle - wheelAngle) / slot);
            click(pocket, speed, travel >= 1);
        } else {
            int under = (int) Math.floor(WheelMath.normalize(reference() - wheelAngle + slot / 2) / slot);
            click(under, speed, spin >= 1);
        }
        if (pointer != null && pointerKick > 0) {
            pointerKick--;
            Props.animate(pointer, pointerPose(pointerKick > 0 ? Math.toRadians(-24) : 0.0), 2);
        }
    }

    /**
     * A tick every time a new tile passes the ball or the pointer.
     */
    private void click(int tile, double speed, boolean stopped) {
        if (stopped || tile == lastClick) {
            return;
        }
        lastClick = tile;
        pointerKick = 2;
        playSound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.45f, (float) (0.8 + 0.9 * (1 - speed)));
    }

    @Override
    protected void onAnimate(int age) {
        // The bulbs chase each other round the rim, faster while the wheel turns.
        int period = active() ? 3 : 6;
        if (age % period != 0 || bulbs.isEmpty()) {
            return;
        }
        int phase = age / period;
        for (int i = 0; i < bulbs.size(); i++) {
            BlockDisplay bulb = bulbs.get(i);
            if (bulb.isValid()) {
                boolean lit = settled() ? phase % 2 == 0 : (i + phase) % 4 < 2;
                bulb.setBlock((lit ? Material.GLOWSTONE : Material.GOLD_BLOCK).createBlockData());
            }
        }
    }

    @Override
    protected void onSettle() {
        if (tiles.isEmpty()) {
            return;
        }
        double finalAngle = startAngle + wheelArc;
        wheelAngle = finalAngle;
        placeTiles(finalAngle, 2);
        if (ball != null) {
            Props.animate(ball, ballPose(finalAngle + sectorAngle[winner] + landingOffset,
                    radius - TILE_DEPTH / 2, 0.22f), 2);
        }
        // Everything but the winning sector dims, and the winner glows and pops out.
        Material colour = sectors.get(winner);
        Color glow = Props.colourOf(colour);
        int rgb = glow.asRGB();
        if (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF) < 180) {
            // A dark pocket would get a dark outline nobody can see.
            glow = Color.WHITE;
        }
        float width = (float) (2 * Math.PI * (radius - TILE_DEPTH / 2) / tileSector.length * 1.06);
        for (int index = 0; index < tiles.size(); index++) {
            BlockDisplay tile = tiles.get(index);
            if (!tile.isValid()) {
                continue;
            }
            if (tileSector[index] == winner) {
                tile.setGlowColorOverride(glow);
                tile.setGlowing(true);
                Props.animate(tile, tilePose(index, finalAngle, width, 0.25f, 1.6f), 6);
            } else {
                tile.setBrightness(new Display.Brightness(4, 4));
            }
        }
        for (int i = 0; i < tileLabels.size(); i++) {
            if (labelSector.get(i) != winner && tileLabels.get(i).isValid()) {
                tileLabels.get(i).setTextOpacity((byte) 90);
            }
        }
        Vector3f spot = new Vector3f(hub).add(onWheel(finalAngle + sectorAngle[winner], radius, 0.4f));
        String label = winner < labels.size() ? labels.get(winner) : "";
        if (label != null && !label.isBlank()) {
            Component banner = Text.c(textColour(colour) + "&l" + label);
            TextDisplay title = text(local(0, hub.y + radius + 1.6f, 0.6f), banner,
                    Props.centred(new Vector3f(), 0.1f), Display.Billboard.VERTICAL);
            title.setBackgroundColor(Props.argb(150, 0, 0, 0));
            later(1, () -> Props.animate(title, Props.centred(new Vector3f(), 2.6f), 8));
        }
        celebrateAt(spot);
        playSound(Sound.BLOCK_NOTE_BLOCK_BELL, 0.9f, 1.4f);
    }

    @Override
    protected int extraLinger() {
        return 20;
    }

    private void celebrateAt(Vector3f spot) {
        // The spot is in show coordinates relative to the ground of the stage.
        stage().world().spawnParticle(Particle.TOTEM_OF_UNDYING, local(spot.x, spot.y, spot.z), 50, 0.5, 0.5, 0.5, 0.4);
        stage().world().spawnParticle(Particle.FIREWORK, local(spot.x, spot.y, spot.z), 30, 0.4, 0.4, 0.4, 0.1);
        playSound(Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.8f, 1.2f);
    }

    // ----------------------------------------------------------------- poses

    private void placeTiles(double angle, int ticks) {
        float width = (float) (2 * Math.PI * (radius - TILE_DEPTH / 2) / tileSector.length * 1.06);
        for (int index = 0; index < tiles.size(); index++) {
            float depth = index % 2 == 0 ? 0.0f : 0.006f;
            Props.animate(tiles.get(index), tilePose(index, angle, width, depth, 1.0f), ticks);
        }
        for (int i = 0; i < tileLabels.size(); i++) {
            int sector = labelSector.get(i);
            float scale = tileLabels.get(i).getTransformation().getScale().x;
            Props.animate(tileLabels.get(i), labelPose(angle + sectorAngle[sector], scale), ticks);
        }
    }

    /**
     * Pose of tile {@code index} with the wheel turned by {@code angle}.
     */
    private Transformation tilePose(int index, double angle, float width, float depth, float thickness) {
        double at = angle + slot * index;
        Quaternionf rotation = new Quaternionf(tilt).mul(Props.roll(at));
        Vector3f centre = onWheel(at, radius - TILE_DEPTH / 2, depth);
        return Props.box(centre, rotation,
                new Vector3f(width, TILE_DEPTH, TILE_THICKNESS * thickness));
    }

    private Transformation labelPose(double angle, float scale) {
        Quaternionf rotation = new Quaternionf(tilt).mul(Props.roll(angle));
        // Text grows upwards from its origin: start a little inwards so it is centred on
        // the tile.
        float along = radius - TILE_DEPTH / 2 - 0.13f * scale;
        Vector3f centre = onWheel(angle, along, TILE_THICKNESS / 2 + 0.03f);
        return Props.centred(centre, rotation, new Vector3f(scale, scale, scale));
    }

    private Transformation ballPose(double angle, float distance, float lift) {
        return Props.centred(onWheel(angle, distance, TILE_THICKNESS / 2 + lift), Props.none(),
                new Vector3f(0.55f, 0.55f, 0.55f));
    }

    private Transformation pointerPose(double kick) {
        Vector3f tip = new Vector3f(0, radius + 0.25f, 0.28f);
        Quaternionf rotation = Props.roll(Math.PI / 4 + kick);
        return Props.box(tilt.transform(tip), new Quaternionf(tilt).mul(rotation), new Vector3f(0.55f, 0.55f, 0.2f));
    }

    /**
     * Point of the wheel at that angle (anticlockwise from the top, as the audience sees
     * it), distance from the axle and height over the face, relative to the hub.
     */
    private Vector3f onWheel(double angle, float distance, float lift) {
        Vector3f flat = new Vector3f((float) (-Math.sin(angle) * distance), (float) (Math.cos(angle) * distance), lift);
        return tilt.transform(flat);
    }

    /**
     * Colour code that reads on a tile of that colour.
     */
    private static String textColour(Material tile) {
        return switch (tile) {
            case WHITE_CONCRETE, YELLOW_CONCRETE, LIME_CONCRETE, GOLD_BLOCK, LIGHT_GRAY_CONCRETE,
                 LIGHT_BLUE_CONCRETE, PINK_CONCRETE -> "&0";
            default -> "&f";
        };
    }
}
