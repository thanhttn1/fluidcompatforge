package me.thanhtnt2.fluidcompat.compat.common;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import me.thanhtnt2.fluidcompat.api.FluidCompatTags;
import me.thanhtnt2.fluidcompat.config.FluidCompatModulesConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Resolves the pipe-like blocks from the config list (class names, ids and tags). Matching by class name covers a whole
 * pipe family without hard-coded ids or links to the pipe mods. Create's own blocks are left to Create.
 */
public final class PipeBlockMatcher {

    private static final String CREATE_NAMESPACE = "create";
    private static final String CREATE_CLASS_PREFIX = "com.simibubi.create.";

    // Class names that compat modules add themselves, next to the player's list, which is never touched
    private static final Set<String> MODULE_CLASS_NAMES = ConcurrentHashMap.newKeySet();

    private PipeBlockMatcher() {
    }

    /** For compat modules: also treat blocks of this class (or a subclass) as pipes. Call before the pipes module starts. */
    public static void addModuleClassName(String className) {
        MODULE_CLASS_NAMES.add(className);
    }

    /** Must run after all registries are filled, which is true from common setup onwards. */
    public static List<Block> collectBlocks() {
        Set<String> classNames = new HashSet<>(FluidCompatModulesConfig.FF_PIPES_BLOCK_CLASSES.get());
        classNames.addAll(MODULE_CLASS_NAMES);
        Set<Block> matched = new LinkedHashSet<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (isInstanceOfAnyNamedClass(block.getClass(), classNames) && !isHandledByCreate(block)) {
                matched.add(block);
            }
        }
        for (String entry : FluidCompatModulesConfig.FF_PIPES_EXTRA_BLOCKS.get()) {
            addBlockEntry(entry, matched);
        }
        return List.copyOf(matched);
    }

    public static List<TagKey<Block>> collectTags() {
        List<TagKey<Block>> tags = new ArrayList<>();
        tags.add(FluidCompatTags.FLUID_PASSABLE_BLOCKS);
        for (String entry : FluidCompatModulesConfig.FF_PIPES_EXTRA_BLOCKS.get()) {
            if (!entry.startsWith("#")) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(entry.substring(1).trim());
            if (id != null) {
                tags.add(TagKey.create(Registries.BLOCK, id));
            }
        }
        return tags;
    }

    private static boolean isHandledByCreate(Block block) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
        return (key != null && CREATE_NAMESPACE.equals(key.getNamespace())) || block.getClass().getName().startsWith(CREATE_CLASS_PREFIX);
    }

    private static boolean isInstanceOfAnyNamedClass(Class<?> type, Set<String> classNames) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (classNames.contains(current.getName())) {
                return true;
            }
        }
        return false;
    }

    private static void addBlockEntry(String entry, Set<Block> target) {
        if (entry.startsWith("#")) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(entry.trim());
        if (id != null) {
            BuiltInRegistries.BLOCK.getOptional(id).filter(block -> !isHandledByCreate(block)).ifPresent(target::add);
        }
    }
}
