package me.thanhtnt2.fluidcompat.compat.flowingfluids.patches.mixin.impl;

import me.thanhtnt2.fluidcompat.compat.flowingfluids.patches.PatchHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.material.Fluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hardening for the "air artifacts" reports (Flowing Fluids issue 184): {@code removeAllFluidAtPos} replaces whatever
 * block is in the cell with air, without checking that the cell still holds fluid. If the fluid code acted on stale
 * information, a solid block silently disappears and leaves a hole. This keeps such a block and logs where it happened.
 *
 * <p>This is a safety net and a source of evidence, not a proven fix of that issue: it only changes behaviour in the
 * case that should never happen, and every normal call (air, liquid, waterlogged, kelp) runs the original code.
 */
// Flowing Fluids is a runtime dependency only: the target is named, not linked, so "public-target" does not apply
@SuppressWarnings("public-target")
@Mixin(targets = "traben.flowing_fluids.FFFluidUtils", remap = false)
public class FluidUtilsEraseOnlyFluidBlocksMixin {

    @Inject(
            method = "removeAllFluidAtPos(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/material/Fluid;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void fluidcompat$keepBlocksThatHoldNoFluid(
            LevelAccessor level, BlockPos pos, Fluid fluid, CallbackInfoReturnable<Boolean> callback) {
        if (PatchHooks.refuseToEraseBlockWithoutFluid(level, pos, fluid)) {
            callback.setReturnValue(false);
        }
    }
}
