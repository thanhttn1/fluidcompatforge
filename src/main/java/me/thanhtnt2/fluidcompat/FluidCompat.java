package me.thanhtnt2.fluidcompat;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import me.thanhtnt2.fluidcompat.client.FluidCompatClient;
import me.thanhtnt2.fluidcompat.compat.CompatBootstrap;
import me.thanhtnt2.fluidcompat.config.FluidCompatConfig;
import me.thanhtnt2.fluidcompat.config.FluidCompatModulesConfig;
import me.thanhtnt2.fluidcompat.core.FluidCompatBootstrap;
import me.thanhtnt2.fluidcompat.logging.FluidCompatLog;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Common entry point (Forge 1.20.1). Contains no client code, so it is safe on dedicated servers.
 *
 * <p>The constructor takes the loading context as a parameter: Forge 1.20.1 offers that, and the static
 * {@code FMLJavaModLoadingContext.get()} / {@code ModLoadingContext.get()} are deprecated for removal.
 */
@Mod(FluidCompat.MODID)
public final class FluidCompat {

    public static final String MODID = "fluidcompat";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final String YACL_MOD_ID = "yet_another_config_lib_v3";
    private static final String COMPAT_CONFIG_FILE_NAME = "fluidcompat-compat.toml";

    public FluidCompat(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        ModContainer container = context.getContainer();
        registerConfig(container, FluidCompatConfig.SPEC, null);
        modEventBus.addListener(this::onCommonSetup);
        registerCompatLayer(modEventBus, container);
        registerClientSide(container);
    }

    // Compat modules live in their own packages and config file; this is their single entry point
    private static void registerCompatLayer(IEventBus modEventBus, ModContainer container) {
        FluidCompatLog.initialize();
        registerConfig(container, FluidCompatModulesConfig.SPEC, COMPAT_CONFIG_FILE_NAME);
        CompatBootstrap.registerBuiltInModules();
        // Added after the core listener on purpose: its setup work is queued first, so the API is bound first
        modEventBus.addListener(CompatBootstrap::onCommonSetup);
    }

    // The settings screen needs YACL and a client; the client class is only touched when both are true
    private static void registerClientSide(ModContainer container) {
        if (!FMLEnvironment.dist.isClient() || !ModList.get().isLoaded(YACL_MOD_ID)) {
            return;
        }
        FluidCompatClient.registerConfigScreen(container);
    }

    private static void registerConfig(ModContainer container, ForgeConfigSpec spec, String fileName) {
        ModConfig config = fileName == null
                ? new ModConfig(ModConfig.Type.COMMON, spec, container)
                : new ModConfig(ModConfig.Type.COMMON, spec, container, fileName);
        container.addConfig(config);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        // enqueueWork so the API is bound on the main thread before any other mod's setup work runs
        event.enqueueWork(FluidCompatBootstrap::initialize);
    }
}
