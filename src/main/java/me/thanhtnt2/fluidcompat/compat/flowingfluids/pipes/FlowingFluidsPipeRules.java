package me.thanhtnt2.fluidcompat.compat.flowingfluids.pipes;

import java.util.List;

import me.thanhtnt2.fluidcompat.api.FluidCompatApi;
import me.thanhtnt2.fluidcompat.config.FluidCompatModulesConfig;
import me.thanhtnt2.fluidcompat.config.WaterlogFlowOverride;
import me.thanhtnt2.fluidcompat.logging.FluidCompatLog;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.event.server.ServerStartedEvent;
import traben.flowing_fluids.api.FlowingFluidsAPI;
import traben.flowing_fluids.config.FFConfig;

/** Everything Fluid Compat tells Flowing Fluids about pipes, through Flowing Fluids' public API only. */
final class FlowingFluidsPipeRules {

    // Flowing Fluids looks the fluid up as found in the world, which can be either variant
    private static final List<Fluid> DISPLACEMENT_FLUIDS =
            List.of(Fluids.WATER, Fluids.FLOWING_WATER, Fluids.LAVA, Fluids.FLOWING_LAVA);

    private FlowingFluidsPipeRules() {
    }

    /** Pipes placed or updated inside a fluid must not push that fluid out of their cell. */
    static void registerNonDisplacers(List<Block> blocks, List<TagKey<Block>> tags) {
        FlowingFluidsAPI api = FlowingFluidsAPI.getInstance(FluidCompatApi.MOD_ID);
        for (Fluid fluid : DISPLACEMENT_FLUIDS) {
            for (Block block : blocks) {
                api.registerBlockThatWontDisplaceFluid(fluid, block);
            }
            for (TagKey<Block> tag : tags) {
                api.registerBlockTagThatWontDisplaceFluid(fluid, tag);
            }
        }
        FluidCompatLog.info("[flowing_fluids_pipes] registered {} block(s) and {} tag(s) as non-displacers for {} fluid variants",
                blocks.size(), tags.size(), DISPLACEMENT_FLUIDS.size());
    }

    /** Applied each time a world starts, because Flowing Fluids reads its own setting from its config file only once. */
    static void onServerStarted(ServerStartedEvent event) {
        try {
            applyWaterlogFlowMode();
        } catch (RuntimeException | LinkageError error) {
            // Never let this optional tweak stop a world from starting
            FluidCompatLog.errorThrottled("pipes-waterlog-mode", "Could not apply the Flowing Fluids waterlog flow mode", error);
        }
    }

    // Modes that let pipes join the fluid simulation duplicated water in testing, so they need an explicit opt-in
    private static WaterlogFlowOverride safeRequestedMode() {
        WaterlogFlowOverride configured = FluidCompatModulesConfig.FF_PIPES_WATERLOG_MODE.get();
        boolean safe = configured == WaterlogFlowOverride.IGNORE
                || configured == WaterlogFlowOverride.KEEP_FLOWING_FLUIDS_SETTING;
        if (safe || FluidCompatModulesConfig.FF_PIPES_ALLOW_EXPERIMENTAL_MODES.get()) {
            return configured;
        }
        FluidCompatLog.warn("[flowing_fluids_pipes] waterlogFlowMode={} is experimental and can duplicate water; using IGNORE instead. "
                + "Set allowExperimentalWaterlogModes=true to force it.", configured);
        return WaterlogFlowOverride.IGNORE;
    }

    private static void applyWaterlogFlowMode() {
        WaterlogFlowOverride requested = safeRequestedMode();
        FFConfig flowingFluidsConfig = FlowingFluidsAPI.getInstance(FluidCompatApi.MOD_ID).getConfig();
        FFConfig.WaterLogFlowMode current = flowingFluidsConfig.waterLogFlowMode;
        if (requested == WaterlogFlowOverride.KEEP_FLOWING_FLUIDS_SETTING) {
            FluidCompatLog.info("[flowing_fluids_pipes] leaving Flowing Fluids waterlog flow mode at {}", current);
            return;
        }
        FFConfig.WaterLogFlowMode target = FFConfig.WaterLogFlowMode.valueOf(requested.name());
        if (current == target) {
            FluidCompatLog.info("[flowing_fluids_pipes] Flowing Fluids waterlog flow mode is already {}", target);
            return;
        }
        // In memory only: setConfig is deliberately not called, so the player's own config file stays theirs
        flowingFluidsConfig.waterLogFlowMode = target;
        FluidCompatLog.info("[flowing_fluids_pipes] Flowing Fluids waterlog flow mode changed {} -> {} (in memory only)", current, target);
    }
}
