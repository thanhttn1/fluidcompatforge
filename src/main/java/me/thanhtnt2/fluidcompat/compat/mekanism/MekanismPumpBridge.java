package me.thanhtnt2.fluidcompat.compat.mekanism;

import me.thanhtnt2.fluidcompat.api.FluidCompatApi;
import me.thanhtnt2.fluidcompat.compat.CompatMixinState;
import me.thanhtnt2.fluidcompat.compat.common.CompatGate;
import me.thanhtnt2.fluidcompat.config.FluidCompatModulesConfig;
import me.thanhtnt2.fluidcompat.logging.FluidCompatLog;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;

/**
 * Decision logic for the Mekanism Electric Pump hook. Has no reference to Mekanism classes.
 *
 * <p>Mekanism already removes source blocks with its own pickup code, which it uses for lava. For water it skips that
 * code (setting {@code pumpWaterSources}, off by default), so the pump re-reads the same block for ever. The hook asks
 * this class whether water should take the lava path; when the answer is yes, Mekanism itself removes the block and
 * the world pays for every bucket the pump gains.
 */
public final class MekanismPumpBridge {

    private static final String HOOK_ID = "mekanism.electric_pump";

    private MekanismPumpBridge() {
    }

    /**
     * @param level the pump's level, null if the block entity has none
     * @return true when the pump must treat water like any other fluid and remove the source block it sucks from
     */
    public static boolean shouldRemoveWaterSource(Level level) {
        boolean remove = decide(level);
        reportFirstUse(remove);
        return remove;
    }

    private static boolean decide(Level level) {
        if (level == null || level.isClientSide() || !FluidCompatApi.isReady()) {
            return false;
        }
        try {
            return keepsWaterFinite();
        } catch (RuntimeException | LinkageError error) {
            // Fail open: a broken hook must never stop Mekanism's pump from working
            FluidCompatLog.errorThrottled("mekanism-electric-pump", "Mekanism electric pump hook failed", error);
            return false;
        }
    }

    private static boolean keepsWaterFinite() {
        if (!CompatGate.masterEnabled() || !FluidCompatModulesConfig.ELECTRIC_PUMP_ENABLED.get()) {
            return false;
        }
        if (!FluidCompatModulesConfig.ELECTRIC_PUMP_WATER.get()) {
            return false;
        }
        return FluidCompatApi.service().isFinite(Fluids.WATER);
    }

    // The one line that proves the injection really happened: the module cannot know that at start-up
    private static void reportFirstUse(boolean remove) {
        if (!CompatMixinState.markHookReached(HOOK_ID)) {
            return;
        }
        FluidCompatLog.info("[mekanism] Electric Pump hook reached for the first time: water source removal is {}",
                remove ? "forced (water is finite)" : "left to Mekanism (water is not kept finite here)");
    }
}
