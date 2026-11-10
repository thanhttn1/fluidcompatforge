package me.thanhtnt2.fluidcompat.compat.flowingfluids.patches.mixin;

import java.util.List;
import java.util.Set;

import me.thanhtnt2.fluidcompat.compat.CompatMixinState;
import me.thanhtnt2.fluidcompat.compat.flowingfluids.patches.PatchSwitch;
import me.thanhtnt2.fluidcompat.compat.flowingfluids.patches.guard.FlowingFluidsShapes;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/**
 * Decides per patch whether it is applied: the player must not have switched patches off, the target class must
 * exist, and the bug must still be present in the exact shape the patch repairs.
 *
 * <p>Unlike the other compat plugins this one guards two different target classes, so it cannot use the
 * single-target base class and keeps one verdict per patch in {@link CompatMixinState}.
 */
public final class FlowingFluidsPatchesMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("FluidCompat/Mixin");

    public static final String MODULE_ID = "flowing_fluid_patches";
    public static final String LISTENER_PATCH_ID = MODULE_ID + ".flow_listeners";
    public static final String REMOVAL_PATCH_ID = MODULE_ID + ".negative_removal";
    public static final String ERASE_PATCH_ID = MODULE_ID + ".erase_only_fluid_blocks";
    public static final String FAST_CHECKS_PATCH_ID = MODULE_ID + ".fast_interaction_checks";

    private static final String FLUID_UTILS_CLASS = "traben.flowing_fluids.FFFluidUtils";

    // Read once: the answer cannot change while the game runs, and it is asked once per mixin class
    private String reasonToStayOff;
    private boolean fastChecksSwitchedOff;

    @Override
    public void onLoad(String mixinPackage) {
        reasonToStayOff = PatchSwitch.reasonToStayOff();
        fastChecksSwitchedOff = PatchSwitch.fastInteractionChecksSwitchedOff();
        if (fastChecksSwitchedOff) {
            LOGGER.info("[{}] fast interaction checks are off: flowing_fluid_patches.fastInteractionChecks is false", MODULE_ID);
        }
        if (reasonToStayOff != null) {
            LOGGER.info("[{}] patches are off: {}", MODULE_ID, reasonToStayOff);
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClass, String mixinClassName) {
        // Keyed by the mixin class, not the target: two of our patches share FFFluidUtils
        String patchId = patchIdOf(mixinClassName);
        if (patchId == null) {
            return false; // Not one of ours: never apply something this plugin does not know
        }
        boolean allowed = reasonToStayOff == null && !(FAST_CHECKS_PATCH_ID.equals(patchId) && fastChecksSwitchedOff);
        boolean apply = allowed && targetStillHasTheBug(targetClass, patchId);
        CompatMixinState.record(patchId, apply);
        return apply;
    }

    private static String patchIdOf(String mixinClassName) {
        if (mixinClassName.endsWith(".FluidUtilsFlowListenerMixin")) {
            return LISTENER_PATCH_ID;
        }
        if (mixinClassName.endsWith(".FlowingFluidsApiNegativeRemovalMixin")) {
            return REMOVAL_PATCH_ID;
        }
        if (mixinClassName.endsWith(".FluidUtilsEraseOnlyFluidBlocksMixin")) {
            return ERASE_PATCH_ID;
        }
        if (mixinClassName.endsWith(".LiquidBlockInteractionShortcutMixin")) {
            return FAST_CHECKS_PATCH_ID;
        }
        return null;
    }

    private static boolean targetStillHasTheBug(String targetClass, String patchId) {
        ClassNode target;
        try {
            target = MixinService.getService().getBytecodeProvider().getClassNode(targetClass);
        } catch (Exception exception) {
            // Catch-all on purpose: the checked exception types differ between Mixin versions,
            // and "class not found" simply means this Flowing Fluids build does not have the class
            LOGGER.info("[{}] {} not found, patch skipped", patchId, targetClass);
            return false;
        }
        boolean stillBroken;
        if (LISTENER_PATCH_ID.equals(patchId)) {
            stillBroken = FlowingFluidsShapes.flowListenerLookupIsBroken(target);
        } else if (REMOVAL_PATCH_ID.equals(patchId)) {
            stillBroken = FlowingFluidsShapes.removalAcceptsNegativeAmounts(target);
        } else if (FAST_CHECKS_PATCH_ID.equals(patchId)) {
            // A vanilla class, so it is only worth touching when Flowing Fluids is there to cause the update storm
            boolean flowingFluidsPresent = flowingFluidsIsPresent();
            boolean shapeMatches = FlowingFluidsShapes.liquidBlockOnlyChecksThenSchedules(target);
            stillBroken = flowingFluidsPresent && shapeMatches;
            if (!stillBroken) {
                // The observed shape goes to the log so a skipped patch can be explained from the log alone
                LOGGER.info("[{}] flowingFluidsPresent={} shapeMatches={} observed: {}",
                        patchId, flowingFluidsPresent, shapeMatches, FlowingFluidsShapes.describeLiquidBlock(target));
            }
        } else {
            stillBroken = FlowingFluidsShapes.removeAllFluidErasesAnyBlock(target);
        }
        if (!stillBroken) {
            LOGGER.info("[{}] {} does not have the expected shape (fixed or changed), patch skipped", patchId, targetClass);
        }
        return stillBroken;
    }

    private static boolean flowingFluidsIsPresent() {
        try {
            return MixinService.getService().getBytecodeProvider().getClassNode(FLUID_UTILS_CLASS) != null;
        } catch (Exception absent) {
            return false;
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClass, ClassNode targetClassNode, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClass, ClassNode targetClassNode, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
