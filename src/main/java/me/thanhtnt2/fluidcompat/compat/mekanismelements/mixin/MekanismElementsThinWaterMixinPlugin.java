package me.thanhtnt2.fluidcompat.compat.mekanismelements.mixin;

import me.thanhtnt2.fluidcompat.compat.GuardedCompatMixinPlugin;
import me.thanhtnt2.fluidcompat.compat.mekanismelements.guard.SeawaterWaterCheckShape;

/** Applies the thin water hook only while the pump still asks "source?" and "water?" exactly once each. */
public final class MekanismElementsThinWaterMixinPlugin extends GuardedCompatMixinPlugin {

    public MekanismElementsThinWaterMixinPlugin() {
        super("mekanism_elements_thin_water",
                "fixdol.mekanismelements.common.tile.machine.TileEntitySeawaterPump",
                SeawaterWaterCheckShape::isThinWaterHookable);
    }
}
