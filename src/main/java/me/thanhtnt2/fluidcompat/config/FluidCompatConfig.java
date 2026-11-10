package me.thanhtnt2.fluidcompat.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * COMMON config, stored in {@code config/fluidcompat-common.toml}.
 * Every gameplay value lives here so modpack authors never have to touch code.
 */
public final class FluidCompatConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue ENABLE_FINITE_FLUIDS;
    public static final ForgeConfigSpec.EnumValue<BackendPreference> BACKEND_PREFERENCE;
    public static final ForgeConfigSpec.BooleanValue FINITE_WATER;
    public static final ForgeConfigSpec.BooleanValue FINITE_LAVA;
    public static final ForgeConfigSpec.BooleanValue FINITE_TAGGED_FLUIDS;
    public static final ForgeConfigSpec.IntValue DRAIN_PERCENT;
    public static final ForgeConfigSpec.IntValue MIN_TICKS_BETWEEN_INTAKES;

    public static final ForgeConfigSpec SPEC;

    static {
        BUILDER.push("general");
        ENABLE_FINITE_FLUIDS = BUILDER
                .comment("Master switch. When false every machine behaves exactly as without Fluid Compat.")
                .define("enableFiniteFluids", true);
        BACKEND_PREFERENCE = BUILDER
                .comment("Which system removes fluid from the world. AUTO prefers Flowing Fluids when it is installed.")
                .defineEnum("backendPreference", BackendPreference.AUTO);
        BUILDER.pop();

        BUILDER.push("fluids");
        FINITE_WATER = BUILDER
                .comment("Pumps drain water from the world instead of creating it.")
                .define("finiteWater", true);
        FINITE_LAVA = BUILDER
                .comment("Pumps drain lava from the world instead of creating it.")
                .define("finiteLava", true);
        FINITE_TAGGED_FLUIDS = BUILDER
                .comment("Also treat every fluid in the tag fluidcompat:managed_fluids as finite.")
                .define("finiteTaggedFluids", true);
        BUILDER.pop();

        BUILDER.push("pumps");
        DRAIN_PERCENT = BUILDER
                .comment("How much of the world's fluid is consumed per unit pumped. 100 = one for one,",
                        "50 = pumps are twice as efficient, 200 = pumps waste half of what they drain.")
                .defineInRange("drainPercent", 100, 1, 1000);
        MIN_TICKS_BETWEEN_INTAKES = BUILDER
                .comment("Minimum ticks between two drains of the same pump. 0 = no extra limit.",
                        "Modern Industrialization pumps already work in 100 tick cycles; raise this to slow them down.")
                .defineInRange("minTicksBetweenIntakes", 0, 0, 72000);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private FluidCompatConfig() {
    }
}
