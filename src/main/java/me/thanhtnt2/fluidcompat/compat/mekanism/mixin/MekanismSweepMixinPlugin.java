package me.thanhtnt2.fluidcompat.compat.mekanism.mixin;

import me.thanhtnt2.fluidcompat.compat.GuardedCompatMixinPlugin;
import me.thanhtnt2.fluidcompat.compat.mekanism.guard.ElectricPumpSweepShape;

/**
 * Allows the leftover sweep only while the pump's search chain is intact. Kept apart from the water-forcing hook so
 * that a change which breaks one of them never switches the other off.
 */
public final class MekanismSweepMixinPlugin extends GuardedCompatMixinPlugin {

    public MekanismSweepMixinPlugin() {
        super("mekanism_sweep",
                "mekanism.common.tile.machine.TileEntityElectricPump",
                ElectricPumpSweepShape::sweepChainIsIntact);
    }
}
