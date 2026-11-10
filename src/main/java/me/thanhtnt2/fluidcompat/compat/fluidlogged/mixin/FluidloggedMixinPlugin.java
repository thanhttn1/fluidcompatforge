package me.thanhtnt2.fluidcompat.compat.fluidlogged.mixin;

import me.thanhtnt2.fluidcompat.compat.GuardedCompatMixinPlugin;
import me.thanhtnt2.fluidcompat.compat.fluidlogged.guard.FluidloggedConfigShape;

/** Applies the block list hook only while Fluidlogged's Config still has its two static block questions. */
public final class FluidloggedMixinPlugin extends GuardedCompatMixinPlugin {

    public FluidloggedMixinPlugin() {
        super("fluidlogged_lists",
                "de.leximon.fluidlogged.config.Config",
                FluidloggedConfigShape::hasBlockLists);
    }
}
