package me.thanhtnt2.fluidcompat.api;

import java.util.List;
import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;

/**
 * Describes what a machine wants to take from the world during one work cycle.
 *
 * <p>The request holds a {@link ServerLevel}, so never store it beyond the current tick.
 *
 * @param machineId       registry id of the machine (usually its block entity type), for events and statistics
 * @param requestedLevels amount to take, in 1/8 bucket levels
 * @param sourcePositions positions to drain from, in the order the machine wants them consumed
 */
public record FluidIntakeRequest(
        ServerLevel level,
        BlockPos machinePos,
        ResourceLocation machineId,
        Fluid fluid,
        int requestedLevels,
        List<BlockPos> sourcePositions) {

    public FluidIntakeRequest {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(machinePos, "machinePos");
        Objects.requireNonNull(machineId, "machineId");
        Objects.requireNonNull(fluid, "fluid");
        if (requestedLevels < 0) {
            throw new IllegalArgumentException("requestedLevels must not be negative");
        }
        machinePos = machinePos.immutable();
        sourcePositions = List.copyOf(sourcePositions);
    }
}
