package me.thanhtnt2.fluidcompat.compat.mekanism.mixin.impl;

import me.thanhtnt2.fluidcompat.compat.mekanism.MekanismSweepBridge;
import me.thanhtnt2.fluidcompat.compat.mekanism.PumpSweepState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes the Electric Pump drain the thin and newly arriving water its own search ignores. The target is named, not
 * linked, and no Mekanism type appears here; {@code MekanismSweepMixinPlugin} decides whether it applies.
 *
 * <p>Everything touched is Mekanism's own, which is never obfuscated, so no remapping is involved
 * ({@code remap = false} everywhere).
 */
@Pseudo
@Mixin(targets = "mekanism.common.tile.machine.TileEntityElectricPump", remap = false)
public abstract class TileEntityElectricPumpSweepMixin {

    // Lives and dies with the pump: no map of pumps anywhere
    @Unique
    private final PumpSweepState fluidcompat$sweepState = new PumpSweepState();

    // Mekanism's own helpers, reused so that Filter Upgrade, tank checks and pump events behave exactly as before.
    // They are private in Mekanism; Mixin accepts a non-private shadow of a private method.
    @Shadow(remap = false)
    protected abstract FluidStack getOutput(Fluid fluid, boolean hasFilter);

    @Shadow(remap = false)
    protected abstract void suck(FluidStack stack, BlockPos pos, boolean addToRecurringNodes);

    // Mekanism hands "has Filter Upgrade" to every source check; keep the latest value for the sweep
    @Inject(method = "suck(Lnet/minecraft/core/BlockPos;ZZ)Z", at = @At("HEAD"), remap = false, require = 0)
    private void fluidcompat$noteFilterUpgrade(BlockPos pos, boolean hasFilter, boolean addToRecurringNodes,
            CallbackInfoReturnable<Boolean> result) {
        fluidcompat$sweepState.noteFilter(hasFilter);
    }

    // suck() answers false when the pump found no full source: that is the moment to look for thin water
    @Inject(method = "suck()Z", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void fluidcompat$drainLeftoverWater(CallbackInfoReturnable<Boolean> result) {
        if (result.getReturnValueZ()) {
            return;
        }
        BlockEntity pump = (BlockEntity) (Object) this;
        if (!MekanismSweepBridge.shouldTry(pump, fluidcompat$sweepState)) {
            return;
        }
        FluidStack bucketYield = getOutput(Fluids.WATER, fluidcompat$sweepState.filterInstalled());
        FluidStack gained = MekanismSweepBridge.drainLeftoverWater(pump, fluidcompat$sweepState, bucketYield);
        if (gained.isEmpty()) {
            return;
        }
        suck(gained, pump.getBlockPos().below(), false);
        result.setReturnValue(true);
    }
}
