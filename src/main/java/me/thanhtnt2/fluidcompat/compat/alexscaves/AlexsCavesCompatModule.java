package me.thanhtnt2.fluidcompat.compat.alexscaves;

import me.thanhtnt2.fluidcompat.api.compat.CompatModule;
import me.thanhtnt2.fluidcompat.compat.CompatMixinState;
import me.thanhtnt2.fluidcompat.logging.FluidCompatLog;

/** Alex's Caves: the Drain stops duplicating water. The hook itself is a mixin, see {@code mixin.impl}. */
public final class AlexsCavesCompatModule implements CompatModule {

    public static final String ID = "alexscaves";
    private static final String DRAIN_CLASS = "com.github.alexmodguy.alexscaves.server.block.DrainBlock";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String modId() {
        return "alexscaves";
    }

    @Override
    public String displayName() {
        return "Alex's Caves (Drain)";
    }

    // The verdict is made when the Drain class is first loaded; load it (without running anything) so the verdict exists
    private static void loadDrainClass() {
        try {
            Class.forName(DRAIN_CLASS, false, AlexsCavesCompatModule.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError ignored) {
            // The verdict then stays "none", which is reported below
        }
    }

    @Override
    public void initialize() {
        loadDrainClass();
        String verdictId = AlexsCavesDrainBridge.VERDICT_ID;
        if (!CompatMixinState.hasVerdict(verdictId)) {
            FluidCompatLog.warn("[alexscaves] Drain hook never got a verdict: its mixin config was not loaded "
                    + "(check that the jar manifest lists fluidcompat.compat.alexscaves.mixins.json)");
            return;
        }
        if (!CompatMixinState.isApplied(verdictId)) {
            FluidCompatLog.warn("[alexscaves] Drain hook is NOT allowed (the Drain code is not what it expects, see the "
                    + "[FluidCompat/Mixin] line in latest.log); the Drain keeps its original behaviour");
            return;
        }
        FluidCompatLog.info("[alexscaves] Drain hook allowed: the Drain code has the expected shape. "
                + "The first Drain cycle logs 'hook reached' if Mixin really injected it");
    }
}
