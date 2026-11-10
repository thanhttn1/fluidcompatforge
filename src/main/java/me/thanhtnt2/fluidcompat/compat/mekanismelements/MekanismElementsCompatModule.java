package me.thanhtnt2.fluidcompat.compat.mekanismelements;

import me.thanhtnt2.fluidcompat.api.compat.CompatModule;
import me.thanhtnt2.fluidcompat.compat.CompatMixinState;
import me.thanhtnt2.fluidcompat.logging.FluidCompatLog;

/** Mekanism Elements: the Seawater Pump drains the water source below it. The hook itself is a mixin. */
public final class MekanismElementsCompatModule implements CompatModule {

    public static final String ID = "mekanism_elements";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String modId() {
        return "mekanismelements";
    }

    @Override
    public String displayName() {
        return "Mekanism Elements (Seawater Pump)";
    }

    private static final String PUMP_CLASS = "fixdol.mekanismelements.common.tile.machine.TileEntitySeawaterPump";

    // The verdict is made when the pump class is first loaded; load it (without running anything) so the verdict exists
    private static void loadPumpClass() {
        try {
            Class.forName(PUMP_CLASS, false, MekanismElementsCompatModule.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError ignored) {
            // The verdict then stays "not allowed", which is the safe answer
        }
    }

    @Override
    public void initialize() {
        loadPumpClass();
        if (!CompatMixinState.hasVerdict(ID)) {
            FluidCompatLog.warn("[mekanism_elements] Seawater Pump hook never got a verdict: its mixin config was not loaded "
                    + "(check that the jar manifest lists fluidcompat.compat.mekanismelements.mixins.json)");
            return;
        }
        reportThinWater();
        if (CompatMixinState.isApplied(ID)) {
            FluidCompatLog.info("[mekanism_elements] Seawater Pump hook allowed: the pump code has the expected shape. "
                    + "The first pump cycle logs 'hook reached' if Mixin really injected it");
            return;
        }
        FluidCompatLog.warn("[mekanism_elements] Seawater Pump hook is NOT allowed (the pump code is not what it expects); "
                + "the pump keeps its original behaviour");
    }

    private static void reportThinWater() {
        String thinWaterId = "mekanism_elements_thin_water";
        if (!CompatMixinState.hasVerdict(thinWaterId)) {
            FluidCompatLog.warn("[mekanism_elements] Seawater Pump thin water hook never got a verdict: its mixin config was not loaded "
                    + "(check that the jar manifest lists fluidcompat.compat.mekanismelements.thinwater.mixins.json); "
                    + "the pump stops as soon as the water below it is no longer a full source");
            return;
        }
        if (CompatMixinState.isApplied(thinWaterId)) {
            FluidCompatLog.info("[mekanism_elements] Seawater Pump thin water hook allowed: the pump's water check has the expected "
                    + "shape. The first cycle on a thin layer logs 'kept running' if Mixin really injected it");
            reportThinWaterRefmap();
            return;
        }
        FluidCompatLog.warn("[mekanism_elements] Seawater Pump thin water hook is NOT allowed (see the "
                + "[mekanism_elements_thin_water] line of this log); the pump stops as soon as the water below it is thin");
    }

    // The two vanilla calls the hook redirects need their SRG ids in the refmap, or Mixin cannot find them in a real game
    private static void reportThinWaterRefmap() {
        String refmap = readRefmap();
        boolean mapsIsSource = refmap != null && refmap.contains("m_76170_");
        boolean mapsGetType = refmap != null && refmap.contains("m_76152_");
        FluidCompatLog.info("[mekanism_elements] refmap carries FluidState.isSource (m_76170_): {}, FluidState.getType (m_76152_): {}",
                mapsIsSource, mapsGetType);
        if (!mapsIsSource || !mapsGetType) {
            FluidCompatLog.warn("[mekanism_elements] refmap lacks a FluidState method: in a real game Mixin cannot place the "
                    + "thin water hook, and the pump stops once the water below it is no longer a full source");
        }
    }

    private static String readRefmap() {
        try (java.io.InputStream stream = MekanismElementsCompatModule.class.getResourceAsStream("/fluidcompat.refmap.json")) {
            return stream == null ? null : new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException unreadable) {
            return null;
        }
    }
}
