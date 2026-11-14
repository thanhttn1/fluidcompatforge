package me.thanhtnt2.fluidcompat.compat.immersiveengineering;

import me.thanhtnt2.fluidcompat.api.compat.CompatModule;
import me.thanhtnt2.fluidcompat.compat.CompatMixinState;
import me.thanhtnt2.fluidcompat.logging.FluidCompatLog;

/** Immersive Engineering: the Fluid Pump stops making water, drains thin layers and stops covering the pond with cobblestone. */
public final class ImmersiveEngineeringCompatModule implements CompatModule {

    public static final String ID = "immersiveengineering";
    private static final String VERDICT_ID = "immersiveengineering_pump";
    private static final String COBBLE_VERDICT_ID = "immersiveengineering_cobble";
    private static final String PUMP_CLASS = "blusunrize.immersiveengineering.common.blocks.metal.FluidPumpBlockEntity";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String modId() {
        return "immersiveengineering";
    }

    @Override
    public String displayName() {
        return "Immersive Engineering (Fluid Pump)";
    }

    // The verdict is made when the pump class is first loaded; load it (without running anything) so the verdict exists
    private static void loadPumpClass() {
        try {
            Class.forName(PUMP_CLASS, false, ImmersiveEngineeringCompatModule.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError ignored) {
            // The verdict then stays "none", which is reported below
        }
    }

    @Override
    public void initialize() {
        loadPumpClass();
        reportCobble();
        if (!CompatMixinState.hasVerdict(VERDICT_ID)) {
            FluidCompatLog.warn("[immersiveengineering] Fluid Pump hooks never got a verdict: their mixin config was not loaded "
                    + "(check that the jar manifest lists fluidcompat.compat.immersiveengineering.mixins.json)");
            return;
        }
        if (!CompatMixinState.isApplied(VERDICT_ID)) {
            FluidCompatLog.warn("[immersiveengineering] Fluid Pump hooks are NOT allowed (see the [immersiveengineering] guard line "
                    + "in latest.log); the pump keeps IE's own behaviour, including infinite water");
            return;
        }
        FluidCompatLog.info("[immersiveengineering] Fluid Pump hooks allowed: the pump code has the expected shape. "
                + "The first pump operation logs 'hook reached' if Mixin really injected them");
    }

    private static void reportCobble() {
        if (!CompatMixinState.hasVerdict(COBBLE_VERDICT_ID)) {
            FluidCompatLog.warn("[immersiveengineering] cobblestone hook never got a verdict: its mixin config was not loaded "
                    + "(check that the jar manifest lists fluidcompat.compat.immersiveengineering.cobble.mixins.json); "
                    + "the pump keeps putting cobblestone where it drained");
        } else if (CompatMixinState.isApplied(COBBLE_VERDICT_ID)) {
            FluidCompatLog.info("[immersiveengineering] cobblestone hook allowed: the pump's drain, tank and cobblestone steps are as expected. "
                    + "The first skipped cobblestone logs 'for the first time' if Mixin really injected it");
        } else {
            FluidCompatLog.warn("[immersiveengineering] cobblestone hook is NOT allowed (see the [immersiveengineering_cobble] line of this log); "
                    + "the pump keeps putting cobblestone where it drained");
        }
    }
}
