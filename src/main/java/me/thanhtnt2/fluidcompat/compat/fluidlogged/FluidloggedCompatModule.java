package me.thanhtnt2.fluidcompat.compat.fluidlogged;

import me.thanhtnt2.fluidcompat.api.compat.CompatModule;
import me.thanhtnt2.fluidcompat.compat.CompatMixinState;
import me.thanhtnt2.fluidcompat.config.FluidCompatModulesConfig;
import me.thanhtnt2.fluidcompat.logging.FluidCompatLog;
import net.minecraftforge.common.MinecraftForge;

/**
 * Fluidlogged: the listed blocks (the Alex's Caves Copper Valve by default) get flooded by Flowing Fluids water, thin
 * layers included, without making any water. The answers Fluidlogged's block lists give are a mixin, see
 * {@code mixin.impl}; the flooding itself is {@link FluidloggedFlooder}.
 */
public final class FluidloggedCompatModule implements CompatModule {

    public static final String ID = "fluidlogged";
    static final String VERDICT_ID = "fluidlogged_lists";
    private static final String CONFIG_CLASS = "de.leximon.fluidlogged.config.Config";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String modId() {
        return "fluidlogged";
    }

    @Override
    public String displayName() {
        return "Fluidlogged";
    }

    // The verdict is made when Fluidlogged's config class is first loaded; load it (without running anything)
    private static void loadConfigClass() {
        try {
            Class.forName(CONFIG_CLASS, false, FluidloggedCompatModule.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError ignored) {
            // The verdict then stays "none", which is reported below
        }
    }

    @Override
    public void initialize() {
        loadConfigClass();
        reportListsHook();
        if (!FluidCompatModulesConfig.FLUIDLOGGED_ENABLED.get()) {
            FluidCompatLog.info("[fluidlogged] flooding is switched off in the config");
            return;
        }
        MinecraftForge.EVENT_BUS.register(new FluidloggedFlooder());
        FluidCompatLog.info("[fluidlogged] flooding started: listed blocks next to Flowing Fluids water get flooded, "
                + "storing through Fluidlogged is {}", FluidloggedStore.isAvailable() ? "available" : "not available");
    }

    private static void reportListsHook() {
        if (!CompatMixinState.hasVerdict(VERDICT_ID)) {
            FluidCompatLog.warn("[fluidlogged] block list hook never got a verdict: its mixin config was not loaded "
                    + "(check that the jar manifest lists fluidcompat.compat.fluidlogged.mixins.json)");
            return;
        }
        if (CompatMixinState.isApplied(VERDICT_ID)) {
            FluidCompatLog.info("[fluidlogged] block list hook allowed: Fluidlogged's block lists have the expected shape. "
                    + "Listed blocks count as fluidloggable once the game uses them");
            return;
        }
        FluidCompatLog.warn("[fluidlogged] block list hook is NOT allowed (see the [fluidlogged_lists] line of this log); "
                + "blocks with a waterlogged state are still flooded");
    }
}
