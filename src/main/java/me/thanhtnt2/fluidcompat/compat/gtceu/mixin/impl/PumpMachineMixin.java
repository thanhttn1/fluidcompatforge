package me.thanhtnt2.fluidcompat.compat.gtceu.mixin.impl;

import me.thanhtnt2.fluidcompat.compat.gtceu.GtPumpBridge;
import me.thanhtnt2.fluidcompat.compat.gtceu.GtPumpState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The only place Fluid Compat touches the GregTech Pump. The target is named, not linked, so Fluid Compat loads without
 * GregTech; {@code GtceuMixinPlugin} decides whether this applies. {@code pumpCycle} is GregTech's own private method and
 * is never obfuscated, so no remapping is involved. The pump's own state is a field added to the machine, so there is
 * nothing to clean up when it is removed.
 */
@Pseudo
@Mixin(targets = "com.gregtechceu.gtceu.common.machine.electric.PumpMachine", remap = false)
public abstract class PumpMachineMixin {

    @Unique
    private GtPumpState fluidcompat$sweepState;

    @Inject(method = "pumpCycle()V", at = @At("RETURN"), remap = false, require = 0)
    private void fluidcompat$sweepLeftovers(CallbackInfo callback) {
        if (fluidcompat$sweepState == null) {
            fluidcompat$sweepState = new GtPumpState();
        }
        GtPumpBridge.afterPumpCycle(this, fluidcompat$sweepState);
    }
}
