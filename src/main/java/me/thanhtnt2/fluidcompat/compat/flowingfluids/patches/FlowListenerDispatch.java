package me.thanhtnt2.fluidcompat.compat.flowingfluids.patches;

import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.material.Fluid;
import traben.flowing_fluids.FFFlowListenerLevel;
import traben.flowing_fluids.IFFFlowListener;

/**
 * The corrected version of Flowing Fluids' "tell the water wheels that fluid just flowed" step.
 *
 * <p>Flowing Fluids keeps, per fluid cell, the set of listener positions (the water wheels next to it). The original
 * code then asked for the block entity at the fluid cell instead of at each listener, so a wheel was never reached.
 * Here every listener position is looked up, and the wheel is told which fluid cell flowed, which is what its
 * callback checks against its own offsets.
 */
final class FlowListenerDispatch {

    private FlowListenerDispatch() {
    }

    static void notifyListeners(LevelAccessor level, BlockPos fluidPos, Direction direction, Fluid fluid, boolean downAlso) {
        if (!(level instanceof FFFlowListenerLevel listenerLevel)) {
            return;
        }
        Map<BlockPos, Set<BlockPos>> listenersByFluidPos = listenerLevel.ff$getFlowListenerPositions();
        Set<BlockPos> listenerPositions = listenersByFluidPos.get(fluidPos);
        if (listenerPositions == null || listenerPositions.isEmpty()) {
            return;
        }
        for (BlockPos listenerPos : listenerPositions) {
            // A listener in an unloaded chunk must not make the fluid tick load that chunk
            if (!level.hasChunkAt(listenerPos)) {
                continue;
            }
            if (level.getBlockEntity(listenerPos) instanceof IFFFlowListener listener) {
                listener.ff$acceptRecentFlow(fluidPos, direction, fluid, downAlso);
            }
        }
    }
}
