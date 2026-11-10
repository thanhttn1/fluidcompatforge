package me.thanhtnt2.fluidcompat.api;

import net.minecraft.resources.ResourceLocation;

/** Ids of the backends shipped with Fluid Compat. */
public final class DrainBackendIds {

    public static final ResourceLocation VANILLA =
            ResourceLocation.fromNamespaceAndPath(FluidCompatApi.MOD_ID, "vanilla");

    public static final ResourceLocation FLOWING_FLUIDS =
            ResourceLocation.fromNamespaceAndPath(FluidCompatApi.MOD_ID, "flowing_fluids");

    private DrainBackendIds() {
    }
}
