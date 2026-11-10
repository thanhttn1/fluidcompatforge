package me.thanhtnt2.fluidcompat.api;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;

/**
 * Knows how to physically remove fluid from the world.
 *
 * <p>Register implementations through {@link DrainBackendRegistry}. The backend with the highest
 * {@link #priority()} that {@linkplain #supports supports} the fluid wins, so a fluid-physics mod
 * can take over from the vanilla fallback without any mixin.
 */
public interface DrainBackend {

    ResourceLocation id();

    /** Higher wins. The built-in vanilla fallback uses 0, the Flowing Fluids backend uses 1000. */
    int priority();

    boolean supports(ServerLevel level, Fluid fluid);

    /**
     * Removes up to {@code maxLevels} fluid levels around {@code sourcePos}.
     *
     * @return the number of levels actually removed, between 0 and {@code maxLevels}
     */
    int drain(ServerLevel level, BlockPos sourcePos, Fluid fluid, int maxLevels);
}
