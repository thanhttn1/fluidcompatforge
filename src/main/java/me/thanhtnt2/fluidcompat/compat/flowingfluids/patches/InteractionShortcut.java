package me.thanhtnt2.fluidcompat.compat.flowingfluids.patches;

import net.minecraft.world.level.block.Block;

/**
 * The two decisions behind the "fast interaction checks" patch, free of logging and mod calls so they can be tested
 * on their own.
 *
 * <p>Forge asks {@code FluidInteractionRegistry.canInteract} (does this fluid react with something next to it?) on
 * every neighbour update and every placement of a liquid block. Flowing Fluids changes fluid levels all the time, so
 * in a water body almost every one of those checks asks again about the same neighbours and finds nothing new.
 * A reaction can only appear when the kind of block next to a fluid changes. A level change inside the same liquid
 * block does not change what any neighbour is made of, and the reactions that did exist were already decided when
 * the neighbours were placed.
 *
 * <p>So the check is skipped only for that pure level change. The tick that keeps the fluid flowing is still scheduled
 * by the caller. Any change between two different blocks (water next to lava, air becoming water, a block replaced by
 * a liquid) runs the full original check.
 */
final class InteractionShortcut {

    private InteractionShortcut() {
    }

    /**
     * A neighbour was updated. The change is a pure level change when the block that was there before and the block
     * that is there now are both this very liquid block.
     */
    static boolean neighbourOnlyChangedItsLevel(Block thisLiquid, Block neighbourBefore, Block neighbourNow) {
        return thisLiquid == neighbourBefore && thisLiquid == neighbourNow;
    }

    /** This liquid block was placed over its own earlier state, so only its level changed. */
    static boolean placementOnlyChangedTheLevel(Block oldBlock, Block newBlock) {
        return oldBlock == newBlock;
    }
}
