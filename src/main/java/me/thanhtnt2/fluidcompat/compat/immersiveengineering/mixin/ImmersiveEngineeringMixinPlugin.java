package me.thanhtnt2.fluidcompat.compat.immersiveengineering.mixin;

import me.thanhtnt2.fluidcompat.compat.GuardedCompatMixinPlugin;
import me.thanhtnt2.fluidcompat.compat.immersiveengineering.guard.PumpContract;

/** Applies the Fluid Pump hooks only while every call they rely on exists exactly as expected (all or nothing). */
public final class ImmersiveEngineeringMixinPlugin extends GuardedCompatMixinPlugin {

    public ImmersiveEngineeringMixinPlugin() {
        super("immersiveengineering_pump",
                "blusunrize.immersiveengineering.common.blocks.metal.FluidPumpBlockEntity",
                PumpContract::isHookable);
    }
}
