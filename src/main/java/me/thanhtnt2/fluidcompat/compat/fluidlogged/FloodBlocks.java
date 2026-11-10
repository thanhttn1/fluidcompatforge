package me.thanhtnt2.fluidcompat.compat.fluidlogged;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.thanhtnt2.fluidcompat.config.FluidCompatModulesConfig;
import me.thanhtnt2.fluidcompat.logging.FluidCompatLog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The blocks the player listed in {@code fluidlogged.blocks}, as a fast yes/no question.
 *
 * <p>The list is compiled again whenever the config hands out a different list object, which is what happens when the
 * file is saved, so a change applies at once with no restart. Unknown ids are ignored with one log line, so listing a
 * block of a mod that is not installed is harmless.
 */
public final class FloodBlocks {

    private record Compiled(List<? extends String> source, Set<Block> blocks, List<TagKey<Block>> tags) {
        boolean isEmpty() {
            return blocks.isEmpty() && tags.isEmpty();
        }
    }

    private static volatile Compiled compiled = new Compiled(null, Set.of(), List.of());

    private FloodBlocks() {
    }

    public static boolean contains(BlockState state) {
        Compiled current = current();
        if (current.isEmpty()) {
            return false;
        }
        if (current.blocks().contains(state.getBlock())) {
            return true;
        }
        for (TagKey<Block> tag : current.tags()) {
            if (state.is(tag)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isAnyListed() {
        return !current().isEmpty();
    }

    private static Compiled current() {
        List<? extends String> configured = FluidCompatModulesConfig.FLUIDLOGGED_BLOCKS.get();
        Compiled snapshot = compiled;
        if (snapshot.source() == configured) {
            return snapshot;
        }
        Compiled fresh = compile(configured);
        compiled = fresh;
        return fresh;
    }

    private static Compiled compile(List<? extends String> entries) {
        Set<Block> blocks = new HashSet<>();
        List<TagKey<Block>> tags = new ArrayList<>();
        for (String entry : entries) {
            addEntry(entry.trim(), blocks, tags);
        }
        FluidCompatLog.info("[fluidlogged] watching {} block(s) and {} tag(s) from the config list", blocks.size(), tags.size());
        return new Compiled(entries, blocks, tags);
    }

    private static void addEntry(String entry, Set<Block> blocks, List<TagKey<Block>> tags) {
        boolean isTag = entry.startsWith("#");
        ResourceLocation id = ResourceLocation.tryParse(isTag ? entry.substring(1) : entry);
        if (id == null) {
            FluidCompatLog.warn("[fluidlogged] ignored '{}': not a valid block id or #tag", entry);
            return;
        }
        if (isTag) {
            tags.add(TagKey.create(Registries.BLOCK, id));
            return;
        }
        BuiltInRegistries.BLOCK.getOptional(id).ifPresentOrElse(blocks::add,
                () -> FluidCompatLog.info("[fluidlogged] '{}' is not a block in this game, skipped", entry));
    }
}
