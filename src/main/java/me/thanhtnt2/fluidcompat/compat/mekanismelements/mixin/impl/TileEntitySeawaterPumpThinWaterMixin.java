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
 * <p>Remapping: the pump's {@code onUpdateServer} is Mekanism Elements' own method and is never obfuscated. The two vanilla
 * calls are written with their SRG ids ({@code m_76170_} for {@code isSource}, {@code m_76152_} for {@code getType}), exactly
 * as they are in the bytecode of a real game, with {@code remap = false}, so nothing depends on a refmap entry.
 */
@Pseudo
@Mixin(targets = "fixdol.mekanismelements.common.tile.machine.TileEntitySeawaterPump", remap = false)
public class TileEntitySeawaterPumpThinWaterMixin {

    @Redirect(
            method = "onUpdateServer()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/material/FluidState;m_76170_()Z",
                    ordinal = 0,
                    remap = false),
            remap = false,
            require = 0)
    private boolean fluidcompat$isSourceForPump(FluidState state) {
        return SeawaterThinWater.isSourceForPump(state);
    }

    @Redirect(
            method = "onUpdateServer()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/material/FluidState;m_76152_()Lnet/minecraft/world/level/material/Fluid;",
                    ordinal = 0,
                    remap = false),
            remap = false,
            require = 0)
    private Fluid fluidcompat$fluidForPump(FluidState state) {
        return SeawaterThinWater.fluidForPump(state);
    }
}
