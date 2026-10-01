package com.chagui68.multiversegambling.world.anim;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.Rng;
import com.chagui68.multiversegambling.util.Text;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
 * A slot machine standing on the pavilion: a red cabinet with a lit marquee, a window
 * with three reels, a lever that is pulled when the spin starts and bulbs that chase
 * each other round the top.
 *
 * <p>Each reel is a little drum of five symbols that really rolls: the symbols slide
 * down one row at a time, squash as they go over the top and the bottom of the drum,
 * and the reel slows down over its last rows before it settles on the symbol the
 * provably fair generator chose. The reels stop left to right. Nothing here decides
 * anything: the combination is drawn before the lever is pulled.</p>
 */
public final class SlotReelsShow extends ArenaShow {

    /** Column of each reel. */
    private static final float[] REEL_X = {-1.7f, 0.0f, 1.7f};
    /** Height of each drum position, top (hidden) to bottom (hidden). */
    private static final float[] ROW_Y = {5.55f, 4.95f, 3.9f, 2.85f, 2.25f};
    /** How tall a symbol is drawn at each drum position: squashed over the edges. */
    private static final float[] ROW_SQUASH = {0.0f, 0.62f, 1.0f, 0.62f, 0.0f};
    private static final float SYMBOL = 0.95f;
    private static final float SYMBOL_Z = 0.78f;
    private static final int DRUM = 5;
    /** Middle row of the window: the payline. */
    private static final int PAYLINE = 2;

    private final List<Material> result;
    private final List<Material> pool;
    private final String title;

    private final ItemDisplay[][] symbols = new ItemDisplay[3][DRUM];
    private final int[] steps = new int[3];
    private final int[] nextStep = new int[3];
    private final int[] stepsLeft = {-1, -1, -1};
    private final boolean[] stopped = new boolean[3];
    @SuppressWarnings("unchecked")
    private final Deque<Material>[] queue = new Deque[]{new ArrayDeque<>(), new ArrayDeque<>(), new ArrayDeque<>()};
    private final List<BlockDisplay> bulbs = new ArrayList<>();
    private BlockDisplay leverArm;
    private ItemDisplay leverKnob;
    private Location anchor;
    private int celebration;
    private List<String> paytable = List.of();
    private boolean replay;

    /**
     * @param result symbol of each reel on the payline, one per reel
     * @param pool   symbols to roll through while the reels spin
     * @param ticks  frames the spin lasts
     */
    public SlotReelsShow(MultiverseGamblingPlugin plugin, ArenaStage stage,
                         List<Material> result, List<Material> pool, int ticks) {
        this(plugin, stage, result, pool, ticks, "SLOTS");
    }

    /**
     * @param title text of the marquee over the window
     */
    public SlotReelsShow(MultiverseGamblingPlugin plugin, ArenaStage stage,
                         List<Material> result, List<Material> pool, int ticks, String title) {
        super(plugin, stage, ticks);
        this.result = List.copyOf(result);
        this.pool = List.copyOf(pool);
        this.title = title == null || title.isBlank() ? "SLOTS" : title;
    }

    /**
     * Lines of the prize table, hung on a sign beside the cabinet.
     */
    public SlotReelsShow paytable(List<String> lines) {
        this.paytable = lines == null ? List.of() : List.copyOf(lines);
        return this;
    }

    /**
     * After the result: a button to spin again with the same stake and one to pick
     * another stake, in front of the player. The machine then stays standing a while
     * longer so there is time to press them; spinning again replaces it at once.
     */
    public void offerReplay(java.util.UUID owner, net.kyori.adventure.text.Component again,
                            net.kyori.adventure.text.Component change,
                            java.util.function.Consumer<org.bukkit.entity.Player> onAgain,
                            java.util.function.Consumer<org.bukkit.entity.Player> onChange) {
        replay = true;
        addControlRow(0.75, owner, java.util.List.of(
                new HoloButton.Spec(again, HoloButton.GREEN, 1.3f, onAgain),
                new HoloButton.Spec(change, HoloButton.BLUE, 1.3f, onChange)));
    }

    // ------------------------------------------------------------------ setup

    @Override
    protected void onStart() {
        anchor = local(0, 0, 0);
        buildCabinet();
        buildLever();
        for (int reel = 0; reel < 3; reel++) {
            for (int i = 0; i < DRUM; i++) {
                int slot = i;
                symbols[reel][i] = item(anchor, random(), symbolPose(reel, slot, 1.0f));
            }
        }
        if (!paytable.isEmpty()) {
            TextDisplay sign = text(local(-5.3, 2.0, -0.2), Text.c(String.join("\n", paytable)),
                    Props.scaled(0.75f), Display.Billboard.FIXED);
            sign.setBackgroundColor(Props.argb(190, 20, 10, 10));
            sign.setAlignment(TextDisplay.TextAlignment.LEFT);
            block(anchor, Material.GOLD_BLOCK, Props.box(new Vector3f(-5.3f, 1.85f, -0.3f),
                    new Vector3f(2.6f, 0.08f, 0.2f)));
        }
        pullLever();
        playSound(Sound.BLOCK_LEVER_CLICK, 1.0f, 0.8f);
    }

    private void buildCabinet() {
        // Base, body, marquee.
        block(anchor, Material.POLISHED_BLACKSTONE_BRICKS,
                Props.box(new Vector3f(0, 0.6f, -0.6f), new Vector3f(6.6f, 1.2f, 3.0f)));
        block(anchor, Material.RED_CONCRETE,
                Props.box(new Vector3f(0, 3.7f, -0.75f), new Vector3f(6.0f, 5.0f, 2.4f)));
        block(anchor, Material.BLACK_CONCRETE,
                Props.box(new Vector3f(0, 6.85f, -0.6f), new Vector3f(6.6f, 1.3f, 2.7f)));
        block(anchor, Material.GOLD_BLOCK,
                Props.box(new Vector3f(0, 7.55f, -0.6f), new Vector3f(6.8f, 0.12f, 2.9f)));
        block(anchor, Material.GOLD_BLOCK,
                Props.box(new Vector3f(0, 1.25f, -0.6f), new Vector3f(6.8f, 0.12f, 3.1f)));
        // Coin tray under the window.
        block(anchor, Material.GOLD_BLOCK,
                Props.box(new Vector3f(0, 1.55f, 0.75f), new Vector3f(3.2f, 0.18f, 0.8f)));
        block(anchor, Material.BLACK_CONCRETE,
                Props.box(new Vector3f(0, 1.62f, 0.75f), new Vector3f(2.8f, 0.06f, 0.55f)));

        // The window: white glass behind the symbols, a gold frame round it.
        block(anchor, Material.WHITE_CONCRETE,
                Props.box(new Vector3f(0, 3.9f, 0.5f), new Vector3f(5.1f, 3.5f, 0.1f)));
        frame(new Vector3f(0, 5.72f, 0.6f), new Vector3f(5.5f, 0.2f, 0.14f));
        frame(new Vector3f(0, 2.08f, 0.6f), new Vector3f(5.5f, 0.2f, 0.14f));
        frame(new Vector3f(-2.65f, 3.9f, 0.6f), new Vector3f(0.2f, 3.84f, 0.14f));
        frame(new Vector3f(2.65f, 3.9f, 0.6f), new Vector3f(0.2f, 3.84f, 0.14f));
        for (float x : new float[]{-0.85f, 0.85f}) {
            block(anchor, Material.GRAY_CONCRETE,
                    Props.box(new Vector3f(x, 3.9f, 0.58f), new Vector3f(0.07f, 3.4f, 0.04f)));
        }
        // Payline arrows on both sides of the window.
        for (int side : new int[]{-1, 1}) {
            block(anchor, Material.REDSTONE_BLOCK, Props.box(new Vector3f(side * 2.95f, 3.9f, 0.62f),
                    Props.roll(Math.PI / 4), new Vector3f(0.32f, 0.32f, 0.1f)));
        }

        // Marquee with the name of the game and a ring of bulbs.
        TextDisplay marquee = text(local(0, 6.5f, 0.78f), Text.c("&6&l★ &e&l" + title + " &6&l★"),
                Props.scaled(1.7f), Display.Billboard.FIXED);
        marquee.setBackgroundColor(Props.argb(0, 0, 0, 0));
        for (int i = 0; i <= 12; i++) {
            float x = -3.0f + i * 0.5f;
            bulbs.add(block(anchor, Material.GLOWSTONE,
                    Props.box(new Vector3f(x, 6.22f, 0.78f), new Vector3f(0.22f, 0.22f, 0.12f))));
            bulbs.add(block(anchor, Material.GLOWSTONE,
                    Props.box(new Vector3f(x, 7.4f, 0.78f), new Vector3f(0.22f, 0.22f, 0.12f))));
        }
    }

    private void frame(Vector3f centre, Vector3f size) {
        block(anchor, Material.GOLD_BLOCK, Props.box(centre, size));
    }

    private void buildLever() {
        block(anchor, Material.IRON_BLOCK,
                Props.box(new Vector3f(3.2f, 3.4f, -0.4f), new Vector3f(0.5f, 0.7f, 0.7f)));
        leverArm = block(anchor, Material.IRON_BLOCK, armPose(0.0));
        leverKnob = item(anchor, Material.REDSTONE_BLOCK, knobPose(0.0));
    }

    private void pullLever() {
        later(1, () -> {
            Props.animate(leverArm, armPose(Math.toRadians(35)), 2);
            Props.animate(leverKnob, knobPose(Math.toRadians(35)), 2);
        });
        later(3, () -> {
            Props.animate(leverArm, armPose(Math.toRadians(70)), 2);
            Props.animate(leverKnob, knobPose(Math.toRadians(70)), 2);
        });
        later(7, () -> {
            Props.animate(leverArm, armPose(Math.toRadians(30)), 4);
            Props.animate(leverKnob, knobPose(Math.toRadians(30)), 4);
        });
        later(11, () -> {
            Props.animate(leverArm, armPose(0.0), 4);
            Props.animate(leverKnob, knobPose(0.0), 4);
        });
    }

    private Transformation armPose(double angle) {
        Quaternionf rotation = Props.pitch(angle);
        Vector3f centre = rotation.transform(new Vector3f(0, 1.1f, 0)).add(3.45f, 3.4f, -0.4f);
        return Props.box(centre, rotation, new Vector3f(0.16f, 2.2f, 0.16f));
    }

    private Transformation knobPose(double angle) {
        Quaternionf rotation = Props.pitch(angle);
        Vector3f centre = rotation.transform(new Vector3f(0, 2.3f, 0)).add(3.45f, 3.4f, -0.4f);
        return Props.centred(centre, rotation, new Vector3f(0.5f, 0.5f, 0.5f));
    }

    // ----------------------------------------------------------------- frames

    @Override
    protected void onFrame(int elapsed, int duration) {
        for (int reel = 0; reel < 3; reel++) {
            if (stopped[reel]) {
                continue;
            }
            if (stepsLeft[reel] < 0 && elapsed >= stopFrame(reel, duration)) {
                // Three more rows: the next symbol to come over the top is the result,
                // and it reaches the payline on the third row.
                queue[reel].clear();
                queue[reel].add(symbolOf(reel));
                stepsLeft[reel] = 3;
            }
            if (elapsed >= nextStep[reel]) {
                step(reel, elapsed);
            }
        }
        if (elapsed % 2 == 0 && !(stopped[0] && stopped[1] && stopped[2])) {
            playSound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.35f, (float) (0.9 + progress(elapsed)));
        }
    }

    /**
     * The reel rolls one row down.
     */
    private void step(int reel, int elapsed) {
        int period = stepsLeft[reel] < 0 ? 1 : 5 - stepsLeft[reel];
        steps[reel]++;
        for (int i = 0; i < DRUM; i++) {
            ItemDisplay symbol = symbols[reel][i];
            if (symbol == null || !symbol.isValid()) {
                continue;
            }
            int slot = (i + steps[reel]) % DRUM;
            if (slot == 0) {
                // Out of sight under the window: it jumps back over the top with a new face.
                Material next = queue[reel].isEmpty() ? random() : queue[reel].poll();
                symbol.setItemStack(new ItemStack(next));
                Props.animate(symbol, symbolPose(reel, 0, 1.0f), 0);
            } else {
                Props.animate(symbol, symbolPose(reel, slot, 1.0f), period);
            }
        }
        nextStep[reel] = elapsed + period;
        if (stepsLeft[reel] > 0) {
            stepsLeft[reel]--;
            if (stepsLeft[reel] == 0) {
                stopped[reel] = true;
                land(reel, period);
            }
        }
    }

    /**
     * A reel came to rest: the payline symbol bounces.
     */
    private void land(int reel, int period) {
        ItemDisplay symbol = payline(reel);
        if (symbol != null) {
            later(period, () -> Props.animate(symbol, symbolPose(reel, PAYLINE, 1.25f), 2));
            later(period + 2, () -> Props.animate(symbol, symbolPose(reel, PAYLINE, 1.0f), 3));
        }
        playSound(Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 0.9f + reel * 0.25f);
        playSound(Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.7f, 0.8f);
    }

    @Override
    protected void onSettle() {
        // Whatever was still rolling snaps to the drawn result.
        for (int reel = 0; reel < 3; reel++) {
            if (stopped[reel]) {
                continue;
            }
            stopped[reel] = true;
            for (int i = 0; i < DRUM; i++) {
                ItemDisplay symbol = symbols[reel][i];
                int slot = (i + steps[reel]) % DRUM;
                if (symbol != null && symbol.isValid()) {
                    if (slot == PAYLINE) {
                        symbol.setItemStack(new ItemStack(symbolOf(reel)));
                    }
                    Props.animate(symbol, symbolPose(reel, slot, 1.0f), 2);
                }
            }
        }
        int matches = matches();
        for (int reel = 0; reel < 3; reel++) {
            ItemDisplay line = payline(reel);
            for (ItemDisplay symbol : symbols[reel]) {
                if (symbol == null || !symbol.isValid()) {
                    continue;
                }
                if (symbol == line && matches >= 2) {
                    symbol.setGlowColorOverride(Color.fromRGB(0xFFD700));
                    symbol.setGlowing(true);
                } else if (symbol != line) {
                    symbol.setBrightness(new Display.Brightness(5, 5));
                }
            }
        }
        if (matches == 3) {
            celebration = 40;
            celebrate(0, 4.0, 1.2);
            playSound(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        } else if (matches == 2) {
            celebration = 16;
            particles(Particle.HAPPY_VILLAGER, 0, 3.9, 1.0, 20, 1.6, 0.0);
            playSound(Sound.BLOCK_NOTE_BLOCK_BELL, 0.9f, 1.3f);
        } else {
            playSound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
        }
    }

    @Override
    protected void onAnimate(int age) {
        boolean party = celebration > 0;
        if (party) {
            celebration--;
            if (celebration % 6 == 0) {
                particles(Particle.FIREWORK, 0, 7.0, 0.8, 12, 2.5, 0.05);
            }
        }
        int period = party ? 2 : 3;
        if (age % period != 0) {
            return;
        }
        int phase = age / period;
        for (int i = 0; i < bulbs.size(); i++) {
            BlockDisplay bulb = bulbs.get(i);
            if (!bulb.isValid()) {
                continue;
            }
            boolean lit = party ? phase % 2 == 0 : ((i / 2) + phase) % 3 == 0;
            bulb.setBlock((lit ? Material.GLOWSTONE : Material.ORANGE_TERRACOTTA).createBlockData());
        }
    }

    @Override
    protected int extraLinger() {
        // With the replay buttons up the machine waits about ten seconds for the player.
        return replay ? 200 : matches() == 3 ? 30 : 0;
    }

    // ---------------------------------------------------------------- helpers

    private int matches() {
        Material a = symbolOf(0);
        Material b = symbolOf(1);
        Material c = symbolOf(2);
        if (a == b && b == c) {
            return 3;
        }
        return a == b || b == c ? 2 : 1;
    }

    private ItemDisplay payline(int reel) {
        for (int i = 0; i < DRUM; i++) {
            if ((i + steps[reel]) % DRUM == PAYLINE) {
                return symbols[reel][i];
            }
        }
        return null;
    }

    private Transformation symbolPose(int reel, int slot, float pop) {
        float squash = ROW_SQUASH[slot];
        float size = SYMBOL * pop;
        return Props.centred(new Vector3f(REEL_X[reel], ROW_Y[slot], SYMBOL_Z), Props.none(),
                new Vector3f(size, size * squash, size * 0.5f));
    }

    /**
     * Reel {@code index} is told to stop at that share of the spin, left to right.
     */
    private int stopFrame(int index, int duration) {
        return (int) (duration * (0.42 + 0.14 * index));
    }

    private Material symbolOf(int reel) {
        return reel < result.size() ? result.get(reel) : Material.BARRIER;
    }

    private Material random() {
        if (pool.isEmpty()) {
            return Material.GOLD_INGOT;
        }
        return pool.get(Rng.intBetween(0, pool.size() - 1));
    }
}
