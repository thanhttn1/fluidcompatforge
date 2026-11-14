package me.thanhtnt2.fluidcompat.compat.flowingfluids.pipes;

import java.util.List;
import java.util.Set;

import me.thanhtnt2.fluidcompat.api.compat.CompatModule;
import me.thanhtnt2.fluidcompat.compat.common.PipeBlockMatcher;
import me.thanhtnt2.fluidcompat.config.FluidCompatModulesConfig;
import me.thanhtnt2.fluidcompat.logging.FluidCompatLog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.MinecraftForge;

/**
 * Makes Flowing Fluids treat pipes as blocks that stay submerged instead of solid obstacles that get drained, and frees
 * the water that stays stuck inside a pipe after the pond around it is gone. A separate module from the Flowing Fluids
 * backend, which stays untouched.
 */
public final class FlowingFluidsPipesCompatModule implements CompatModule {

    public static final String ID = "flowing_fluids_pipes";

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
        return "Flowing Fluids (submerged pipes)";
    }

    @Override
    public void initialize() {
        if (!FluidCompatModulesConfig.FF_PIPES_ENABLED.get()) {
            FluidCompatLog.info("[flowing_fluids_pipes] disabled in config");
            return;
        }
        List<Block> pipes = PipeBlockMatcher.collectBlocks();
        List<TagKey<Block>> tags = PipeBlockMatcher.collectTags();
        FluidCompatLog.info("[flowing_fluids_pipes] {} pipe block(s) matched", pipes.size());
        logMatchedBlocks(pipes);
        if (FluidCompatModulesConfig.FF_PIPES_NON_DISPLACERS.get()) {
            FlowingFluidsPipeRules.registerNonDisplacers(pipes, tags);
        }
        MinecraftForge.EVENT_BUS.addListener(FlowingFluidsPipeRules::onServerStarted);
        // With no matched pipe block the janitor would walk every block entity near the players for nothing
        if (FluidCompatModulesConfig.FF_PIPES_CLEAN_ORPHAN_WATER.get() && !pipes.isEmpty()) {
            OrphanPipeWaterJanitor.register(Set.copyOf(pipes));
        }
    }

    private static void logMatchedBlocks(List<Block> pipes) {
        StringBuilder ids = new StringBuilder();
        for (Block block : pipes) {
            ids.append(BuiltInRegistries.BLOCK.getKey(block)).append(' ');
        }
        FluidCompatLog.debug("[flowing_fluids_pipes] matched blocks: {}", ids.toString().trim());
    }
}
