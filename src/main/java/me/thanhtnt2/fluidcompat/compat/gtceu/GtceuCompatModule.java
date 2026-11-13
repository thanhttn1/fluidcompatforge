package me.thanhtnt2.fluidcompat.compat.gtceu;

import me.thanhtnt2.fluidcompat.api.compat.CompatModule;
import me.thanhtnt2.fluidcompat.compat.CompatMixinState;
import me.thanhtnt2.fluidcompat.logging.FluidCompatLog;

/** GregTech CEu Modern: the electric Pump also drains thin Flowing Fluids layers once its source blocks are gone. */
public final class GtceuCompatModule implements CompatModule {

    public static final String ID = "gtceu";
    private static final String VERDICT_ID = "gtceu_pump";
    private static final String PUMP_CLASS = "com.gregtechceu.gtceu.common.machine.electric.PumpMachine";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String modId() {
        return "gtceu";
    }

    @Override
    public String displayName() {
        return "GregTech CEu Modern (Pump)";
    }

    // The verdict is made when the pump class is first loaded; load it (without running anything) so the verdict exists
    private static void loadPumpClass() {
        try {
            Class.forName(PUMP_CLASS, false, GtceuCompatModule.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError ignored) {
            // The verdict then stays "none", which is reported below
        }
    }

    @Override
    public void initialize() {
        loadPumpClass();
        if (!CompatMixinState.hasVerdict(VERDICT_ID)) {
            FluidCompatLog.warn("[gtceu] Pump hook never got a verdict: its mixin config was not loaded "
                    + "(check that the jar manifest lists fluidcompat.compat.gtceu.mixins.json)");
            return;
        }
        if (!CompatMixinState.isApplied(VERDICT_ID)) {
            FluidCompatLog.warn("[gtceu] Pump hook is NOT allowed (see the [gtceu_pump] line of this log); "
                    + "the pump keeps GregTech's own behaviour and leaves thin water behind");
            return;
        }
        FluidCompatLog.info("[gtceu] Pump hook allowed: the pump code has the expected shape. "
                + "The first leftover sweep logs 'reached' if Mixin really injected it");
    }
}
