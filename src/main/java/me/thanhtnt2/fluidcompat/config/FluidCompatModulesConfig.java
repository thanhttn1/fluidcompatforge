package me.thanhtnt2.fluidcompat.config;

import java.util.List;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Settings of the compat modules, stored in {@code config/fluidcompat-compat.toml}.
 *
 * <p>It lives in its own file on purpose: the {@link FluidCompatConfig} keys (finite water, drain percent, ...) are
 * never rewritten when a new mod gets supported. This is the 1.20.1 line: only the sections that exist so far are
 * here, and every later module adds its own new section.
 */
public final class FluidCompatModulesConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue COMPAT_ENABLED;

    public static final ForgeConfigSpec.BooleanValue ELECTRIC_PUMP_ENABLED;
    public static final ForgeConfigSpec.BooleanValue ELECTRIC_PUMP_WATER;
    public static final ForgeConfigSpec.BooleanValue ELECTRIC_PUMP_DRAIN_LEFTOVERS;

    public static final ForgeConfigSpec.BooleanValue SEAWATER_PUMP_ENABLED;
    public static final ForgeConfigSpec.IntValue SEAWATER_DRAIN_PERCENT;
    public static final ForgeConfigSpec.IntValue SEAWATER_MIN_TICKS_BETWEEN_INTAKES;
    public static final ForgeConfigSpec.EnumValue<ShortfallBehavior> SEAWATER_SHORTFALL;
    public static final ForgeConfigSpec.BooleanValue SEAWATER_DRAIN_THIN_WATER;

    public static final ForgeConfigSpec.BooleanValue ALEXSCAVES_DRAIN_ENABLED;
    public static final ForgeConfigSpec.IntValue ALEXSCAVES_DRAIN_MAX_LEVELS;
    public static final ForgeConfigSpec.IntValue ALEXSCAVES_DRAIN_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue ALEXSCAVES_DRAIN_FILL_SPREAD;
    public static final ForgeConfigSpec.IntValue ALEXSCAVES_DRAIN_INTAKE_REACH;
    public static final ForgeConfigSpec.BooleanValue ALEXSCAVES_DRAIN_WORLD_EFFECTS;

    public static final ForgeConfigSpec.BooleanValue IE_FINITE_PUMP;
    public static final ForgeConfigSpec.BooleanValue IE_NO_COBBLE_FOR_FINITE;
    public static final ForgeConfigSpec.BooleanValue IE_DRAIN_THIN_FLUIDS;

    public static final ForgeConfigSpec.BooleanValue GTCEU_PUMP_ENABLED;
    public static final ForgeConfigSpec.BooleanValue GTCEU_PUMP_DRAIN_LEFTOVERS;

    public static final ForgeConfigSpec.BooleanValue FLUIDLOGGED_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> FLUIDLOGGED_BLOCKS;
    public static final ForgeConfigSpec.BooleanValue FLUIDLOGGED_MAKE_FLUIDLOGGABLE;
    public static final ForgeConfigSpec.BooleanValue FLUIDLOGGED_MAKE_PERMEABLE;
    public static final ForgeConfigSpec.IntValue FLUIDLOGGED_FLOOD_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue FLUIDLOGGED_MAX_BLOCKS_PER_PASS;

    public static final ForgeConfigSpec.BooleanValue SWEEP_ENABLED;
    public static final ForgeConfigSpec.IntValue SWEEP_MAX_PER_TICK;
    public static final ForgeConfigSpec.IntValue SWEEP_MAX_MICROS_PER_TICK;
    public static final ForgeConfigSpec.IntValue SWEEP_EMERGENCY_MSPT;
    public static final ForgeConfigSpec.IntValue SWEEP_TRIP_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.IntValue SWEEP_MAX_TRIPS_PER_SESSION;
    public static final ForgeConfigSpec.IntValue SWEEP_IDLE_BACKOFF_MAX_CYCLES;
    public static final ForgeConfigSpec.IntValue SWEEP_REACH;
    public static final ForgeConfigSpec.IntValue SWEEP_SCAN_CELLS_PER_CALL;

    public static final ForgeConfigSpec.BooleanValue FF_PATCHES_DISABLED;
    public static final ForgeConfigSpec.BooleanValue FF_FAST_INTERACTION_CHECKS;

    public static final ForgeConfigSpec.BooleanValue LOG_FILE_ENABLED;
    public static final ForgeConfigSpec.BooleanValue LOG_VERBOSE;
    public static final ForgeConfigSpec.BooleanValue LOG_DEBUG_MODE;
    public static final ForgeConfigSpec.IntValue LOG_VERBOSE_THROTTLE_MS;

    public static final ForgeConfigSpec.BooleanValue AUTO_BALANCED_ENABLED;
    public static final ForgeConfigSpec.BooleanValue AUTO_BALANCED_FOLLOW_FLOWING_FLUIDS;
    public static final ForgeConfigSpec.IntValue AUTO_BALANCED_TARGET_MSPT;
    public static final ForgeConfigSpec.IntValue AUTO_BALANCED_MAX_SLOWDOWN;

    public static final ForgeConfigSpec SPEC;

    static {
        BUILDER.push("general");
        COMPAT_ENABLED = BUILDER
                .comment("Master switch for every compat module. Turn it off to run only the core hooks that must keep working.")
                .define("compatEnabled", true);
        BUILDER.pop();

        BUILDER.push("logging");
        LOG_FILE_ENABLED = BUILDER
                .comment("Write logs/fluidcompat/fluidcompat.log in the game folder (the previous run is kept as fluidcompat-previous.log).")
                .define("fileEnabled", true);
        LOG_VERBOSE = BUILDER
                .comment("Also log every fluid intake. Useful for bug reports, noisy otherwise.")
                .define("verbose", false);
        LOG_DEBUG_MODE = BUILDER
                .comment("DEBUG MODE: log EVERYTHING into logs/fluidcompat/fluidcompat.log: full config dump, every loaded mod,",
                        "every fluid intake (no throttling) and every block update of pipes. Files can get very large.",
                        "Pipe update tracing only starts after a game restart.")
                .define("debugMode", false);
        LOG_VERBOSE_THROTTLE_MS = BUILDER
                .comment("Minimum milliseconds between two verbose lines about the same machine.")
                .defineInRange("verboseThrottleMillis", 1000, 0, 600000);
        BUILDER.pop();

        BUILDER.push("auto_balanced");
        AUTO_BALANCED_ENABLED = BUILDER
                .comment("AUTO BALANCED COMPAT: when the server is slower than its target, Fluid Compat stretches the intervals of its own",
                        "optional periodic work (pipe cleanup, idle hopper checks) instead of adding to the lag. It never touches the",
                        "finite-water logic and never changes Flowing Fluids or Create settings.")
                .define("enabled", true);
        AUTO_BALANCED_FOLLOW_FLOWING_FLUIDS = BUILDER
                .comment("Use Flowing Fluids' own tick-time target (50 ms x its autoPerformanceMSPTargetMultiplier) while its auto performance",
                        "is not OFF, so both mods call the server 'slow' at the same moment.")
                .define("followFlowingFluidsTarget", true);
        AUTO_BALANCED_TARGET_MSPT = BUILDER
                .comment("Average milliseconds per tick above which Fluid Compat slows down its optional work (used when the option above does not apply).")
                .defineInRange("targetMillisPerTick", 45, 10, 100);
        AUTO_BALANCED_MAX_SLOWDOWN = BUILDER
                .comment("Largest factor the optional intervals are multiplied by while the server is slow. 1 = never slow down.")
                .defineInRange("maxSlowdown", 8, 1, 32);
        BUILDER.pop();
        BUILDER.push("flowing_fluid_patches");
        FF_PATCHES_DISABLED = BUILDER
                .comment("Turn OFF Fluid Compat's small repairs for bugs inside Flowing Fluids (see the log file for the list).",
                        "These are mixins, so changing this needs a GAME RESTART. general.compatEnabled = false also turns them off.")
                .define("disablePatches", false);
        FF_FAST_INTERACTION_CHECKS = BUILDER
                .comment("Skip Forge's fluid interaction check (lava meets water and similar) when a liquid block only changed its level.",
                        "This is the biggest server tick saving of the patches with Flowing Fluids: the check ran on nearly every fluid update.",
                        "Reactions between two different fluids or blocks are still checked in full. Needs a GAME RESTART.",
                        "Turn it off if you ever see a fluid reaction that no longer happens.")
                .define("fastInteractionChecks", true);
        BUILDER.pop();

        BUILDER.push("mekanism_electric_pump");
        ELECTRIC_PUMP_ENABLED = BUILDER
                .comment("Make the Mekanism Electric Pump really remove the water source it pumps, instead of duplicating it.")
                .define("enabled", true);
        ELECTRIC_PUMP_WATER = BUILDER
                .comment("Apply to water (still requires fluids.finiteWater in fluidcompat-common.toml). "
                        + "Mekanism already removes lava and other fluid sources by itself, so there is no lava key on 1.20.1.")
                .define("water", true);
        ELECTRIC_PUMP_DRAIN_LEFTOVERS = BUILDER
                .comment("When the pump finds no full source block, drain the thin water Flowing Fluids leaves behind "
                        + "(and water that flows in later) instead of stopping. Uses the sweep guard in [sweep].")
                .define("drainLeftovers", true);
        BUILDER.pop();

        BUILDER.push("mekanism_elements_seawater_pump");
        SEAWATER_PUMP_ENABLED = BUILDER
                .comment("Make the Mekanism Elements Seawater Pump drain the water source below it.")
                .define("enabled", true);
        SEAWATER_DRAIN_PERCENT = BUILDER
                .comment("World water consumed per unit of seawater made. 100 = one for one, 50 = twice as efficient.")
                .defineInRange("drainPercent", 100, 1, 1000);
        SEAWATER_MIN_TICKS_BETWEEN_INTAKES = BUILDER
                .comment("Minimum ticks between two drains of the same pump. 0 = no extra limit.")
                .defineInRange("minTicksBetweenIntakes", 0, 0, 72000);
        SEAWATER_SHORTFALL = BUILDER
                .comment("When the world cannot pay a whole cycle: SCALE_DOWN produces less, STOP produces nothing.")
                .defineEnum("shortfallBehavior", ShortfallBehavior.SCALE_DOWN);
        SEAWATER_DRAIN_THIN_WATER = BUILDER
                .comment("Let the pump keep working when the water below it is a thin Flowing Fluids layer instead of a full "
                        + "source block. The pump only runs on a source block, and every cycle takes water out of that block, "
                        + "so without this it stops after one or two cycles until the water level recovers.")
                .define("drainThinWater", true);
        BUILDER.pop();

        BUILDER.push("alexscaves_drain");
        ALEXSCAVES_DRAIN_ENABLED = BUILDER
                .comment("Make the Alex's Caves Drain move exactly the fluid it takes: it drains through Flowing Fluids "
                        + "and pours out only the levels it really removed, instead of counting every thin layer as a full block.")
                .define("enabled", true);
        ALEXSCAVES_DRAIN_MAX_LEVELS = BUILDER
                .comment("Most fluid levels (1/8 bucket each) one Drain moves per cycle. The original moves up to 64 blocks, "
                        + "which is 512 levels.")
                .defineInRange("maxLevelsPerCycle", 512, 8, 4096);
        ALEXSCAVES_DRAIN_INTERVAL_TICKS = BUILDER
                .comment("Ticks between two Drain cycles while it is working. The original uses 20. A Drain that found "
                        + "nothing to move or no room to pour waits four times as long before looking again.")
                .defineInRange("intervalTicks", 20, 5, 400);
        ALEXSCAVES_DRAIN_FILL_SPREAD = BUILDER
                .comment("How many blocks sideways and downwards from the lowest open cell below the Drain the poured fluid "
                        + "may spread when it looks for room.")
                .defineInRange("fillSpread", 10, 1, 20);
        ALEXSCAVES_DRAIN_INTAKE_REACH = BUILDER
                .comment("How many blocks away from the cell above the Drain it may take fluid from. Flowing Fluids' thin "
                        + "layers often stop short of the Drain, and the original Drain does nothing unless fluid touches "
                        + "that cell. The Drain also keeps checking while it is on instead of going to sleep. 0 = original: "
                        + "only the cell above.")
                .defineInRange("intakeReach", 3, 0, 8);
        ALEXSCAVES_DRAIN_WORLD_EFFECTS = BUILDER
                .comment("Play the Drain's own sound and particles (a network message of Alex's Caves) when it works.")
                .define("worldEffects", true);
        BUILDER.pop();

        BUILDER.push("immersive_engineering");
        IE_FINITE_PUMP = BUILDER
                .comment("Make the Immersive Engineering Fluid Pump finite. Its infinite-water rule (two source blocks next to the pump "
                        + "give 1000 mB for free) is switched off for finite fluids and the pump drains the pond with its own area search "
                        + "instead, so the world pays for every bucket. Needs a game restart.")
                .define("finitePump", true);
        IE_NO_COBBLE_FOR_FINITE = BUILDER
                .comment("Do not let the pump put cobblestone where it drained a finite fluid. IE has its own switches for this "
                        + "(server config pump_placeCobble and a per-pump toggle); with Flowing Fluids the cobblestone only hides the "
                        + "drained space, and it would also replace any thin layer still left in that block.")
                .define("noCobbleForFiniteFluids", true);
        IE_DRAIN_THIN_FLUIDS = BUILDER
                .comment("Let the pump also drain thin Flowing Fluids layers (partly filled blocks), which are not source blocks and "
                        + "which IE's pump ignores. Each thin block gives exactly the levels it holds.")
                .define("drainThinFluids", true);
        BUILDER.pop();

        BUILDER.push("gtceu_pump");
        GTCEU_PUMP_ENABLED = BUILDER
                .comment("Needs GregTech CEu Modern. Lets the electric Pump drain what its own search cannot see: thin Flowing Fluids "
                        + "layers, which are not source blocks. The world pays for every level the pump receives.")
                .define("enabled", true);
        GTCEU_PUMP_DRAIN_LEFTOVERS = BUILDER
                .comment("When the pump has no source block left to take, sweep the thin water around its pump head "
                        + "(within sweep.reach) and give the pump exactly the levels the world gave up.")
                .define("drainLeftovers", true);
        BUILDER.pop();

        BUILDER.push("fluidlogged");
        FLUIDLOGGED_ENABLED = BUILDER
                .comment("Needs the Fluidlogged mod. Floods the listed blocks with water from the Flowing Fluids water next to "
                        + "them, thin layers included, and takes exactly that water out of the world, so nothing is duplicated.")
                .define("enabled", true);
        FLUIDLOGGED_BLOCKS = BUILDER
                .comment("Blocks to flood: block ids (modid:name) or block tags (#modid:name). Applied right after you save "
                        + "this file, no restart needed. The Alex's Caves Copper Valve is listed by default so a Drain "
                        + "with a valve on top keeps getting water.")
                .defineListAllowEmpty("blocks", List.of("alexscaves:copper_valve"), entry -> entry instanceof String);
        FLUIDLOGGED_MAKE_FLUIDLOGGABLE = BUILDER
                .comment("Tell Fluidlogged that the listed blocks can hold a fluid, so blocks that cannot be waterlogged "
                        + "can be flooded too. Blocks that can be waterlogged use the normal waterlogged state either way.")
                .define("makeFluidloggable", true);
        FLUIDLOGGED_MAKE_PERMEABLE = BUILDER
                .comment("Also tell Fluidlogged that fluid may flow through the listed blocks. Only has an effect while "
                        + "Fluidlogged's own fluid permeability setting is on.")
                .define("makePermeable", false);
        FLUIDLOGGED_FLOOD_INTERVAL_TICKS = BUILDER
                .comment("Ticks between two checks of the listed blocks that are not flooded yet.")
                .defineInRange("floodIntervalTicks", 20, 5, 400);
        FLUIDLOGGED_MAX_BLOCKS_PER_PASS = BUILDER
                .comment("Most listed blocks checked in one pass, so a very common block in the list cannot cost much time.")
                .defineInRange("maxBlocksPerPass", 64, 1, 4096);
        BUILDER.pop();

        BUILDER.push("sweep");
        SWEEP_ENABLED = BUILDER
                .comment("Master switch of leftover-fluid sweeping for machines that only see full source blocks.")
                .define("enabled", true);
        SWEEP_MAX_PER_TICK = BUILDER
                .comment("Most sweeps allowed in one server tick, shared by every machine. Lowered automatically "
                        + "while Auto Balanced Compat slows work down. Extra requests wait for the machine's next cycle.")
                .defineInRange("maxSweepsPerTick", 8, 1, 64);
        SWEEP_MAX_MICROS_PER_TICK = BUILDER
                .comment("Guard: if sweeping uses more than this many microseconds per tick on average over one second, "
                        + "sweeping is switched off for the cooldown below. 1500 is about 3% of a 50 ms tick.")
                .defineInRange("maxMicrosPerTick", 1500, 100, 20000);
        SWEEP_EMERGENCY_MSPT = BUILDER
                .comment("Guard: if the average server tick time is above this many milliseconds during a second in which "
                        + "sweeps ran, sweeping is switched off for the cooldown below.")
                .defineInRange("emergencyMspt", 90, 51, 1000);
        SWEEP_TRIP_COOLDOWN_SECONDS = BUILDER
                .comment("How long sweeping stays off after the guard stopped it.")
                .defineInRange("tripCooldownSeconds", 30, 1, 3600);
        SWEEP_MAX_TRIPS_PER_SESSION = BUILDER
                .comment("After this many guard stops sweeping stays off until the server restarts.")
                .defineInRange("maxTripsPerSession", 3, 1, 100);
        SWEEP_IDLE_BACKOFF_MAX_CYCLES = BUILDER
                .comment("A machine that found nothing to sweep waits up to this many of its own cycles before looking "
                        + "again, so idle pumps cost almost nothing. 0 = look every cycle.")
                .defineInRange("idleBackoffMaxCycles", 4, 0, 40);
        SWEEP_REACH = BUILDER
                .comment("How far from a machine, in blocks, a sweep looks for leftover fluid when nothing is right next to it. "
                        + "Flowing Fluids spreads thin water that stops moving at a small height difference, so water farther "
                        + "away than this is not reached unless it flows closer. The search is spread over many cycles.")
                .defineInRange("reach", 8, 1, 32);
        SWEEP_SCAN_CELLS_PER_CALL = BUILDER
                .comment("Most positions looked at per sweep call while searching beyond the cells next to the machine. "
                        + "Each position costs two block reads. Lower is lighter on the server and slower to find far water.")
                .defineInRange("scanCellsPerCall", 24, 1, 256);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private FluidCompatModulesConfig() {
    }
}
