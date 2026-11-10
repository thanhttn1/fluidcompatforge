package me.thanhtnt2.fluidcompat.compat.mekanismelements;

import me.thanhtnt2.fluidcompat.compat.CompatMixinState;
import me.thanhtnt2.fluidcompat.compat.common.CompatGate;
import me.thanhtnt2.fluidcompat.config.FluidCompatModulesConfig;
import me.thanhtnt2.fluidcompat.logging.FluidCompatLog;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * Lets the Seawater Pump see a thin Flowing Fluids layer as the water source it needs.
 *
 * <p>The pump only runs while the cell below it is a full water source, and every cycle the world pays for its
 * seawater out of that very cell. After one or two cycles the cell holds fewer than 8 levels, which Minecraft calls
 * flowing water, so the pump stopped (the 1.3.0 log shows two cycles, then nothing). The pump is not charged for what
 * it does here: the world still pays for every unit of seawater through the normal intake, so a cell that runs dry
 * simply makes the intake come back short and the shortfall setting decides what the pump produces.
 *
 * <p>Both hooks answer for the one {@link FluidState} the pump looks at. They fail open: any problem gives the answer
 * the pump would have got.
 */
public final class SeawaterThinWater {

    private static final String HOOK_ID = "mekanismelements.thin_water";

    private SeawaterThinWater() {
    }

    /** What the pump's "is it a source?" question gets. */
    public static boolean isSourceForPump(FluidState state) {
        if (state.isSource()) {
            return true;
        }
        return isThinWater(state) && markUsed();
    }

    /** What the pump's "which fluid is it?" question gets: thin water is reported as the water the pump expects. */
    public static Fluid fluidForPump(FluidState state) {
        Fluid type = state.getType();
        return type == Fluids.FLOWING_WATER && isThinWater(state) ? Fluids.WATER : type;
    }

    // A cell with at least one level of flowing water; the intake decides how much of the cycle it can really pay for
    static boolean isThinWater(FluidState state) {
        try {
            return CompatGate.masterEnabled()
                    && FluidCompatModulesConfig.SEAWATER_DRAIN_THIN_WATER.get()
                    && state.getType() == Fluids.FLOWING_WATER
                    && state.getAmount() >= 1;
        } catch (RuntimeException | LinkageError error) {
            FluidCompatLog.errorThrottled("seawater-thin-water", "Seawater pump thin water check failed", error);
            return false;
        }
    }

    private static boolean markUsed() {
        if (CompatMixinState.markHookReached(HOOK_ID)) {
            FluidCompatLog.info("[mekanism_elements] Seawater Pump kept running on a thin water layer for the first time");
        }
        return true;
    }
}
