package me.thanhtnt2.fluidcompat.client;

import me.thanhtnt2.fluidcompat.client.yacl.YaclConfigScreenFactory;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModContainer;

/** Client-only registrations. Never referenced from code that can run on a dedicated server. */
public final class FluidCompatClient {

    private FluidCompatClient() {
    }

    /** Makes the Config button of the Mods screen open the YACL screen. Call it only when YACL is installed. */
    public static void registerConfigScreen(ModContainer container) {
        container.registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(YaclConfigScreenFactory::create));
    }
}
