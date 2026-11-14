package me.thanhtnt2.fluidcompat.compat.immersiveengineering.mixin;

import me.thanhtnt2.fluidcompat.compat.GuardedCompatMixinPlugin;
import me.thanhtnt2.fluidcompat.compat.immersiveengineering.guard.CobbleContract;

/** Applies the cobblestone hook only while the pump still has exactly the drain, tank and cobblestone steps it expects. */
public final class ImmersiveEngineeringCobbleMixinPlugin extends GuardedCompatMixinPlugin {

    public ImmersiveEngineeringCobbleMixinPlugin() {
        super("immersiveengineering_cobble",
                "blusunrize.immersiveengineering.common.blocks.metal.FluidPumpBlockEntity",
                CobbleContract::isHookable);
    }
}
