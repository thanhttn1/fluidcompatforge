package me.thanhtnt2.fluidcompat.logging;

import me.thanhtnt2.fluidcompat.compat.CompatMixinState;
import me.thanhtnt2.fluidcompat.compat.flowingfluids.patches.mixin.FlowingFluidsPatchesMixinPlugin;
import me.thanhtnt2.fluidcompat.config.FluidCompatModulesConfig;
import me.thanhtnt2.fluidcompat.config.FluidCompatConfig;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

/** Debug mode only: writes everything a bug report could need into the log file at startup. */
public final class DebugSnapshot {

    private DebugSnapshot() {
    }

    public static void logAll() {
        FluidCompatLog.info("=== DEBUG SNAPSHOT START ===");
        logEnvironment();
        logMods();
        logMixinState();
        logCoreConfig();
        logCompatConfig();
        logModuleState();
        FluidCompatLog.info("=== DEBUG SNAPSHOT END ===");
    }

    private static void logEnvironment() {
        FluidCompatLog.info("game dir: {}", FMLPaths.GAMEDIR.get());
        FluidCompatLog.info("config dir: {}", FMLPaths.CONFIGDIR.get());
        FluidCompatLog.info("java: {} {} ({})", System.getProperty("java.vendor"), System.getProperty("java.version"), System.getProperty("os.name"));
        FluidCompatLog.info("max memory: {} MB", Runtime.getRuntime().maxMemory() / (1024 * 1024));
    }

    private static void logMods() {
        StringBuilder mods = new StringBuilder();
        ModList.get().getMods().forEach(mod -> mods.append(mod.getModId()).append('=').append(mod.getVersion()).append(' '));
        FluidCompatLog.info("all loaded mods ({}): {}", ModList.get().getMods().size(), mods.toString().trim());
    }

    private static void logMixinState() {
        for (String patchId : new String[] {
                FlowingFluidsPatchesMixinPlugin.LISTENER_PATCH_ID, FlowingFluidsPatchesMixinPlugin.REMOVAL_PATCH_ID,
                FlowingFluidsPatchesMixinPlugin.ERASE_PATCH_ID, FlowingFluidsPatchesMixinPlugin.FAST_CHECKS_PATCH_ID}) {
            FluidCompatLog.info("mixin applied [{}] = {}", patchId, CompatMixinState.isApplied(patchId));
        }
    }

    private static void logModuleState() {
        FluidCompatLog.info("mixin allowed [mekanism] = {} [mekanism_sweep] = {} [mekanism_elements] = {} [alexscaves_drain] = {} [mekanism_elements_thin_water] = {} [fluidlogged_lists] = {} [immersiveengineering_pump] = {} [gtceu_pump] = {}",
                CompatMixinState.isApplied("mekanism"), CompatMixinState.isApplied("mekanism_sweep"),
                CompatMixinState.isApplied("mekanism_elements"), CompatMixinState.isApplied("alexscaves_drain"),
                CompatMixinState.isApplied("mekanism_elements_thin_water"), CompatMixinState.isApplied("fluidlogged_lists"),
                CompatMixinState.isApplied("immersiveengineering_pump"), CompatMixinState.isApplied("gtceu_pump"));
        FluidCompatLog.info("config[immersive_engineering] finitePump={} noCobble={} thinFluids={}; config[gtceu_pump] enabled={} leftovers={}",
                FluidCompatModulesConfig.IE_FINITE_PUMP.get(), FluidCompatModulesConfig.IE_NO_COBBLE_FOR_FINITE.get(),
                FluidCompatModulesConfig.IE_DRAIN_THIN_FLUIDS.get(), FluidCompatModulesConfig.GTCEU_PUMP_ENABLED.get(),
                FluidCompatModulesConfig.GTCEU_PUMP_DRAIN_LEFTOVERS.get());
        FluidCompatLog.info("config[fluidlogged] enabled={} blocks={} loggable={} permeable={} intervalTicks={} perPass={}",
                FluidCompatModulesConfig.FLUIDLOGGED_ENABLED.get(), FluidCompatModulesConfig.FLUIDLOGGED_BLOCKS.get(),
                FluidCompatModulesConfig.FLUIDLOGGED_MAKE_FLUIDLOGGABLE.get(), FluidCompatModulesConfig.FLUIDLOGGED_MAKE_PERMEABLE.get(),
                FluidCompatModulesConfig.FLUIDLOGGED_FLOOD_INTERVAL_TICKS.get(), FluidCompatModulesConfig.FLUIDLOGGED_MAX_BLOCKS_PER_PASS.get());
        FluidCompatLog.info("config[seawater] drainThinWater={} alexscaves intakeReach={}",
                FluidCompatModulesConfig.SEAWATER_DRAIN_THIN_WATER.get(), FluidCompatModulesConfig.ALEXSCAVES_DRAIN_INTAKE_REACH.get());
        FluidCompatLog.info("config[alexscaves_drain] enabled={} maxLevels={} intervalTicks={} fillSpread={} effects={}",
                FluidCompatModulesConfig.ALEXSCAVES_DRAIN_ENABLED.get(), FluidCompatModulesConfig.ALEXSCAVES_DRAIN_MAX_LEVELS.get(),
                FluidCompatModulesConfig.ALEXSCAVES_DRAIN_INTERVAL_TICKS.get(), FluidCompatModulesConfig.ALEXSCAVES_DRAIN_FILL_SPREAD.get(),
                FluidCompatModulesConfig.ALEXSCAVES_DRAIN_WORLD_EFFECTS.get());
        FluidCompatLog.info("config[sweep] enabled={} perTick={} maxMicros={} emergencyMspt={} cooldownS={} maxTrips={} backoff={}",
                FluidCompatModulesConfig.SWEEP_ENABLED.get(), FluidCompatModulesConfig.SWEEP_MAX_PER_TICK.get(),
                FluidCompatModulesConfig.SWEEP_MAX_MICROS_PER_TICK.get(), FluidCompatModulesConfig.SWEEP_EMERGENCY_MSPT.get(),
                FluidCompatModulesConfig.SWEEP_TRIP_COOLDOWN_SECONDS.get(), FluidCompatModulesConfig.SWEEP_MAX_TRIPS_PER_SESSION.get(),
                FluidCompatModulesConfig.SWEEP_IDLE_BACKOFF_MAX_CYCLES.get());
        FluidCompatLog.info("config[mekanism_electric_pump] enabled={} water={} drainLeftovers={}",
                FluidCompatModulesConfig.ELECTRIC_PUMP_ENABLED.get(), FluidCompatModulesConfig.ELECTRIC_PUMP_WATER.get(),
                FluidCompatModulesConfig.ELECTRIC_PUMP_DRAIN_LEFTOVERS.get());
    }

    private static void logCoreConfig() {
        FluidCompatLog.info("config[general] enableFiniteFluids={} backendPreference={}",
                FluidCompatConfig.ENABLE_FINITE_FLUIDS.get(), FluidCompatConfig.BACKEND_PREFERENCE.get());
        FluidCompatLog.info("config[fluids] finiteWater={} finiteLava={} finiteTaggedFluids={}",
                FluidCompatConfig.FINITE_WATER.get(), FluidCompatConfig.FINITE_LAVA.get(), FluidCompatConfig.FINITE_TAGGED_FLUIDS.get());
        FluidCompatLog.info("config[pumps] drainPercent={} minTicksBetweenIntakes={}",
                FluidCompatConfig.DRAIN_PERCENT.get(), FluidCompatConfig.MIN_TICKS_BETWEEN_INTAKES.get());
    }

    private static void logCompatConfig() {
        FluidCompatLog.info("config[compat] compatEnabled={} logFile={} verbose={} debugMode={} throttleMs={}",
                FluidCompatModulesConfig.COMPAT_ENABLED.get(), FluidCompatModulesConfig.LOG_FILE_ENABLED.get(),
                FluidCompatModulesConfig.LOG_VERBOSE.get(), FluidCompatModulesConfig.LOG_DEBUG_MODE.get(),
                FluidCompatModulesConfig.LOG_VERBOSE_THROTTLE_MS.get());
        FluidCompatLog.info("config[auto_balanced] enabled={} followFlowingFluids={} targetMs={} maxSlowdown={}",
                FluidCompatModulesConfig.AUTO_BALANCED_ENABLED.get(), FluidCompatModulesConfig.AUTO_BALANCED_FOLLOW_FLOWING_FLUIDS.get(),
                FluidCompatModulesConfig.AUTO_BALANCED_TARGET_MSPT.get(), FluidCompatModulesConfig.AUTO_BALANCED_MAX_SLOWDOWN.get());
        FluidCompatLog.info("config[flowing_fluid_patches] disablePatches={} fastInteractionChecks={}",
                FluidCompatModulesConfig.FF_PATCHES_DISABLED.get(), FluidCompatModulesConfig.FF_FAST_INTERACTION_CHECKS.get());
    }
}
