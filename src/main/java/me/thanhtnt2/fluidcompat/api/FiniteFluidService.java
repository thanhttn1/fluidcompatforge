package me.thanhtnt2.fluidcompat.api;

import java.util.Optional;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;

/**
 * Mediates between a fluid-extracting machine and the world.
 *
 * <p>Machines (from any mod) describe what they want to take through a {@link FluidIntakeRequest};
 * this service decides whether the fluid is finite, drains the world accordingly and reports back
 * how much the machine is actually allowed to produce.
 */
public interface FiniteFluidService {

    /** Whether the given fluid is currently treated as a finite resource by the active configuration. */
    boolean isFinite(Fluid fluid);

    /**
     * Resolves an intake request. Always call this on the logical server.
     * If the fluid is not finite the result is {@linkplain FluidIntakeResult#managed() unmanaged}
     * and the machine should keep its original behaviour.
     */
    FluidIntakeResult requestIntake(FluidIntakeRequest request);

    /** The backend that would currently handle the given fluid, if any. Useful for diagnostics. */
    Optional<DrainBackend> activeBackendFor(ServerLevel level, Fluid fluid);
}
