package me.thanhtnt2.fluidcompat.api.sweep;

import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;

/**
 * Asks Fluid Compat to take a bounded amount of fluid that a machine's own scan cannot see, typically the thin,
 * partially filled layers Flowing Fluids leaves behind (a vanilla-style pump only looks at full source blocks).
 *
 * <p>The request holds a {@link ServerLevel}, so never store it beyond the current tick.
 *
 * @param machineId registry id of the machine (usually its block entity type), for events and logs
 * @param maxLevels the most the machine can accept right now, in 1/8 bucket levels; the machine must check its own
 *                  free tank space first, because whatever is removed from the world has to be given to it
 * @param origin    where the machine reaches the fluid, normally the block it faces; its neighbours are tried first
 * @param cursor    optional: lets the search continue outward over several cycles, within the configured reach;
 *                  null means only the cells right around {@code origin} are looked at
 */
public record FluidSweepRequest(
        ServerLevel level,
        BlockPos machinePos,
        ResourceLocation machineId,
        Fluid fluid,
        int maxLevels,
        BlockPos origin,
        SweepCursor cursor) {

    public FluidSweepRequest {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(machinePos, "machinePos");
        Objects.requireNonNull(machineId, "machineId");
        Objects.requireNonNull(fluid, "fluid");
        Objects.requireNonNull(origin, "origin");
        if (maxLevels < 0) {
            throw new IllegalArgumentException("maxLevels must not be negative");
        }
        machinePos = machinePos.immutable();
        origin = origin.immutable();
    }
}
