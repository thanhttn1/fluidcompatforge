package me.thanhtnt2.fluidcompat.compat.immersiveengineering;

import me.thanhtnt2.fluidcompat.api.compat.CompatModule;
import me.thanhtnt2.fluidcompat.compat.CompatMixinState;
import me.thanhtnt2.fluidcompat.logging.FluidCompatLog;

/** Immersive Engineering: the Fluid Pump stops making water, drains thin layers and stops covering the pond with cobblestone. */
public final class ImmersiveEngineeringCompatModule implements CompatModule {

    public static final String ID = "immersiveengineering";
    private static final String VERDICT_ID = "immersiveengineering_pump";
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
        reportRefmap();
    }

    // getType and setBlockAndUpdate are vanilla calls: they need their SRG ids in the refmap in a real game
    private static void reportRefmap() {
        String refmap = readRefmap();
        boolean getType = refmap != null && refmap.contains("m_76152_");
        boolean setBlock = refmap != null && refmap.contains("m_46597_");
        FluidCompatLog.info("[immersiveengineering] refmap carries FluidState.getType (m_76152_): {}, Level.setBlockAndUpdate (m_46597_): {}",
                getType, setBlock);
        if (!getType || !setBlock) {
            FluidCompatLog.warn("[immersiveengineering] refmap lacks a vanilla method: in a real game Mixin cannot place the "
                    + "search and cobblestone hooks");
        }
    }

    private static String readRefmap() {
        try (java.io.InputStream stream = ImmersiveEngineeringCompatModule.class.getResourceAsStream("/fluidcompat.refmap.json")) {
            return stream == null ? null : new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException unreadable) {
            return null;
        }
    }
}
