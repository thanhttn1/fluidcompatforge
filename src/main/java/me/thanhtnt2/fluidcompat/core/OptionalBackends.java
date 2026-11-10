package me.thanhtnt2.fluidcompat.core;

import java.util.Optional;

import me.thanhtnt2.fluidcompat.api.DrainBackend;
import me.thanhtnt2.fluidcompat.core.backend.FlowingFluidsDrainBackend;
import net.minecraftforge.fml.ModList;

/**
 * Creates backends that depend on other mods. Keeping the mod-presence check next to the
 * instantiation means the Flowing Fluids classes are never resolved when it is absent.
 */
final class OptionalBackends {

    private static final String FLOWING_FLUIDS_MOD_ID = "flowing_fluids";

    private OptionalBackends() {
    }

    static Optional<DrainBackend> createFlowingFluidsBackend() {
        if (!ModList.get().isLoaded(FLOWING_FLUIDS_MOD_ID)) {
            return Optional.empty();
        }
        return Optional.of(new FlowingFluidsDrainBackend());
    }
}
