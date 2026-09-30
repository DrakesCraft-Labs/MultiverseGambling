package com.chagui68.multiversegambling.world.anim;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/**
 * Draws blocks in a world and puts the ground back exactly as it was.
 *
 * <p>A show is temporary scenery: the first time a block is painted its previous state
 * is remembered, and {@link #restore()} returns every one of them. That way an arena
 * is never left covered in the leftovers of an old round, even when the round is
 * won by a player leaving or the plug-in shutting down mid spin.</p>
 */
public final class BlockPaint {

    private record Position(int x, int y, int z) {
    }

    private final World world;
    private final Map<Position, BlockData> original = new LinkedHashMap<>();

    public BlockPaint(World world) {
        this.world = world;
    }

    /**
     * Replaces one block with the given material, remembering what was there.
     */
    public void set(int x, int y, int z, Material material) {
        Block block = world.getBlockAt(x, y, z);
        remember(block);
        block.setType(material, false);
    }

    /**
     * Replaces one block with a full block data, remembering what was there.
     */
    public void set(int x, int y, int z, BlockData data) {
        Block block = world.getBlockAt(x, y, z);
        remember(block);
        block.setBlockData(data, false);
    }

    private void remember(Block block) {
        original.putIfAbsent(new Position(block.getX(), block.getY(), block.getZ()),
                block.getBlockData().clone());
    }

    /**
     * How many blocks the paint touched.
     */
    public int size() {
        return original.size();
    }

    /**
     * Puts every painted block back and forgets about them.
     */
    public void restore() {
        for (Map.Entry<Position, BlockData> entry : original.entrySet()) {
            Position position = entry.getKey();
            world.getBlockAt(position.x(), position.y(), position.z())
                    .setBlockData(entry.getValue(), false);
        }
        original.clear();
    }
}
