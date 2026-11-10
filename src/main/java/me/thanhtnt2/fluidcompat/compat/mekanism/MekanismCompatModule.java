package me.thanhtnt2.fluidcompat.compat.mekanism;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import me.thanhtnt2.fluidcompat.api.compat.CompatModule;
import me.thanhtnt2.fluidcompat.compat.CompatMixinState;
import me.thanhtnt2.fluidcompat.logging.FluidCompatLog;

/** Mekanism: the Electric Pump stops duplicating water. The hook itself is a mixin, see {@code mixin.impl}. */
public final class MekanismCompatModule implements CompatModule {

    public static final String ID = "mekanism";
    private static final String SWEEP_ID = "mekanism_sweep";
    private static final String PUMP_CLASS = "mekanism.common.tile.machine.TileEntityElectricPump";

    // The SRG id of Fluids.WATER: the refmap must carry it or the hook cannot find the field in a real game
    private static final String WATER_FIELD_SRG = "f_76193_";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String modId() {
        return "mekanism";
    }

    @Override
    public String displayName() {
        return "Mekanism (Electric Pump)";
    }

    // Both hooks are decided when Mekanism's pump class is first loaded; load it (without running anything) so the verdicts exist
    private static void loadPumpClass() {
        try {
            Class.forName(PUMP_CLASS, false, MekanismCompatModule.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError ignored) {
            // The verdict then stays "not allowed", which is the safe answer
        }
    }

    @Override
    public void initialize() {
        loadPumpClass();
        if (!CompatMixinState.isApplied(ID)) {
            FluidCompatLog.warn("[mekanism] Electric Pump hook is NOT allowed (the pump code is not what it expects); "
                    + "the pump keeps vanilla Mekanism behaviour");
            return;
        }
        FluidCompatLog.info("[mekanism] Electric Pump hook allowed: the pump code has the expected shape. "
                + "The first pump operation logs 'hook reached' if Mixin really injected it");
        reportRefmap();
        reportSweep();
    }

    private static void reportSweep() {
        if (CompatMixinState.isApplied(SWEEP_ID)) {
            FluidCompatLog.info("[mekanism] Electric Pump leftover sweep allowed: the pump search has the expected shape. "
                    + "The first sweep logs 'reached' if Mixin really injected it");
            return;
        }
        if (!CompatMixinState.hasVerdict(SWEEP_ID)) {
            // The 1.2.0 jar listed this config nowhere, so the plugin never ran and the line below blamed the pump
            FluidCompatLog.warn("[mekanism] Electric Pump leftover sweep never got a verdict: its mixin config was not loaded "
                    + "(check that the jar manifest lists fluidcompat.compat.mekanism.sweep.mixins.json); "
                    + "the pump will still ignore thin water");
            return;
        }
        FluidCompatLog.warn("[mekanism] Electric Pump leftover sweep is NOT allowed (the pump search is not what it expects, "
                + "see the [mekanism_sweep] line of this log); the pump will still ignore thin water");
    }

    private static void reportRefmap() {
        String refmap = readRefmap();
        boolean mapsWater = refmap != null && refmap.contains(WATER_FIELD_SRG);
        FluidCompatLog.info("[mekanism] refmap carries the SRG id of Fluids.WATER ({}): {}", WATER_FIELD_SRG, mapsWater);
        if (!mapsWater) {
            FluidCompatLog.warn("[mekanism] refmap lacks Fluids.WATER: in a real game Mixin cannot place the hook");
        }
    }

    private static String readRefmap() {
        try (InputStream stream = MekanismCompatModule.class.getResourceAsStream("/fluidcompat.refmap.json")) {
            return stream == null ? null : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            return null;
        }
    }
}
