package me.thanhtnt2.fluidcompat.compat.flowingfluids.patches.mixin.impl;

import me.thanhtnt2.fluidcompat.compat.flowingfluids.patches.PatchHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Patch: water wheels never heard about fluid flowing next to them, because {@code notifyListeners} looked for the
 * wheel at the position of the fluid cell instead of at the wheel's own position.
 *
 * <p>The original method is replaced as a whole. It is a short static loop with no side effects besides the
 * callbacks, so replacing it is simpler and safer than redirecting one of its calls (the position needed is a local
 * variable a redirect cannot see). The target is referenced by name; {@code FlowingFluidPatchesMixinPlugin} only lets
 * this mixin in while the bug is still there.
 */
// Flowing Fluids is a runtime dependency only: the target is named, not linked, so "public-target" does not apply
@SuppressWarnings("public-target")
@Mixin(targets = "traben.flowing_fluids.FFFluidUtils", remap = false)
public class FluidUtilsFlowListenerMixin {

    @Inject(
            method = "notifyListeners(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/material/Fluid;Z)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void fluidcompat$notifyTheRealListeners(
            LevelAccessor level, BlockPos fluidPos, Direction direction, Fluid fluid, boolean downAlso, CallbackInfo callback) {
        // Only take over when our version ran to the end; on any error the original code runs as before
        if (PatchHooks.notifyFlowListeners(level, fluidPos, direction, fluid, downAlso)) {
            callback.cancel();
        }
    }
}
