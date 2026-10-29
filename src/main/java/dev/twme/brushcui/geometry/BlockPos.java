package dev.twme.brushcui.geometry;

/**
 * Immutable integer block position.
 */
public record BlockPos(int x, int y, int z) {

    public BlockPos add(BlockPos other) {
        return new BlockPos(x + other.x, y + other.y, z + other.z);
    }

    public BlockPos subtract(BlockPos other) {
        return new BlockPos(x - other.x, y - other.y, z - other.z);
    }

    public BlockPos min(BlockPos other) {
        return new BlockPos(Math.min(x, other.x), Math.min(y, other.y), Math.min(z, other.z));
    }

    public BlockPos max(BlockPos other) {
        return new BlockPos(Math.max(x, other.x), Math.max(y, other.y), Math.max(z, other.z));
    }
}
