package me.thanhtnt2.fluidcompat.compat;

import java.util.List;

import me.thanhtnt2.fluidcompat.api.compat.CompatModule;
import me.thanhtnt2.fluidcompat.api.compat.CompatModuleRegistry;
import me.thanhtnt2.fluidcompat.api.compat.CompatStatus;
import me.thanhtnt2.fluidcompat.api.event.FluidIntakeEvent;
import me.thanhtnt2.fluidcompat.compat.alexscaves.AlexsCavesCompatModule;
import me.thanhtnt2.fluidcompat.compat.flowingfluids.patches.FlowingFluidsPatchesCompatModule;
import me.thanhtnt2.fluidcompat.compat.flowingfluids.pipes.FlowingFluidsPipesCompatModule;
import me.thanhtnt2.fluidcompat.compat.fluidlogged.FluidloggedCompatModule;
import me.thanhtnt2.fluidcompat.compat.gtceu.GtceuCompatModule;
import me.thanhtnt2.fluidcompat.compat.immersiveengineering.ImmersiveEngineeringCompatModule;
import me.thanhtnt2.fluidcompat.compat.flowingfluids.probe.FlowingFluidsProbeCompatModule;
import me.thanhtnt2.fluidcompat.compat.info.InfoCompatModule;
import me.thanhtnt2.fluidcompat.compat.mekanism.MekanismCompatModule;
import me.thanhtnt2.fluidcompat.compat.mekanismelements.MekanismElementsCompatModule;
import me.thanhtnt2.fluidcompat.config.FluidCompatModulesConfig;
import me.thanhtnt2.fluidcompat.core.sweep.FluidSweepServiceImpl;
import me.thanhtnt2.fluidcompat.logging.DebugSnapshot;
import me.thanhtnt2.fluidcompat.logging.FluidCompatLog;
import me.thanhtnt2.fluidcompat.logging.IntakeAuditLogger;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Registers and starts every compat module. Kept separate from the core bootstrap so adding a mod
 * never means editing the code of an earlier integration.
 *
 * <p>Each supported mod adds one line to {@link #registerBuiltInModules()} and nothing else here. Besides the machine
 * compat modules there are the Flowing Fluids bug-fix patches and a start-up probe that reports whether the environment
 * matches what the code expects.
 */
public final class CompatBootstrap {

    private static final List<String> MODS_TO_REPORT = List.of("forge", "flowing_fluids", "mekanism", "mekanismelements", "alexscaves", "fluidlogged", "immersiveengineering", "gtceu");

    private CompatBootstrap() {
    }

    /** Registers the built-in modules. Called from the mod constructor so addons can register theirs too. */
    public static void registerBuiltInModules() {
        // Already wired elsewhere (the backend); listed here for the log only
        CompatModuleRegistry.register(new InfoCompatModule("flowing_fluids", "flowing_fluids", "Flowing Fluids (drain backend)"));
        CompatModuleRegistry.register(new FlowingFluidsProbeCompatModule());
        CompatModuleRegistry.register(new FlowingFluidsPatchesCompatModule());
        CompatModuleRegistry.register(new MekanismCompatModule());
        CompatModuleRegistry.register(new MekanismElementsCompatModule());
        CompatModuleRegistry.register(new AlexsCavesCompatModule());
        CompatModuleRegistry.register(new FluidloggedCompatModule());
        CompatModuleRegistry.register(new ImmersiveEngineeringCompatModule());
        CompatModuleRegistry.register(new FlowingFluidsPipesCompatModule());
        CompatModuleRegistry.register(new GtceuCompatModule());
    }

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        // Queued after the core bootstrap, so the API is already bound when modules start
        event.enqueueWork(CompatBootstrap::initialize);
    }

    private static void initialize() {
        reportEnvironment();
        if (FluidCompatLog.isDebugMode()) {
            DebugSnapshot.logAll();
        }
        for (CompatModule module : CompatModuleRegistry.snapshot()) {
            activate(module);
        }
        // The explicit class overload: Forge cannot work out the event type of a method reference on its own everywhere
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, FluidIntakeEvent.Post.class, IntakeAuditLogger::onIntake);
        // A guard trip in one world must not follow the player into the next one
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ServerStartingEvent.class,
                event -> FluidSweepServiceImpl.resetGuard());
        FluidCompatLog.info("Compat setup finished: {}", summary());
    }

    private static void activate(CompatModule module) {
        if (!ModList.get().isLoaded(module.modId())) {
            CompatModuleRegistry.updateStatus(module.id(), CompatStatus.MISSING_MOD);
            return;
        }
        if (!FluidCompatModulesConfig.COMPAT_ENABLED.get()) {
            CompatModuleRegistry.updateStatus(module.id(), CompatStatus.DISABLED);
            return;
        }
        try {
            module.initialize();
            CompatModuleRegistry.updateStatus(module.id(), CompatStatus.ACTIVE);
        } catch (RuntimeException | LinkageError error) {
            // One broken module must never take the others, or the game, down with it
            CompatModuleRegistry.updateStatus(module.id(), CompatStatus.FAILED);
            FluidCompatLog.error("Compat module '" + module.id() + "' failed to initialise", error);
        }
    }

    private static void reportEnvironment() {
        StringBuilder mods = new StringBuilder();
        for (String modId : MODS_TO_REPORT) {
            ModList.get().getModContainerById(modId).ifPresent(container ->
                    mods.append(modId).append('=').append(container.getModInfo().getVersion()).append(' '));
        }
        FluidCompatLog.info("Relevant mods: {}", mods.toString().trim());
    }

    private static String summary() {
        StringBuilder text = new StringBuilder();
        for (CompatModule module : CompatModuleRegistry.snapshot()) {
            text.append(module.id()).append('=').append(CompatModuleRegistry.statusOf(module.id())).append(' ');
        }
        return text.toString().trim();
    }
}
