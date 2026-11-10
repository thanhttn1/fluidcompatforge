package me.thanhtnt2.fluidcompat.compat.mekanismelements.mixin.impl;

import me.thanhtnt2.fluidcompat.compat.mekanismelements.SeawaterThinWater;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Makes the Seawater Pump accept thin Flowing Fluids water below it. Its own mixin and config on purpose: this hook
 * and the one that charges the world for the seawater apply or fail independently.
 *
 * <p>Remapping: the pump's {@code onUpdateServer} is Mekanism Elements' own method and is never obfuscated
 * ({@code remap = false} on the redirects), but {@code FluidState.isSource} and {@code FluidState.getType} are vanilla
 * methods with SRG names in a real game. An {@code @At} inherits the remap flag of its injector, so each {@code @At}
 * says {@code remap = true} itself; only then is its entry written to the refmap. Both methods are declared in
 * {@code FluidState} itself, which is the owner the refmap lookup needs.
 */
@Pseudo
@Mixin(targets = "fixdol.mekanismelements.common.tile.machine.TileEntitySeawaterPump")
public class TileEntitySeawaterPumpThinWaterMixin {

    @Redirect(
            method = "onUpdateServer()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/material/FluidState;isSource()Z",
                    ordinal = 0,
                    remap = true),
            remap = false,
            require = 0)
    private boolean fluidcompat$isSourceForPump(FluidState state) {
        return SeawaterThinWater.isSourceForPump(state);
    }

    @Redirect(
            method = "onUpdateServer()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/material/FluidState;getType()Lnet/minecraft/world/level/material/Fluid;",
                    ordinal = 0,
                    remap = true),
            remap = false,
            require = 0)
    private Fluid fluidcompat$fluidForPump(FluidState state) {
        return SeawaterThinWater.fluidForPump(state);
    }
}
