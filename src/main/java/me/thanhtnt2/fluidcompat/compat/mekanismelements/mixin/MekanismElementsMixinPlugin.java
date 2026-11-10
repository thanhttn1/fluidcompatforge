package me.thanhtnt2.fluidcompat.compat.mekanismelements.mixin;

import me.thanhtnt2.fluidcompat.compat.GuardedCompatMixinPlugin;
import me.thanhtnt2.fluidcompat.compat.mekanismelements.guard.SeawaterInsertShape;

/** Applies the Seawater Pump hook only while its update still inserts the seawater stack into the tank exactly once. */
public final class MekanismElementsMixinPlugin extends GuardedCompatMixinPlugin {

    public MekanismElementsMixinPlugin() {
        super("mekanism_elements",
                "fixdol.mekanismelements.common.tile.machine.TileEntitySeawaterPump",
                SeawaterInsertShape::insertsOnlyTheSeawaterStack);
    }
}
