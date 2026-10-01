package com.chagui68.multiversegambling.world.anim;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.Rng;
import com.chagui68.multiversegambling.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The towers game as a real tower standing on the pavilion: a stack of floors between
 * two quartz pillars, a row of doors on every floor, the multiplier of each floor
 * written beside it and the head of the player climbing up the side.
 *
 * <p>The floor being played glows; a safe door turns into an emerald with a gem popping
 * out of it, the bombs of every cleared floor are shown in their place, and a wrong door
 * blows up and brings the rest of the tower crashing down. The game drives it step by
 * step; whether a door is safe was decided by the provably fair generator before the
 * player chose it.</p>
 */
public final class TowerShow extends ArenaShow {

    private static final float DOOR_PITCH = 1.55f;
    private static final float DOOR_WIDTH = 1.25f;
    private static final float BASE = 1.3f;

    private final int levels;
    private final float floorHeight;
    private final List<List<BlockDisplay>> doors = new ArrayList<>();
    private final List<BlockDisplay> slabs = new ArrayList<>();
    private final List<TextDisplay> labels = new ArrayList<>();
    private final List<BlockDisplay> bulbs = new ArrayList<>();
    private Location anchor;
    private TextDisplay banner;
    private ItemDisplay climber;
    private double[] multipliers = new double[0];
    private int tiles;
    private int current = -1;
    private boolean built;
    private boolean over;
    private boolean party;

    public TowerShow(MultiverseGamblingPlugin plugin, ArenaStage stage, int levels, int ticks) {
        super(plugin, stage, ticks);
        this.levels = Math.max(1, levels);
        this.floorHeight = Math.min(1.25f, 11.0f / this.levels);
    }

    @Override
    protected void onStart() {
        anchor = local(0, 0, 0);
        block(anchor, Material.POLISHED_BLACKSTONE_BRICKS, Props.box(new Vector3f(0, 0.35f, 0), new Vector3f(8.5f, 0.7f, 3.0f)));
        block(anchor, Material.GOLD_BLOCK, Props.box(new Vector3f(0, 0.72f, 0), new Vector3f(8.7f, 0.08f, 3.2f)));
        banner = text(local(0, 2.4, 0.4), Component.empty(), Props.scaled(2.0f), Display.Billboard.VERTICAL);
        banner.setBackgroundColor(Props.argb(150, 0, 0, 0));
        playSound(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 0.8f);
    }

    /**
     * Words over the empty base while the player picks a difficulty.
     */
    public void waiting(Component text) {
        if (banner != null && banner.isValid()) {
            banner.text(text);
        }
    }

    /**
     * Raises the tower once the difficulty is known.
     *
     * @param multipliers what reaching each floor pays, floor 1 first
     */
    public void build(int tiles, double[] multipliers, ItemStack climberHead) {
        if (built || anchor == null) {
            return;
        }
        built = true;
        this.tiles = Math.max(2, tiles);
        this.multipliers = multipliers.clone();
        if (banner != null) {
            discard(banner);
            banner = null;
        }
        float half = this.tiles * DOOR_PITCH / 2;
        float top = floorY(levels) + 0.2f;
        for (int side : new int[]{-1, 1}) {
            float x = side * (half + 0.35f);
            block(anchor, Material.QUARTZ_PILLAR, Props.box(new Vector3f(x, (BASE + top) / 2, 0),
                    new Vector3f(0.55f, top - BASE + 0.6f, 0.55f)));
            for (int level = 0; level < levels; level++) {
                bulbs.add(block(anchor, Material.GLOWSTONE, Props.box(
                        new Vector3f(x, floorY(level) + floorHeight * 0.5f, 0.32f), new Vector3f(0.2f, 0.2f, 0.12f))));
            }
        }
        block(anchor, Material.GOLD_BLOCK, Props.box(new Vector3f(0, top + 0.35f, 0),
                new Vector3f(half * 2 + 1.6f, 0.3f, 1.4f)));
        item(anchor, Material.GOLD_BLOCK, Props.centred(new Vector3f(0, top + 1.2f, 0),
                new Quaternionf().rotateY((float) (Math.PI / 4)).rotateX((float) (Math.PI / 4)), new Vector3f(1.0f, 1.0f, 1.0f)));
        block(anchor, Material.BLACK_CONCRETE, Props.box(new Vector3f(0, (BASE + top) / 2, -0.45f),
                new Vector3f(half * 2 + 0.3f, top - BASE + 0.3f, 0.1f)));

        for (int level = 0; level < levels; level++) {
            float y = floorY(level);
            slabs.add(block(anchor, Material.POLISHED_BLACKSTONE, Props.box(new Vector3f(0, y - 0.06f, 0),
                    new Vector3f(half * 2 + 0.2f, 0.12f, 1.1f))));
            List<BlockDisplay> row = new ArrayList<>();
            for (int tile = 0; tile < this.tiles; tile++) {
                BlockDisplay door = block(anchor, Material.GRAY_CONCRETE, doorPose(level, tile, 1.0f));
                door.setBrightness(new Display.Brightness(7, 7));
                row.add(door);
            }
            doors.add(row);
            double multiplier = level < multipliers.length ? multipliers[level] : 0;
            labels.add(text(local(half + 1.55, y + floorHeight * 0.18, 0.2),
                    Text.c("&7" + Text.multiplier(multiplier)), Props.scaled(Math.min(1.1f, floorHeight * 1.0f)),
                    Display.Billboard.FIXED));
        }
        if (climberHead != null) {
            climber = item(local(-half - 1.3, floorY(0) + floorHeight * 0.45, 0.3), climberHead, Props.scaled(0.9f));
            climber.setTeleportDuration(8);
            climber.setBillboard(Display.Billboard.VERTICAL);
        }
        playSound(Sound.BLOCK_STONE_PLACE, 1.0f, 0.7f);
        current(0);
    }

    /**
     * Lights the floor being played.
     */
    public void current(int level) {
        if (!built || over) {
            return;
        }
        current = level;
        if (level >= levels) {
            return;
        }
        for (int tile = 0; tile < tiles; tile++) {
            BlockDisplay door = doors.get(level).get(tile);
            if (door.isValid()) {
                door.setBlock(Material.WHITE_CONCRETE.createBlockData());
                door.setBrightness(Props.FULL_BRIGHT);
                door.setGlowColorOverride(Color.fromRGB(0xFFD24A));
                door.setGlowing(true);
                Props.animate(door, doorPose(level, tile, 1.08f), 4);
            }
            text(local(doorX(tile), floorY(level) + floorHeight * 0.18, 0.2),
                    Text.c("&0&l" + (tile + 1)), Props.scaled(Math.min(1.2f, floorHeight * 1.1f)), Display.Billboard.FIXED);
        }
        label(level, "&e&l▶ ");
        moveClimber(level);
    }

    /**
     * A safe door: the floor is cleared, its bombs are shown and the next one lights up.
     */
    public void safe(int level, int tile, Set<Integer> bombs) {
        if (!built || level >= levels) {
            return;
        }
        List<BlockDisplay> row = doors.get(level);
        for (int index = 0; index < tiles; index++) {
            BlockDisplay door = row.get(index);
            if (!door.isValid()) {
                continue;
            }
            door.setGlowing(false);
            Props.animate(door, doorPose(level, index, 1.0f), 3);
            if (index == tile) {
                door.setBlock(Material.EMERALD_BLOCK.createBlockData());
            } else if (bombs.contains(index)) {
                door.setBlock(Material.TNT.createBlockData());
                door.setBrightness(new Display.Brightness(9, 9));
            } else {
                door.setBlock(Material.LIME_STAINED_GLASS.createBlockData());
            }
        }
        label(level, "&a&l✔ ");
        popGem(level, tile);
        particles(Particle.HAPPY_VILLAGER, doorX(tile), floorY(level) + floorHeight * 0.4, 0.4, 12, 0.3, 0.0);
        playSound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.0f + level * 0.08f);
        if (level + 1 < levels) {
            current(level + 1);
        } else {
            moveClimber(levels);
        }
    }

    /**
     * A bomb: the door blows up and every floor above it comes down.
     */
    public void boom(int level, int tile, Set<Integer> bombs) {
        if (!built || over) {
            return;
        }
        over = true;
        if (level < levels) {
            List<BlockDisplay> row = doors.get(level);
            for (int index = 0; index < tiles; index++) {
                BlockDisplay door = row.get(index);
                if (door.isValid()) {
                    door.setGlowing(false);
                    door.setBlock((bombs.contains(index) ? Material.TNT : Material.LIME_STAINED_GLASS).createBlockData());
                    if (index == tile) {
                        door.setBlock(Material.RED_CONCRETE.createBlockData());
                        door.setGlowColorOverride(Color.RED);
                        door.setGlowing(true);
                    }
                }
            }
        }
        double x = doorX(tile);
        double y = floorY(level) + floorHeight * 0.4;
        particles(Particle.EXPLOSION_EMITTER, x, y, 0.4, 1, 0.0, 0.0);
        particles(Particle.LAVA, x, y, 0.4, 16, 0.4, 0.1);
        particles(Particle.LARGE_SMOKE, x, y, 0.4, 20, 0.5, 0.05);
        playSound(Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
        // Everything above the blast tumbles down to the base.
        for (int above = level + 1; above < levels; above++) {
            for (BlockDisplay door : doors.get(above)) {
                fall(door);
            }
            fall(slabs.get(above));
        }
        if (climber != null && climber.isValid()) {
            climber.setTeleportDuration(12);
            climber.teleport(local(-tiles * DOOR_PITCH / 2 - 1.3, 0.9, 0.8));
        }
    }

    /**
     * Cashed out (or the top was reached): the tower lights up in gold.
     */
    public void cashOut(int reached) {
        if (!built || over) {
            return;
        }
        over = true;
        party = true;
        if (reached > 0) {
            label(Math.min(reached, levels) - 1, "&6&l★ ");
        }
        if (reached < levels) {
            for (BlockDisplay door : doors.get(reached)) {
                if (door.isValid()) {
                    door.setGlowing(false);
                    door.setBlock(Material.GRAY_CONCRETE.createBlockData());
                    door.setBrightness(new Display.Brightness(6, 6));
                }
            }
        }
        double y = floorY(Math.max(0, reached - 1)) + floorHeight;
        celebrate(0, y, 0.6);
        playSound(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
    }

    @Override
    protected void onAnimate(int age) {
        if (bulbs.isEmpty() || age % 3 != 0) {
            return;
        }
        int phase = age / 3;
        int perSide = bulbs.size() / 2;
        for (int i = 0; i < bulbs.size(); i++) {
            BlockDisplay bulb = bulbs.get(i);
            if (!bulb.isValid()) {
                continue;
            }
            int level = i % Math.max(1, perSide);
            boolean lit;
            if (party) {
                lit = phase % 2 == 0;
            } else if (over) {
                lit = false;
            } else {
                // The bulbs climb towards the floor being played.
                lit = level < current || (level == current && phase % 2 == 0);
            }
            bulb.setBlock((lit ? Material.GLOWSTONE : Material.BLACK_CONCRETE).createBlockData());
        }
        if (party && age % 12 == 0) {
            particles(Particle.FIREWORK, 0, floorY(levels) + 1.5, 0.3, 14, 1.5, 0.05);
        }
    }

    @Override
    protected int extraLinger() {
        return 30;
    }

    // ------------------------------------------------------------------ helpers

    private void moveClimber(int level) {
        if (climber == null || !climber.isValid()) {
            return;
        }
        float y = level >= levels ? floorY(levels) + 0.8f : floorY(level) + floorHeight * 0.45f;
        climber.teleport(local(-tiles * DOOR_PITCH / 2 - 1.3, y, 0.3));
    }

    private void popGem(int level, int tile) {
        Vector3f start = new Vector3f(doorX(tile), floorY(level) + floorHeight * 0.4f, 0.4f);
        ItemDisplay gem = item(anchor, Material.EMERALD, Props.centred(start, 0.1f));
        later(1, () -> Props.animate(gem, Props.centred(new Vector3f(start).add(0, 0.9f, 0.5f),
                new Quaternionf().rotateY((float) Math.PI), new Vector3f(0.8f, 0.8f, 0.8f)), 10));
        later(16, () -> Props.animate(gem, Props.centred(new Vector3f(start).add(0, 1.4f, 0.5f), 0.0f), 6));
        later(24, () -> discard(gem));
    }

    private void fall(BlockDisplay piece) {
        if (piece == null || !piece.isValid()) {
            return;
        }
        Transformation now = piece.getTransformation();
        Vector3f landing = new Vector3f(now.getTranslation()).add(
                (float) ((Rng.next() - 0.5) * 3), 0, (float) (Rng.next() * 2));
        landing.y = 0.8f + (float) Rng.next() * 0.4f;
        Quaternionf tumble = new Quaternionf().rotateXYZ((float) Rng.next() * 2f, (float) Rng.next() * 2f,
                (float) Rng.next() * 2f);
        Props.animate(piece, new Transformation(landing, tumble, now.getScale(), new Quaternionf()), 14);
        piece.setBrightness(new Display.Brightness(5, 5));
    }

    private Transformation doorPose(int level, int tile, float grow) {
        Vector3f centre = new Vector3f(doorX(tile), floorY(level) + floorHeight * 0.42f, 0.0f);
        return Props.box(centre, new Vector3f(DOOR_WIDTH * grow, floorHeight * 0.78f * grow, 0.3f));
    }

    private float doorX(int tile) {
        return (tile - (tiles - 1) / 2.0f) * DOOR_PITCH;
    }

    private float floorY(int level) {
        return BASE + level * floorHeight;
    }

    /**
     * Rewrites the multiplier written beside a floor with a mark in front of it.
     */
    private void label(int level, String mark) {
        if (level < 0 || level >= labels.size()) {
            return;
        }
        TextDisplay label = labels.get(level);
        if (label.isValid()) {
            double multiplier = level < multipliers.length ? multipliers[level] : 0;
            label.text(Text.c(mark + Text.multiplier(multiplier)));
        }
    }
}
