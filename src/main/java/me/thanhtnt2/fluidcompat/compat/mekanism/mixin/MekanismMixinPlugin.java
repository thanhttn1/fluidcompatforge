package me.thanhtnt2.fluidcompat.compat.mekanism.mixin;

import me.thanhtnt2.fluidcompat.compat.GuardedCompatMixinPlugin;
import me.thanhtnt2.fluidcompat.compat.mekanism.guard.ElectricPumpShape;

/** Allows the Electric Pump hook only while the pump's water comparison still leads into Mekanism's pickup code. */
public final class MekanismMixinPlugin extends GuardedCompatMixinPlugin {

    public MekanismMixinPlugin() {
        super("mekanism",
                "mekanism.common.tile.machine.TileEntityElectricPump",
                ElectricPumpShape::waterComparisonLeadsIntoPickup);
    }
}
