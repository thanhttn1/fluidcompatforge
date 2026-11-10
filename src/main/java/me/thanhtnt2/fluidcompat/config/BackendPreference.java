package me.thanhtnt2.fluidcompat.config;

import me.thanhtnt2.fluidcompat.api.DrainBackendIds;
import net.minecraft.resources.ResourceLocation;

/** Lets pack makers force a specific drain backend instead of the automatic priority order. */
public enum BackendPreference {
    AUTO,
    FLOWING_FLUIDS,
    VANILLA;

    public boolean accepts(ResourceLocation backendId) {
        return switch (this) {
            case AUTO -> true;
            case FLOWING_FLUIDS -> DrainBackendIds.FLOWING_FLUIDS.equals(backendId);
            case VANILLA -> DrainBackendIds.VANILLA.equals(backendId);
        };
    }
}
