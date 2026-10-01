package com.chagui68.multiversegambling.world.anim;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.util.Text;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Horse;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.joml.Vector3f;

/**
 * A horse race on a stepped track: one lane per runner, each lane a step higher and
 * further back than the one in front, so the whole field is seen side on from the
 * audience. Real horses, wearing armour dyed in the colour of their lane, gallop from
 * the starting gates on the left to the chequered line on the right.
 *
 * <p>The game feeds the position of every runner on each step of its simulation, so
 * what is seen is the real race and not a decoration; the show only smooths the
 * motion between steps so the horses gallop instead of hopping.</p>
 */
public final class RaceShow extends ArenaShow {

    /** Where the runners start and finish, left to right in front of the audience. */
    private static final double START_X = -11.0;
    private static final double FINISH_X = 11.0;
    /** Depth the stepped track may take, front lane to back lane. */
    private static final double DEPTH = 17.0;

    private final List<Material> colours;
    private final double[] ratios;
    private final double[] shown;
    private final List<Horse> horses = new ArrayList<>();
    private final List<BlockDisplay> gates = new ArrayList<>();
    private final double spacing;
    private int winner = -1;

    /**
     * @param colours colour of the runner of each lane
     * @param ticks   frames the race lasts
     */
    public RaceShow(MultiverseGamblingPlugin plugin, ArenaStage stage, List<Material> colours, int ticks) {
        super(plugin, stage, ticks);
        this.colours = List.copyOf(colours);
        this.ratios = new double[colours.size()];
        this.shown = new double[colours.size()];
        this.spacing = Math.min(2.2, DEPTH / Math.max(1, colours.size()));
    }

    @Override
    protected void onStart() {
        Location anchor = local(0, 0, 0);
        float length = (float) (FINISH_X - START_X + 3.0);
        float middle = (float) ((START_X + FINISH_X) / 2);
        for (int lane = 0; lane < colours.size(); lane++) {
            float y = laneY(lane);
            float z = laneZ(lane);
            // The step under the lane, its sand and the white rail in front of it.
            if (y > 0.01f) {
                block(anchor, Material.POLISHED_DEEPSLATE, Props.box(new Vector3f(middle, y / 2, z),
                        new Vector3f(length, y, (float) spacing)));
            }
            block(anchor, Material.BROWN_CONCRETE_POWDER, Props.box(new Vector3f(middle, y + 0.03f, z),
                    new Vector3f(length, 0.06f, (float) spacing * 0.92f)));
            block(anchor, Material.WHITE_CONCRETE, Props.box(new Vector3f(middle, y + 0.35f, z + (float) spacing * 0.48f),
                    new Vector3f(length, 0.08f, 0.06f)));
            // Chequered finish line.
            for (int square = 0; square < 4; square++) {
                float sz = (float) spacing / 4;
                float squareZ = z - (float) spacing / 2 + sz * (square + 0.5f);
                for (int column = 0; column < 2; column++) {
                    boolean white = (square + column + lane) % 2 == 0;
                    block(anchor, white ? Material.WHITE_CONCRETE : Material.BLACK_CONCRETE,
                            Props.box(new Vector3f((float) FINISH_X + 0.5f + column * 0.5f, y + 0.07f, squareZ),
                                    new Vector3f(0.5f, 0.04f, sz)));
                }
            }
            // Starting gate, lifted when the race starts.
            gates.add(block(anchor, Material.IRON_BLOCK, gatePose(lane, 0.0f)));
            text(local(START_X - 1.6, y + 0.6, z), label(lane, "#" + (lane + 1)),
                    Props.scaled(1.1f), Display.Billboard.VERTICAL);
            horses.add(spawnHorse(lane));
        }
        // Finish banner over the back of the track.
        float top = laneY(colours.size() - 1) + 3.2f;
        text(local(FINISH_X + 0.75, top, laneZ(colours.size() - 1)), Text.c("&f&l⚑ FINISH"),
                Props.scaled(1.6f), Display.Billboard.VERTICAL);
        later(10, () -> {
            for (int lane = 0; lane < gates.size(); lane++) {
                Props.animate(gates.get(lane), gatePose(lane, 2.2f), 6);
            }
            playSound(Sound.BLOCK_IRON_DOOR_OPEN, 1.0f, 1.0f);
        });
        playSound(Sound.ENTITY_HORSE_AMBIENT, 1.0f, 1.0f);
    }

    private Horse spawnHorse(int lane) {
        Location spot = standing(lane, START_X);
        Horse.Color[] coats = Horse.Color.values();
        return spawn(spot, Horse.class, horse -> {
            horse.setAI(false);
            horse.setGravity(false);
            horse.setInvulnerable(true);
            horse.setSilent(true);
            horse.setCollidable(false);
            horse.setRemoveWhenFarAway(false);
            horse.setAdult();
            horse.setColor(coats[lane % coats.length]);
            horse.setStyle(Horse.Style.NONE);
            horse.customName(label(lane, "#" + (lane + 1)));
            horse.setCustomNameVisible(true);
            ItemStack armour = new ItemStack(Material.LEATHER_HORSE_ARMOR);
            if (armour.getItemMeta() instanceof LeatherArmorMeta meta) {
                meta.setColor(Props.colourOf(colours.get(lane)));
                armour.setItemMeta(meta);
            }
            horse.getInventory().setArmor(armour);
        });
    }

    /** Moves the runners: 0 at the start, 1 on the finish line. */
    public void progress(double[] ratios) {
        int lanes = Math.min(this.ratios.length, ratios.length);
        for (int lane = 0; lane < lanes; lane++) {
            this.ratios[lane] = Math.max(0, Math.min(1, ratios[lane]));
        }
    }

    /** The race is over: the winner is crowned on the finish line. */
    public void finish(int winner) {
        if (winner < 0 || winner >= colours.size()) {
            return;
        }
        this.winner = winner;
        ratios[winner] = 1.0;
        Horse horse = horses.get(winner);
        if (horse.isValid()) {
            horse.setGlowing(true);
            horse.setRearing(true);
        }
        double y = laneY(winner) + 2.5;
        double z = laneZ(winner);
        particles(Particle.FIREWORK, FINISH_X, y, z, 50, 0.8, 0.15);
        particles(Particle.TOTEM_OF_UNDYING, FINISH_X, y, z, 40, 0.6, 0.4);
        TextBanner.raise(this, local(FINISH_X, y + 1.0, z), label(winner, "#" + (winner + 1) + " ★"));
        playSound(Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.2f);
        playSound(Sound.ENTITY_HORSE_GALLOP, 1.0f, 1.0f);
    }

    @Override
    protected void onAnimate(int age) {
        for (int lane = 0; lane < horses.size(); lane++) {
            Horse horse = horses.get(lane);
            if (!horse.isValid()) {
                continue;
            }
            double target = ratios[lane];
            double next = shown[lane] + (target - shown[lane]) * 0.4;
            if (Math.abs(next - shown[lane]) < 1e-4) {
                continue;
            }
            shown[lane] = next;
            horse.teleport(standing(lane, START_X + (FINISH_X - START_X) * next));
        }
        if (age % 8 == 0 && active()) {
            playSound(Sound.ENTITY_HORSE_GALLOP, 0.5f, 1.0f);
        }
        if (winner >= 0 && age % 10 == 0) {
            Horse horse = horses.get(winner);
            if (horse.isValid()) {
                horse.setRearing(age % 20 == 0);
            }
        }
    }

    private Location standing(int lane, double x) {
        Location spot = local(x, laneY(lane) + 0.06, laneZ(lane));
        spot.setYaw(stage().frame().lookYaw(0, 0, 1, 0));
        return spot;
    }

    private org.bukkit.util.Transformation gatePose(int lane, float lift) {
        return Props.box(new Vector3f((float) START_X - 0.6f, laneY(lane) + 0.9f + lift, laneZ(lane)),
                new Vector3f(0.18f, 1.6f, (float) spacing * 0.9f));
    }

    /** Front lane on the floor, every other one a step up and back. */
    private float laneY(int lane) {
        return (float) (lane * spacing * 0.55);
    }

    private float laneZ(int lane) {
        return (float) (3.0 - lane * spacing);
    }

    /**
     * Bold text in the colour of a lane.
     */
    private Component label(int lane, String content) {
        Color colour = Props.colourOf(colours.get(lane));
        return Component.text(content, TextColor.color(colour.asRGB() & 0xFFFFFF), TextDecoration.BOLD);
    }

    /**
     * A banner that pops out of nowhere: used for the winner of the race.
     */
    private static final class TextBanner {

        private TextBanner() {
        }

        static void raise(RaceShow show, Location where, Component content) {
            org.bukkit.entity.TextDisplay banner = show.text(where, content, Props.scaled(0.1f),
                    Display.Billboard.VERTICAL);
            banner.setBackgroundColor(Props.argb(150, 0, 0, 0));
            show.later(1, () -> Props.animate(banner, Props.scaled(2.4f), 6));
        }
    }
}
