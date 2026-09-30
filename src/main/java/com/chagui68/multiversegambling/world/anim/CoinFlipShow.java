package com.chagui68.multiversegambling.world.anim;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;

/**
 * A coin flipping over the arena: the coin tumbles and rises, slows down and lands on
 * the ground showing the side that really came up, which the arena then paves under it.
 *
 * <p>Heads is gold and tails is iron, the same colours the menu uses. The side is
 * decided by the provably fair generator before the toss; this show only paints it.</p>
 */
public final class CoinFlipShow extends ArenaShow {

    /**
     * Height the coin tumbles at.
     */
    private static final double COIN_HEIGHT = 3.0;
    /**
     * Degrees the coin turns on every frame.
     */
    private static final double SPIN_SPEED = 42.0;

    /**
     * Share of the show spent tossing; the rest is the coin lying on its side.
     */
    private static final double TOSS_SHARE = 0.75;

    private final boolean heads;
    private ItemDisplay coin;
    private double rotation;
    private double swing;
    private boolean paved;

    public CoinFlipShow(MultiverseGamblingPlugin plugin, ArenaStage stage, boolean heads, int ticks) {
        super(plugin, stage, ticks);
        this.heads = heads;
    }

    @Override
    protected void onStart() {
        int centerX = stage().arena().centerX();
        int centerZ = stage().arena().centerZ();
        int y = stage().floorY() + 1;
        // A small table for the coin to land on.
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx * dx + dz * dz <= 9) {
                    paint().set(centerX + dx, y, centerZ + dz, Material.POLISHED_BLACKSTONE);
                }
            }
        }
        coin = spawn(stage().at(0, COIN_HEIGHT, 0), ItemDisplay.class, display -> {
            // Gold while it is in the air: the side is only shown once it lands.
            display.setItemStack(new ItemStack(Material.GOLD_BLOCK));
            display.setBillboard(Display.Billboard.FIXED);
            display.setGravity(false);
            display.setInvulnerable(true);
        });
    }

    @Override
    protected void onFrame(int elapsed, int duration) {
        double progress = progress(elapsed);
        double toss = Math.min(1, progress / TOSS_SHARE);
        double speed = 1 - WheelMath.ease(toss);
        rotation = (rotation + SPIN_SPEED * speed) % 360;
        swing = 0.6 * Math.cos(elapsed * 0.6) * speed;
        place(1.2 + swing, rotation);
        if (elapsed % 3 == 0) {
            playSound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, (float) (1.0 + toss));
        }
        // The coin rests on the winner for the tail of the show, so the side it landed
        // on is seen before the podium is taken down.
        if (!paved && toss >= 1) {
            reveal();
        }
    }

    @Override
    protected void onSettle() {
        reveal();
    }

    private void reveal() {
        if (paved) {
            return;
        }
        paved = true;
        place(1.2, 0);
        if (coin != null && coin.isValid()) {
            coin.setItemStack(new ItemStack(heads ? Material.GOLD_BLOCK : Material.IRON_BLOCK));
        }
        disc(0, 3, heads ? Material.GOLD_BLOCK : Material.IRON_BLOCK);
        playSound(heads ? Sound.BLOCK_NOTE_BLOCK_BELL : Sound.BLOCK_ANVIL_LAND, 0.8f, 1.2f);
    }

    private void place(double height, double yaw) {
        if (coin == null || !coin.isValid()) {
            return;
        }
        Location spot = stage().at(0, height, 0);
        spot.setYaw((float) yaw);
        coin.teleport(spot);
    }
}
