package me.thanhtnt2.fluidcompat.api;

import me.thanhtnt2.fluidcompat.api.sweep.FluidSweepService;

/**
 * Entry point of the Fluid Compat addon API.
 *
 * <p>Everything inside {@code me.thanhtnt2.fluidcompat.api} is stable across minor versions.
 * Anything outside of it (core, integration, mixin) is internal and may change without notice.
 */
public final class FluidCompatApi {

    public static final String MOD_ID = "fluidcompat";

    /** Bumped only when a breaking change is made to this package. */
    public static final int API_VERSION = 1;

    /** One fluid "level" is 1/8 of a bucket, the same unit Flowing Fluids uses internally. */
    public static final int LEVELS_PER_BUCKET = 8;

    public static final int MILLIBUCKETS_PER_LEVEL = 1000 / LEVELS_PER_BUCKET;

    private static volatile FiniteFluidService service;
    private static volatile FluidSweepService sweepService;

    private FluidCompatApi() {
    }

    /**
     * Returns the active service. Safe to call from {@code FMLCommonSetupEvent#enqueueWork} onwards.
     *
     * @throws IllegalStateException if Fluid Compat has not finished its common setup yet
     */
    public static FiniteFluidService service() {
        FiniteFluidService current = service;
        if (current == null) {
            throw new IllegalStateException("Fluid Compat API was used before common setup finished");
        }
        return current;
    }

    /**
     * Returns the leftover-fluid sweep service. Same lifetime rules as {@link #service()}.
     *
     * @throws IllegalStateException if Fluid Compat has not finished its common setup yet
     */
    public static FluidSweepService sweeps() {
        FluidSweepService current = sweepService;
        if (current == null) {
            throw new IllegalStateException("Fluid Compat API was used before common setup finished");
        }
        return current;
    }

    public static boolean isReady() {
        return service != null;
    }

    /** Internal: called once by Fluid Compat itself. Addons must never call this. */
    public static void bind(FiniteFluidService newService) {
        if (service != null) {
            throw new IllegalStateException("Fluid Compat API is already bound");
        }
        service = newService;
    }

    /** Internal: called once by Fluid Compat itself. Addons must never call this. */
    public static void bindSweeps(FluidSweepService newService) {
        if (sweepService != null) {
            throw new IllegalStateException("Fluid Compat sweep API is already bound");
        }
        sweepService = newService;
    }
}
