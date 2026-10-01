package com.chagui68.multiversegambling.world.anim;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
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
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

/**
 * The hot bomb: a huge block of TNT on a podium that throbs faster and faster as its fuse
 * burns down, sparks running along the fuse, and a small TNT floating over the head of
 * whoever is holding the bomb right now, flying to the next player when it is passed.
 *
 * <p>The fuse is the suspense of the game made visible: the round is decided by a roll on
 * every tick, so the fuse only shows how much of the drawn time is left. When it goes off
 * the bomb bursts in a flash and grows back on the podium a moment later for the next
 * round of passes.</p>
 */
public final class BombShow extends ArenaShow {

    /** Pieces of fuse at full length. */
    private static final int FUSE = 12;
    private static final float BOMB_Y = 2.35f;
    private static final float BOMB = 1.9f;
    /** Ticks the podium stays empty after a blast. */
    private static final int REBUILD_TICKS = 18;

    private final List<BlockDisplay> fuse = new ArrayList<>();
    private Location anchor;
    private ItemDisplay bomb;
    private ItemDisplay carried;
    private TextDisplay holderLabel;
    private Player holder;
    private double ratio = 1.0;
    private int shownFuse = FUSE;
    private int age;
    private int blastAge = -1;
    private boolean swollen;
    private int nextPulse;

    public BombShow(MultiverseGamblingPlugin plugin, ArenaStage stage, int ticks) {
        super(plugin, stage, ticks);
    }

    @Override
    protected void onStart() {
        anchor = local(0, 0, 0);
        block(anchor, Material.POLISHED_BLACKSTONE_BRICKS, Props.box(new Vector3f(0, 0.45f, 0), new Vector3f(3.0f, 0.9f, 3.0f)));
        block(anchor, Material.POLISHED_BLACKSTONE_BRICKS, Props.box(new Vector3f(0, 0.45f, 0), Props.yaw(Math.PI / 4),
                new Vector3f(3.0f, 0.9f, 3.0f)));
        block(anchor, Material.RED_CONCRETE, Props.box(new Vector3f(0, 0.95f, 0), new Vector3f(3.2f, 0.12f, 3.2f)));
        block(anchor, Material.POLISHED_DEEPSLATE, Props.box(new Vector3f(0, 0.06f, 0), new Vector3f(6.0f, 0.12f, 6.0f)));
        // Hazard stripes round the podium.
        for (int i = 0; i < 16; i++) {
            double angle = WheelMath.TAU * i / 16;
            Vector3f spot = new Vector3f((float) WheelMath.x(angle, 2.6), 0.14f, (float) WheelMath.z(angle, 2.6));
            block(anchor, i % 2 == 0 ? Material.YELLOW_CONCRETE : Material.BLACK_CONCRETE,
                    Props.box(spot, Props.yaw(angle), new Vector3f(0.9f, 0.06f, 0.4f)));
        }
        bomb = item(anchor, Material.TNT, bombPose(BOMB));
        for (int piece = 0; piece < FUSE; piece++) {
            fuse.add(block(anchor, piece == 0 ? Material.GRAY_CONCRETE : Material.BROWN_CONCRETE, fusePose(piece)));
        }
        holderLabel = text(local(0, BOMB_Y + 3.4, 0), Text.c(""), Props.scaled(1.4f), Display.Billboard.VERTICAL);
        holderLabel.setBackgroundColor(Props.argb(120, 0, 0, 0));
        playSound(Sound.ENTITY_TNT_PRIMED, 1.0f, 0.8f);
    }

    /** Shortens the fuse: {@code ratio} is the share of the drawn time that is left. */
    public void burn(double ratio) {
        this.ratio = Math.max(0, Math.min(1, ratio));
        if (blastAge >= 0 && age - blastAge >= REBUILD_TICKS) {
            rebuild();
        }
        int length = (int) Math.ceil(this.ratio * FUSE);
        if (length == shownFuse || blastAge >= 0) {
            return;
        }
        for (int piece = 0; piece < fuse.size(); piece++) {
            BlockDisplay part = fuse.get(piece);
            if (part.isValid()) {
                float size = piece < length ? 1.0f : 0.0f;
                Props.animate(part, fusePose(piece, size), 2);
            }
        }
        shownFuse = length;
    }

    /** The bomb is in somebody else's hands now. */
    public void holder(Player player) {
        holder(player, player == null ? "" : player.getName());
    }

    /**
     * The bomb is in somebody else's hands now; {@code player} is {@code null} when it is
     * not a player (the house in a duel), and the small bomb then waits over the podium.
     */
    public void holder(Player player, String name) {
        this.holder = player;
        if (holderLabel != null && holderLabel.isValid()) {
            holderLabel.text(Text.c(name == null || name.isEmpty() ? "" : "&c☠ &f&l" + name));
        }
        if (carried == null || !carried.isValid()) {
            carried = spawn(overHead(player), ItemDisplay.class, display -> {
                display.setItemStack(new org.bukkit.inventory.ItemStack(Material.TNT));
                display.setTransformation(Props.scaled(0.7f));
                display.setTeleportDuration(2);
            });
        } else {
            // A quick throw to the next holder.
            carried.setTeleportDuration(6);
            carried.teleport(overHead(player));
            later(6, () -> {
                if (carried != null && carried.isValid()) {
                    carried.setTeleportDuration(2);
                }
            });
        }
        playSound(Sound.ENTITY_SNOWBALL_THROW, 0.8f, 0.8f);
    }

    /** It went off on whoever was holding it. */
    public void blast() {
        blastAge = age;
        Props.animate(bomb, bombPose(BOMB * 1.9f), 3);
        later(3, () -> Props.animate(bomb, bombPose(0.0f), 2));
        for (int piece = 0; piece < fuse.size(); piece++) {
            Props.animate(fuse.get(piece), fusePose(piece, 0.0f), 2);
        }
        particles(Particle.EXPLOSION_EMITTER, 0, BOMB_Y, 0, 1, 0.0, 0.0);
        particles(Particle.LAVA, 0, BOMB_Y, 0, 25, 0.8, 0.2);
        particles(Particle.LARGE_SMOKE, 0, BOMB_Y, 0, 40, 1.2, 0.05);
        if (holder != null && holder.isValid()) {
            holder.getWorld().spawnParticle(Particle.EXPLOSION, holder.getLocation().add(0, 1, 0), 3, 0.3, 0.3, 0.3, 0);
        }
        if (carried != null && carried.isValid()) {
            discard(carried);
            carried = null;
        }
        shownFuse = 0;
        playSound(Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.9f);
    }

    private void rebuild() {
        blastAge = -1;
        Props.animate(bomb, bombPose(BOMB), 6);
        shownFuse = -1;
        burn(ratio);
        if (holderLabel != null && holderLabel.isValid() && holder != null) {
            holder(holder);
        }
        playSound(Sound.ENTITY_TNT_PRIMED, 0.9f, 1.2f);
    }

    @Override
    protected void onAnimate(int age) {
        this.age = age;
        if (blastAge >= 0) {
            if (age - blastAge >= REBUILD_TICKS + 20) {
                // The game stopped burning the fuse (the round is over): grow back anyway.
                rebuild();
            }
            return;
        }
        // The bomb throbs, faster as the fuse gets shorter.
        if (age >= nextPulse) {
            int half = (int) Math.max(2, Math.round(2 + 9 * ratio));
            swollen = !swollen;
            Props.animate(bomb, bombPose(swollen ? BOMB * 1.12f : BOMB), half);
            nextPulse = age + half;
            if (swollen && ratio < 0.35) {
                playSound(Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.6f, 1.6f);
            }
        }
        if (age % 2 == 0 && shownFuse > 0) {
            Vector3f tip = fusePoint(Math.max(0, shownFuse - 1));
            particles(Particle.FLAME, tip.x, tip.y + 0.1, tip.z, 1, 0.02, 0.01);
            particles(Particle.ELECTRIC_SPARK, tip.x, tip.y + 0.1, tip.z, 2, 0.06, 0.05);
            particles(Particle.SMOKE, tip.x, tip.y + 0.2, tip.z, 1, 0.02, 0.01);
        }
        if (carried != null && carried.isValid() && holder != null && holder.isValid()
                && carried.getTeleportDuration() <= 2) {
            carried.teleport(overHead(holder));
        }
    }

    private Location overHead(Player player) {
        if (player == null || !player.isValid()) {
            return local(0, BOMB_Y + BOMB + 1.6, 0);
        }
        return player.getLocation().add(0, player.getHeight() + 0.6, 0);
    }

    private Transformation bombPose(float size) {
        return Props.centred(new Vector3f(0, BOMB_Y, 0), Props.none(), new Vector3f(size, size, size));
    }

    private Transformation fusePose(int piece) {
        return fusePose(piece, 1.0f);
    }

    private Transformation fusePose(int piece, float size) {
        float side = 0.16f * size;
        return Props.box(fusePoint(piece), new Vector3f(side, side, side));
    }

    /**
     * The fuse leaves the top of the bomb and curls up and to the right.
     */
    private static Vector3f fusePoint(int piece) {
        double s = piece / (double) (FUSE - 1);
        float x = (float) (0.1 + 1.3 * s);
        float y = (float) (BOMB_Y + BOMB / 2 + 0.05 + 1.0 * Math.sin(s * Math.PI * 0.85));
        return new Vector3f(x, y, 0);
    }
}
