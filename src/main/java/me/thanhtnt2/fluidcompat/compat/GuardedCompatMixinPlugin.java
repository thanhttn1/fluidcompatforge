package me.thanhtnt2.fluidcompat.compat;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

/**
 * Base for the per-mod mixin plugins of the compat modules.
 *
 * <p>The mixin is applied only when the target class exists and still has the shape the hook expects.
 * Anything else disables just that hook with a warning, never the whole game.
 */
public abstract class GuardedCompatMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("FluidCompat/Mixin");

    private final String moduleId;
    private final String targetClassName;
    private final Predicate<ClassNode> shapeCheck;

    protected GuardedCompatMixinPlugin(String moduleId, String targetClassName, Predicate<ClassNode> shapeCheck) {
        this.moduleId = moduleId;
        this.targetClassName = targetClassName;
        this.shapeCheck = shapeCheck;
    }

    /** Helper for subclasses: does the class declare a method with this exact name and descriptor? */
    protected static boolean declaresMethod(ClassNode classNode, String name, String descriptor) {
        for (MethodNode method : classNode.methods) {
            if (name.equals(method.name) && descriptor.equals(method.desc)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean shouldApplyMixin(String targetClass, String mixinClassName) {
        if (!targetClassName.equals(targetClass)) {
            return true;
        }
        boolean compatible = isTargetCompatible();
        CompatMixinState.record(moduleId, compatible);
        return compatible;
    }

    private boolean isTargetCompatible() {
        ClassNode target;
        try {
            target = MixinService.getService().getBytecodeProvider().getClassNode(targetClassName);
        } catch (Exception exception) {
            // Catch-all on purpose: the checked exception types differ between Mixin versions,
            // and "class not found" simply means the other mod is not installed
            LOGGER.info("[{}] target mod not found, hook disabled", moduleId);
            return false;
        }
        if (shapeCheck.test(target)) {
            return true;
        }
        LOGGER.warn("[{}] {} changed shape, hook disabled. Update Fluid Compat.", moduleId, targetClassName);
        return false;
    }

    @Override
    public void onLoad(String mixinPackage) {
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
