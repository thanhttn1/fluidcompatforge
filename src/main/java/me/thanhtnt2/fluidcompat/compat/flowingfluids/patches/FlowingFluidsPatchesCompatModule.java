package me.thanhtnt2.fluidcompat.compat.flowingfluids.patches;

import me.thanhtnt2.fluidcompat.api.compat.CompatModule;
import me.thanhtnt2.fluidcompat.compat.CompatMixinState;
import me.thanhtnt2.fluidcompat.config.FluidCompatModulesConfig;
import me.thanhtnt2.fluidcompat.logging.FluidCompatLog;

/**
 * Small repairs for bugs inside Flowing Fluids itself, applied as mixins so the player does not have to wait for a
 * new Flowing Fluids release. Every patch checks the bytecode of its target first and switches itself off when
 * Flowing Fluids was fixed or changed. This class only reports what happened to the log; the mixins live in
 */
public final class FlowingFluidsPatchesCompatModule implements CompatModule {

    public static final String ID = me.thanhtnt2.fluidcompat.compat.flowingfluids.patches.mixin.FlowingFluidsPatchesMixinPlugin.MODULE_ID;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String modId() {
        return "flowing_fluids";
    }

    @Override
    public String displayName() {
        return "Flowing Fluids (bug-fix patches)";
    }

    @Override
    public void initialize() {
        if (FluidCompatModulesConfig.FF_PATCHES_DISABLED.get()) {
            FluidCompatLog.info("[flowing_fluid_patches] disabled in config (a game restart is needed to change this)");
            return;
        }
        report("water wheel flow listeners", me.thanhtnt2.fluidcompat.compat.flowingfluids.patches.mixin.FlowingFluidsPatchesMixinPlugin.LISTENER_PATCH_ID);
        report("negative removal amount", me.thanhtnt2.fluidcompat.compat.flowingfluids.patches.mixin.FlowingFluidsPatchesMixinPlugin.REMOVAL_PATCH_ID);
        report("keep blocks that hold no fluid", me.thanhtnt2.fluidcompat.compat.flowingfluids.patches.mixin.FlowingFluidsPatchesMixinPlugin.ERASE_PATCH_ID);
        if (FluidCompatModulesConfig.FF_FAST_INTERACTION_CHECKS.get()) {
            report("fast fluid interaction checks", me.thanhtnt2.fluidcompat.compat.flowingfluids.patches.mixin.FlowingFluidsPatchesMixinPlugin.FAST_CHECKS_PATCH_ID);
        } else {
            FluidCompatLog.info("[flowing_fluid_patches] fast fluid interaction checks are switched off in the config");
        }
    }

    private static void report(String name, String patchId) {
        if (CompatMixinState.isApplied(patchId)) {
            FluidCompatLog.info("[flowing_fluid_patches] patch active: {}", name);
            return;
        }
        FluidCompatLog.info("[flowing_fluid_patches] patch not applied: {} (Flowing Fluids already fixed it, or changed that code)", name);
    }
}
