package com.chagui68.multiversegambling.world.anim;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.util.Text;

import java.util.ArrayList;
import java.util.List;

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
 * A coin tossed from a pedestal: it shoots up, flips over and over, comes down and ends
 * standing on its edge, facing the audience with the side that really came up.
 *
 * <p>The coin is a thin octagonal plate. It turns a quarter at a time and the client
 * interpolates the turn, so it really tumbles; every time it is seen edge on its face is
 * swapped, which is what makes a one sided plate read as a coin with two faces. The
 * swaps are counted backwards from the landing, so the last face shown is always the
 * side decided by the provably fair generator before the toss.</p>
 *
 * <p>Two contenders can be hung on either side (the duel uses the heads of both
 * players): the one whose side comes up lights up, the other one drops.</p>
 */
public final class CoinFlipShow extends ArenaShow {

    /** Frames per quarter turn while the coin is in the air. */
    private static final int QUARTER = 2;
    /** Highest point of the toss over the pedestal. */
    private static final double TOSS_HEIGHT = 5.5;
    /** Top of the pedestal. */
    private static final float REST_Y = 1.25f;
    /** Height of the middle of the coin standing on its edge. */
    private static final float SHOW_Y = 2.25f;
    private static final float COIN = 1.8f;
    /** Share of the show spent in the air. */
    private static final double TOSS_SHARE = 0.72;

    private final boolean heads;
    private Material headsFace = Material.GOLD_BLOCK;
    private Material tailsFace = Material.IRON_BLOCK;
    private String headsCaption = "";
    private String tailsCaption = "";
    private ItemStack headsIcon;
    private ItemStack tailsIcon;

    private final List<ItemDisplay> coin = new ArrayList<>();
    private final List<BlockDisplay> lights = new ArrayList<>();
    private ItemDisplay headsSide;
    private ItemDisplay tailsSide;
    private Location anchor;
    private int quarters;
    private int turns;
    private int lastStep = -1;
    private boolean landed;

    public CoinFlipShow(MultiverseGamblingPlugin plugin, ArenaStage stage, boolean heads, int ticks) {
        super(plugin, stage, ticks);
        this.heads = heads;
    }

    /**
     * Words written over the coin when it lands, one per side.
     */
    public CoinFlipShow captions(String heads, String tails) {
        this.headsCaption = heads == null ? "" : heads;
        this.tailsCaption = tails == null ? "" : tails;
        return this;
    }

    /**
     * Materials of the two faces of the coin.
     */
    public CoinFlipShow faces(Material heads, Material tails) {
        this.headsFace = heads;
        this.tailsFace = tails;
        return this;
    }

    /**
     * Who stands for each side, shown floating left (heads) and right (tails).
     */
    public CoinFlipShow contenders(ItemStack heads, ItemStack tails) {
        this.headsIcon = heads;
        this.tailsIcon = tails;
        return this;
    }

    // ------------------------------------------------------------------ setup

    @Override
    protected void onStart() {
        anchor = local(0, 0, 0);
        // Pedestal: a dark column with a golden top.
        block(anchor, Material.POLISHED_BLACKSTONE_BRICKS,
                Props.box(new Vector3f(0, 0.55f, 0), new Vector3f(1.5f, 1.1f, 1.5f)));
        block(anchor, Material.POLISHED_BLACKSTONE_BRICKS, Props.box(new Vector3f(0, 0.55f, 0),
                Props.yaw(Math.PI / 4), new Vector3f(1.5f, 1.1f, 1.5f)));
        block(anchor, Material.GOLD_BLOCK, Props.box(new Vector3f(0, 1.15f, 0), new Vector3f(1.8f, 0.12f, 1.8f)));
        block(anchor, Material.POLISHED_DEEPSLATE, Props.box(new Vector3f(0, 0.06f, 0), new Vector3f(4.6f, 0.12f, 4.6f)));
        for (int i = 0; i < 12; i++) {
            double angle = WheelMath.TAU * i / 12;
            Vector3f spot = new Vector3f((float) WheelMath.x(angle, 2.6), 0.18f, (float) WheelMath.z(angle, 2.6));
            lights.add(block(anchor, Material.GLOWSTONE, Props.box(spot, new Vector3f(0.3f, 0.12f, 0.3f))));
        }
        for (int plate = 0; plate < 2; plate++) {
            coin.add(item(anchor, headsFace, coinPose(REST_Y + 0.08f, 0.0, plate)));
        }
        if (headsIcon != null) {
            headsSide = item(local(-3.6, 3.0, 0), headsIcon, Props.scaled(2.6f));
            headsSide.setBillboard(Display.Billboard.VERTICAL);
            text(local(-3.6, 4.4, 0), Text.c("&6&l" + headsCaption), Props.scaled(1.1f), Display.Billboard.VERTICAL);
        }
        if (tailsIcon != null) {
            tailsSide = item(local(3.6, 3.0, 0), tailsIcon, Props.scaled(2.6f));
            tailsSide.setBillboard(Display.Billboard.VERTICAL);
            text(local(3.6, 4.4, 0), Text.c("&7&l" + tailsCaption), Props.scaled(1.1f), Display.Billboard.VERTICAL);
        }
        // The flight has a whole number of quarter turns that ends face on to the
        // audience: one quarter past a whole number of turns.
        int frames = (int) (duration() * TOSS_SHARE);
        turns = Math.max(1, (frames / QUARTER - 1) / 4);
        quarters = 4 * turns + 1;
        playSound(Sound.ENTITY_ITEM_PICKUP, 0.8f, 0.6f);
    }

    // ----------------------------------------------------------------- frames

    @Override
    protected void onFrame(int elapsed, int duration) {
        if (landed) {
            return;
        }
        int step = elapsed / QUARTER + 1;
        if (step == lastStep) {
            return;
        }
        lastStep = step;
        if (step > quarters) {
            land();
            return;
        }
        // The quarter that just finished: the coin is edge on every half turn, which is
        // when its face can be swapped without anybody seeing it change.
        int reached = step - 1;
        if (reached % 2 == 0) {
            int swap = reached / 2;
            int lastSwap = (quarters - 1) / 2;
            boolean showResult = (lastSwap - swap) % 2 == 0;
            Material face = showResult == heads ? headsFace : tailsFace;
            for (ItemDisplay plate : coin) {
                if (plate.isValid()) {
                    plate.setItemStack(new ItemStack(face));
                }
            }
        }
        double flight = step / (double) quarters;
        double height = REST_Y + 0.08 + 4 * TOSS_HEIGHT * flight * (1 - flight);
        // On the way down the coin also drifts to its standing height.
        if (flight > 0.5) {
            height = Math.max(height, REST_Y + (SHOW_Y - REST_Y) * (flight - 0.5) * 2);
        }
        for (int plate = 0; plate < coin.size(); plate++) {
            Props.animate(coin.get(plate), coinPose((float) height, step * Math.PI / 2, plate), QUARTER);
        }
        if (step % 2 == 0) {
            playSound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, (float) (1.0 + flight));
            particles(Particle.WAX_ON, 0, height, 0, 3, 0.3, 0.0);
        }
    }

    private void land() {
        if (landed) {
            return;
        }
        landed = true;
        Material face = heads ? headsFace : tailsFace;
        for (int plate = 0; plate < coin.size(); plate++) {
            ItemDisplay display = coin.get(plate);
            if (display.isValid()) {
                display.setItemStack(new ItemStack(face));
                display.setGlowColorOverride(heads ? Color.fromRGB(0xFFD700) : Color.fromRGB(0xE0E0E0));
                display.setGlowing(true);
            }
            Props.animate(display, coinPose(SHOW_Y, Math.PI / 2, plate), 3);
        }
        String caption = heads ? headsCaption : tailsCaption;
        if (!caption.isBlank()) {
            TextDisplay label = text(local(0, SHOW_Y + 1.6, 0.2), Text.c((heads ? "&6&l" : "&f&l") + caption),
                    Props.scaled(0.1f), Display.Billboard.VERTICAL);
            label.setBackgroundColor(Props.argb(140, 0, 0, 0));
            later(1, () -> Props.animate(label, Props.scaled(2.4f), 6));
        }
        ItemDisplay winner = heads ? headsSide : tailsSide;
        ItemDisplay loser = heads ? tailsSide : headsSide;
        if (winner != null && winner.isValid()) {
            winner.setGlowColorOverride(Color.fromRGB(0xFFD700));
            winner.setGlowing(true);
            Props.animate(winner, Props.centred(new Vector3f(0, 0.4f, 0), 2.0f), 6);
        }
        if (loser != null && loser.isValid()) {
            loser.setBrightness(new Display.Brightness(3, 3));
            Props.animate(loser, Props.centred(new Vector3f(0, -2.2f, 0), 0.8f), 10);
        }
        particles(Particle.FIREWORK, 0, SHOW_Y, 0, 30, 0.6, 0.12);
        particles(Particle.END_ROD, 0, SHOW_Y, 0, 16, 0.9, 0.04);
        playSound(heads ? Sound.BLOCK_NOTE_BLOCK_BELL : Sound.BLOCK_ANVIL_LAND, 0.8f, 1.2f);
        playSound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.0f);
    }

    @Override
    protected void onSettle() {
        land();
    }

    @Override
    protected void onAnimate(int age) {
        if (age % 4 != 0) {
            return;
        }
        int phase = age / 4;
        for (int i = 0; i < lights.size(); i++) {
            BlockDisplay light = lights.get(i);
            if (light.isValid()) {
                boolean lit = landed ? phase % 2 == 0 : (i + phase) % 3 == 0;
                light.setBlock((lit ? Material.GLOWSTONE : Material.YELLOW_TERRACOTTA).createBlockData());
            }
        }
        // Once standing, the coin turns a little from side to side, showing off.
        if (landed && age % 20 == 0) {
            double sway = (age / 20) % 2 == 0 ? 0.35 : -0.35;
            for (int plate = 0; plate < coin.size(); plate++) {
                Props.animate(coin.get(plate), coinPose(SHOW_Y, Math.PI / 2, plate, sway), 20);
            }
        }
    }

    private Transformation coinPose(float height, double flip, int plate) {
        return coinPose(height, flip, plate, 0.0);
    }

    /**
     * Pose of one of the two plates of the coin: the second is turned an eighth round
     * the face, which makes the pair an octagon.
     */
    private Transformation coinPose(float height, double flip, int plate, double sway) {
        Quaternionf rotation = Props.yaw(sway).mul(Props.pitch(flip)).mul(Props.yaw(plate * Math.PI / 4));
        return Props.centred(new Vector3f(0, height, 0), rotation, new Vector3f(COIN, 0.14f, COIN));
    }
}
