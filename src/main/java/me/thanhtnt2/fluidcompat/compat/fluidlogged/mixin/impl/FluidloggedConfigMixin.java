package me.thanhtnt2.fluidcompat.compat.fluidlogged.mixin.impl;

import me.thanhtnt2.fluidcompat.compat.fluidlogged.FluidloggedHooks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The only place Fluid Compat touches Fluidlogged. The target is named, not linked, so Fluid Compat loads without
 * Fluidlogged; {@code FluidloggedMixinPlugin} decides whether this applies. Fluidlogged's own methods are never
 * obfuscated, and the descriptors only name a vanilla class whose name is the same everywhere, so no remapping is
 * involved. Both targets are static, so both handlers are static. They only ever add "yes".
 */
@Pseudo
@Mixin(targets = "de.leximon.fluidlogged.config.Config", remap = false)
public class FluidloggedConfigMixin {

    @Inject(method = "isFluidloggable(Lnet/minecraft/world/level/block/state/BlockState;)Z",
            at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void fluidcompat$listedBlocksCanHoldFluid(BlockState state, CallbackInfoReturnable<Boolean> result) {
        if (!result.getReturnValueZ() && FluidloggedHooks.addsFluidloggable(state)) {
            result.setReturnValue(true);
        }
    }

    @Inject(method = "isFluidPermeable(Lnet/minecraft/world/level/block/state/BlockState;)Z",
            at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void fluidcompat$listedBlocksLetFluidThrough(BlockState state, CallbackInfoReturnable<Boolean> result) {
        if (!result.getReturnValueZ() && FluidloggedHooks.addsPermeable(state)) {
            result.setReturnValue(true);
        }
    }
}
