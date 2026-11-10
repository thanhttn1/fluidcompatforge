package me.thanhtnt2.fluidcompat.compat.alexscaves.mixin;

import me.thanhtnt2.fluidcompat.compat.GuardedCompatMixinPlugin;
import me.thanhtnt2.fluidcompat.compat.alexscaves.guard.DrainLogicShape;

/** Applies the Drain hook only while the Drain's logic still has the parts the replacement models. */
public final class AlexsCavesMixinPlugin extends GuardedCompatMixinPlugin {

    public AlexsCavesMixinPlugin() {
        super("alexscaves_drain",
                "com.github.alexmodguy.alexscaves.server.block.DrainBlock",
                DrainLogicShape::isReplaceable);
    }
}
