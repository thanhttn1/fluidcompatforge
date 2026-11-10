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
 * Patch: {@code removeFluidAmountFromPos} subtracts {@code maxAmount} from the fluid in the cell, so a negative
 * amount from a careless caller turned a thin layer into a full source block, creating fluid. A negative request is
 * refused here; every valid call (zero or more) goes to the original code untouched.
 */
// Flowing Fluids is a runtime dependency only: the target is named, not linked, so "public-target" does not apply
@SuppressWarnings("public-target")
@Mixin(targets = "traben.flowing_fluids.api.FlowingFluidsApiImpl", remap = false)
public class FlowingFluidsApiNegativeRemovalMixin {

    @Inject(
            method = "removeFluidAmountFromPos(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/material/Fluid;II)I",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void fluidcompat$refuseNegativeRemoval(
            LevelAccessor level, BlockPos pos, Fluid fluid, int minAmount, int maxAmount, CallbackInfoReturnable<Integer> callback) {
        if (PatchHooks.isNegativeRemoval(pos, maxAmount)) {
            callback.setReturnValue(0);
        }
    }
}
