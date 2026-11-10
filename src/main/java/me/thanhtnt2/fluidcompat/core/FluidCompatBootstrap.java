package me.thanhtnt2.fluidcompat.core;

import me.thanhtnt2.fluidcompat.FluidCompat;
import me.thanhtnt2.fluidcompat.api.DrainBackendRegistry;
import me.thanhtnt2.fluidcompat.api.FluidCompatApi;
import me.thanhtnt2.fluidcompat.core.sweep.FluidSweepServiceImpl;
import me.thanhtnt2.fluidcompat.core.backend.VanillaDrainBackend;

/** Wires the API to its default implementation. Runs once on the main thread after all mods are constructed. */
public final class FluidCompatBootstrap {

    private FluidCompatBootstrap() {
    }

    public static void initialize() {
        DrainBackendRegistry.register(new VanillaDrainBackend());
        OptionalBackends.createFlowingFluidsBackend().ifPresent(backend -> {
            DrainBackendRegistry.register(backend);
            FluidCompat.LOGGER.info("Flowing Fluids detected: finite fluids will be drained through its API");
        });
        FluidCompatApi.bind(new FiniteFluidServiceImpl());
        FluidCompatApi.bindSweeps(new FluidSweepServiceImpl());
    }
}
