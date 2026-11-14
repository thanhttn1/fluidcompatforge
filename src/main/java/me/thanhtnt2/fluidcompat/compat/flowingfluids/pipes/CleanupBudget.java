package me.thanhtnt2.fluidcompat.compat.flowingfluids.pipes;

/**
 * Per-tick work limit of the orphan pipe cleanup, kept free of Minecraft types so it can be unit tested.
 *
 * <p>The cleanup used to be limited only by a number of chunks per tick, but the cost of a chunk is the number of block
 * entities in it, which is very different in an empty plain and in a dense industrial base. The budget counts both: it
 * stops taking new chunks once either the chunk count or the block entity count of the tick is used up. A chunk that
 * is already being worked on is always finished (it cannot be split), and at least one chunk is processed per tick, so
 * the queue always makes progress.
 */
final class CleanupBudget {

    private int chunksLeft;
    private int blockEntitiesLeft;
    private int chunksTaken;
    private int blockEntitiesVisited;

    CleanupBudget(int maxChunks, int maxBlockEntities, int slowdown) {
        int divisor = Math.max(1, slowdown);
        this.chunksLeft = Math.max(1, maxChunks / divisor);
        this.blockEntitiesLeft = Math.max(1, maxBlockEntities / divisor);
    }

    /** Whether another chunk may be started: the first one always may. */
    boolean mayTakeChunk() {
        return chunksTaken == 0 || (chunksLeft > 0 && blockEntitiesLeft > 0);
    }

    /** Records a chunk that was started and how many block entities it holds. */
    void tookChunk(int blockEntities) {
        chunksTaken++;
        chunksLeft--;
        blockEntitiesVisited += blockEntities;
        blockEntitiesLeft -= blockEntities;
    }

    int chunksTaken() {
        return chunksTaken;
    }

    int blockEntitiesVisited() {
        return blockEntitiesVisited;
    }
}
