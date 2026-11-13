package me.thanhtnt2.fluidcompat.compat.gtceu.mixin;

import me.thanhtnt2.fluidcompat.compat.GuardedCompatMixinPlugin;
import me.thanhtnt2.fluidcompat.compat.gtceu.guard.PumpMachineShape;

/** Applies the Pump sweep only while the pump still has the cycle, fields and energy charge the sweep relies on. */
public final class GtceuMixinPlugin extends GuardedCompatMixinPlugin {

    public GtceuMixinPlugin() {
        super("gtceu_pump",
                "com.gregtechceu.gtceu.common.machine.electric.PumpMachine",
                PumpMachineShape::isSweepable);
    }
}
