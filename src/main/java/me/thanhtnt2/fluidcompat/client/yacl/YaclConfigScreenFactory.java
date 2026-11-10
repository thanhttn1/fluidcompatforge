package me.thanhtnt2.fluidcompat.client.yacl;

import java.util.Locale;

import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionFlag;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import me.thanhtnt2.fluidcompat.api.compat.CompatModule;
import me.thanhtnt2.fluidcompat.api.compat.CompatModuleRegistry;
import me.thanhtnt2.fluidcompat.api.compat.CompatStatus;
import me.thanhtnt2.fluidcompat.config.BackendPreference;
import me.thanhtnt2.fluidcompat.config.FluidCompatConfig;
import me.thanhtnt2.fluidcompat.config.FluidCompatModulesConfig;
import me.thanhtnt2.fluidcompat.config.ShortfallBehavior;
import me.thanhtnt2.fluidcompat.logging.FluidCompatLog;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModList;

/**
 * Config screen built with YACL. Client only, and only ever loaded when YACL is installed.
 *
 * <p>Every option is bound straight to the existing {@link ForgeConfigSpec} values, so the screen and the toml
 * files can never drift apart. The core keys are only read and written here, never redefined.
 */
public final class YaclConfigScreenFactory {

    private static final String KEY_PREFIX = "fluidcompat.option.";

    private YaclConfigScreenFactory() {
    }

    public static Screen create(Screen parent) {
        return YetAnotherConfigLib.createBuilder()
                .title(Component.translatable("fluidcompat.configuration.title"))
                .category(buildCoreCategory())
                .category(buildModCompatListCategory())
                .category(buildLoggingCategory())
                .save(YaclConfigScreenFactory::saveAll)
                .build()
                .generateScreen(parent);
    }

    // ---------------------------------------------------------------- categories

    private static ConfigCategory buildCoreCategory() {
        OptionGroup general = OptionGroup.createBuilder()
                .name(Component.translatable("fluidcompat.group.core"))
                .option(LabelOption.create(Component.translatable("fluidcompat.notice.unofficial").withStyle(ChatFormatting.GRAY)))
                .option(toggle("enableFiniteFluids", FluidCompatConfig.ENABLE_FINITE_FLUIDS, true))
                .option(choice("backendPreference", FluidCompatConfig.BACKEND_PREFERENCE, BackendPreference.class))
                .option(toggle("finiteWater", FluidCompatConfig.FINITE_WATER, true))
                .option(toggle("finiteLava", FluidCompatConfig.FINITE_LAVA, true))
                .option(toggle("finiteTaggedFluids", FluidCompatConfig.FINITE_TAGGED_FLUIDS, true))
                .build();
        return ConfigCategory.createBuilder()
                .name(Component.translatable("fluidcompat.tab.core"))
                .group(general)
                .build();
    }

    private static ConfigCategory buildModCompatListCategory() {
        return ConfigCategory.createBuilder()
                .name(Component.translatable("fluidcompat.tab.mod_compat_list"))
                .tooltip(Component.translatable("fluidcompat.tab.mod_compat_list.tooltip"))
                .group(buildOverviewGroup())
                .group(buildMasterGroup())
                .group(buildMekanismGroup())
                .group(buildMekanismElementsGroup())
                .group(buildAlexsCavesGroup())
                .group(buildFluidloggedGroup())
                .group(buildSweepGroup())
                .group(buildAutoBalancedGroup())
                .group(buildFlowingFluidPatchesGroup())
                .build();
    }

    private static ConfigCategory buildLoggingCategory() {
        OptionGroup.Builder group = OptionGroup.createBuilder()
                .name(Component.translatable("fluidcompat.group.logging"))
                .option(toggle("logFileEnabled", FluidCompatModulesConfig.LOG_FILE_ENABLED, true))
                .option(toggle("logDebug", FluidCompatModulesConfig.LOG_DEBUG_MODE, true))
                .option(toggle("logVerbose", FluidCompatModulesConfig.LOG_VERBOSE, true))
                .option(slider("logThrottle", FluidCompatModulesConfig.LOG_VERBOSE_THROTTLE_MS, 0, 10000, 100, true));
        if (FluidCompatLog.logFilePath() != null) {
            group.option(LabelOption.create(Component.literal(FluidCompatLog.logFilePath().toString()).withStyle(ChatFormatting.GRAY)));
        }
        return ConfigCategory.createBuilder()
                .name(Component.translatable("fluidcompat.tab.logging"))
                .group(group.build())
                .build();
    }

    // ---------------------------------------------------------------- groups of the "Mod Compat List"

    private static OptionGroup buildOverviewGroup() {
        OptionGroup.Builder group = OptionGroup.createBuilder()
                .name(Component.translatable("fluidcompat.group.overview"))
                .description(OptionDescription.of(Component.translatable("fluidcompat.group.overview.desc")));
        for (CompatModule module : CompatModuleRegistry.snapshot()) {
            group.option(LabelOption.create(statusLine(module)));
        }
        return group.build();
    }

    private static OptionGroup buildMasterGroup() {
        return OptionGroup.createBuilder()
                .name(Component.translatable("fluidcompat.group.general"))
                .option(toggle("compatEnabled", FluidCompatModulesConfig.COMPAT_ENABLED, true))
                .build();
    }

    private static OptionGroup buildMekanismGroup() {
        boolean installed = isLoaded("mekanism");
        return OptionGroup.createBuilder()
                .name(Component.translatable("fluidcompat.group.mekanism"))
                .description(OptionDescription.of(Component.translatable("fluidcompat.group.mekanism.desc")))
                .option(toggle("electricPumpEnabled", FluidCompatModulesConfig.ELECTRIC_PUMP_ENABLED, installed))
                .option(toggle("electricPumpWater", FluidCompatModulesConfig.ELECTRIC_PUMP_WATER, installed))
                .option(toggle("electricPumpDrainLeftovers", FluidCompatModulesConfig.ELECTRIC_PUMP_DRAIN_LEFTOVERS, installed))
                .build();
    }

    private static OptionGroup buildMekanismElementsGroup() {
        boolean installed = isLoaded("mekanismelements");
        return OptionGroup.createBuilder()
                .name(Component.translatable("fluidcompat.group.mekanism_elements"))
                .description(OptionDescription.of(Component.translatable("fluidcompat.group.mekanism_elements.desc")))
                .option(toggle("seawaterEnabled", FluidCompatModulesConfig.SEAWATER_PUMP_ENABLED, installed))
                .option(slider("seawaterDrainPercent", FluidCompatModulesConfig.SEAWATER_DRAIN_PERCENT, 1, 1000, 1, installed))
                .option(slider("seawaterMinTicks", FluidCompatModulesConfig.SEAWATER_MIN_TICKS_BETWEEN_INTAKES, 0, 72000, 20, installed))
                .option(choice("seawaterShortfall", FluidCompatModulesConfig.SEAWATER_SHORTFALL, ShortfallBehavior.class))
                .option(toggle("seawaterThinWater", FluidCompatModulesConfig.SEAWATER_DRAIN_THIN_WATER, installed))
                .build();
    }

    private static OptionGroup buildAlexsCavesGroup() {
        boolean installed = isLoaded("alexscaves");
        return OptionGroup.createBuilder()
                .name(Component.translatable("fluidcompat.group.alexscaves"))
                .description(OptionDescription.of(Component.translatable("fluidcompat.group.alexscaves.desc")))
                .option(toggle("alexsCavesDrainEnabled", FluidCompatModulesConfig.ALEXSCAVES_DRAIN_ENABLED, installed))
                .option(slider("alexsCavesDrainMaxLevels", FluidCompatModulesConfig.ALEXSCAVES_DRAIN_MAX_LEVELS, 8, 4096, 8, installed))
                .option(slider("alexsCavesDrainInterval", FluidCompatModulesConfig.ALEXSCAVES_DRAIN_INTERVAL_TICKS, 5, 400, 5, installed))
                .option(slider("alexsCavesDrainFillSpread", FluidCompatModulesConfig.ALEXSCAVES_DRAIN_FILL_SPREAD, 1, 20, 1, installed))
                .option(slider("alexsCavesDrainReach", FluidCompatModulesConfig.ALEXSCAVES_DRAIN_INTAKE_REACH, 0, 8, 1, installed))
                .option(toggle("alexsCavesDrainEffects", FluidCompatModulesConfig.ALEXSCAVES_DRAIN_WORLD_EFFECTS, installed))
                .build();
    }

    private static OptionGroup buildFluidloggedGroup() {
        boolean installed = isLoaded("fluidlogged");
        return OptionGroup.createBuilder()
                .name(Component.translatable("fluidcompat.group.fluidlogged"))
                .description(OptionDescription.of(Component.translatable("fluidcompat.group.fluidlogged.desc")))
                .option(toggle("fluidloggedEnabled", FluidCompatModulesConfig.FLUIDLOGGED_ENABLED, installed))
                .option(toggle("fluidloggedLoggable", FluidCompatModulesConfig.FLUIDLOGGED_MAKE_FLUIDLOGGABLE, installed))
                .option(toggle("fluidloggedPermeable", FluidCompatModulesConfig.FLUIDLOGGED_MAKE_PERMEABLE, installed))
                .option(slider("fluidloggedInterval", FluidCompatModulesConfig.FLUIDLOGGED_FLOOD_INTERVAL_TICKS, 5, 400, 5, installed))
                .option(slider("fluidloggedPerPass", FluidCompatModulesConfig.FLUIDLOGGED_MAX_BLOCKS_PER_PASS, 1, 4096, 1, installed))
                .build();
    }

    private static OptionGroup buildSweepGroup() {
        return OptionGroup.createBuilder()
                .name(Component.translatable("fluidcompat.group.sweep"))
                .description(OptionDescription.of(Component.translatable("fluidcompat.group.sweep.desc")))
                .option(toggle("sweepEnabled", FluidCompatModulesConfig.SWEEP_ENABLED, true))
                .option(slider("sweepMaxPerTick", FluidCompatModulesConfig.SWEEP_MAX_PER_TICK, 1, 64, 1, true))
                .option(slider("sweepMaxMicros", FluidCompatModulesConfig.SWEEP_MAX_MICROS_PER_TICK, 100, 20000, 100, true))
                .option(slider("sweepEmergencyMspt", FluidCompatModulesConfig.SWEEP_EMERGENCY_MSPT, 51, 1000, 1, true))
                .option(slider("sweepTripCooldown", FluidCompatModulesConfig.SWEEP_TRIP_COOLDOWN_SECONDS, 1, 3600, 1, true))
                .option(slider("sweepMaxTrips", FluidCompatModulesConfig.SWEEP_MAX_TRIPS_PER_SESSION, 1, 100, 1, true))
                .option(slider("sweepIdleBackoff", FluidCompatModulesConfig.SWEEP_IDLE_BACKOFF_MAX_CYCLES, 0, 40, 1, true))
                .option(slider("sweepReach", FluidCompatModulesConfig.SWEEP_REACH, 1, 32, 1, true))
                .option(slider("sweepScanCells", FluidCompatModulesConfig.SWEEP_SCAN_CELLS_PER_CALL, 1, 256, 1, true))
                .build();
    }

    private static OptionGroup buildAutoBalancedGroup() {
        return OptionGroup.createBuilder()
                .name(Component.translatable("fluidcompat.group.auto_balanced"))
                .description(OptionDescription.of(Component.translatable("fluidcompat.group.auto_balanced.desc")))
                .option(toggle("autoBalancedEnabled", FluidCompatModulesConfig.AUTO_BALANCED_ENABLED, true))
                .option(toggle("autoBalancedFollowFf", FluidCompatModulesConfig.AUTO_BALANCED_FOLLOW_FLOWING_FLUIDS, true))
                .option(slider("autoBalancedTarget", FluidCompatModulesConfig.AUTO_BALANCED_TARGET_MSPT, 10, 100, 1, true))
                .option(slider("autoBalancedMaxSlowdown", FluidCompatModulesConfig.AUTO_BALANCED_MAX_SLOWDOWN, 1, 32, 1, true))
                .build();
    }

    private static OptionGroup buildFlowingFluidPatchesGroup() {
        boolean installed = isLoaded("flowing_fluids");
        return OptionGroup.createBuilder()
                .name(Component.translatable("fluidcompat.group.flowing_fluid_patches"))
                .description(OptionDescription.of(Component.translatable("fluidcompat.group.flowing_fluid_patches.desc")))
                .option(restartToggle("ffPatchesDisabled", FluidCompatModulesConfig.FF_PATCHES_DISABLED, installed))
                .option(restartToggle("ffFastInteractionChecks", FluidCompatModulesConfig.FF_FAST_INTERACTION_CHECKS, installed))
                .build();
    }

    // ---------------------------------------------------------------- option builders

    private static Option<Boolean> toggle(String key, ForgeConfigSpec.BooleanValue value, boolean available) {
        return Option.<Boolean>createBuilder()
                .name(Component.translatable(KEY_PREFIX + key))
                .description(OptionDescription.of(Component.translatable(KEY_PREFIX + key + ".desc")))
                .binding(value.getDefault(), value::get, value::set)
                .controller(TickBoxControllerBuilder::create)
                .available(available)
                .build();
    }

    // Mixins are applied while the game starts, so these options have to ask for a restart
    private static Option<Boolean> restartToggle(String key, ForgeConfigSpec.BooleanValue value, boolean available) {
        return Option.<Boolean>createBuilder()
                .name(Component.translatable(KEY_PREFIX + key))
                .description(OptionDescription.of(Component.translatable(KEY_PREFIX + key + ".desc")))
                .binding(value.getDefault(), value::get, value::set)
                .controller(TickBoxControllerBuilder::create)
                .flag(OptionFlag.GAME_RESTART)
                .available(available)
                .build();
    }

    private static Option<Integer> slider(String key, ForgeConfigSpec.IntValue value, int min, int max, int step, boolean available) {
        return Option.<Integer>createBuilder()
                .name(Component.translatable(KEY_PREFIX + key))
                .description(OptionDescription.of(Component.translatable(KEY_PREFIX + key + ".desc")))
                .binding(value.getDefault(), value::get, value::set)
                .controller(option -> IntegerSliderControllerBuilder.create(option).range(min, max).step(step))
                .available(available)
                .build();
    }

    private static <E extends Enum<E>> Option<E> choice(String key, ForgeConfigSpec.EnumValue<E> value, Class<E> type) {
        return Option.<E>createBuilder()
                .name(Component.translatable(KEY_PREFIX + key))
                .description(OptionDescription.of(Component.translatable(KEY_PREFIX + key + ".desc")))
                .binding(value.getDefault(), value::get, value::set)
                .controller(option -> EnumControllerBuilder.create(option).enumClass(type))
                .build();
    }

    // ---------------------------------------------------------------- helpers

    private static Component statusLine(CompatModule module) {
        CompatStatus status = CompatModuleRegistry.statusOf(module.id());
        ChatFormatting color = switch (status) {
            case ACTIVE -> ChatFormatting.GREEN;
            case FAILED -> ChatFormatting.RED;
            case DISABLED, PENDING -> ChatFormatting.YELLOW;
            case MISSING_MOD -> ChatFormatting.GRAY;
        };
        Component label = Component.translatable("fluidcompat.status." + status.name().toLowerCase(Locale.ROOT));
        return Component.literal(module.displayName() + ": ").append(label.copy().withStyle(color));
    }

    private static boolean isLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    private static void saveAll() {
        try {
            FluidCompatConfig.SPEC.save();
            FluidCompatModulesConfig.SPEC.save();
            FluidCompatLog.info("Config saved from the YACL screen");
        } catch (RuntimeException exception) {
            FluidCompatLog.error("Could not save the Fluid Compat config", exception);
        }
    }
}
