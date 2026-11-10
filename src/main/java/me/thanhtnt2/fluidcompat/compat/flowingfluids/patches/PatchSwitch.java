package me.thanhtnt2.fluidcompat.compat.flowingfluids.patches;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Decides, before any config is loaded, whether the Flowing Fluids patches may be applied.
 *
 * <p>Mixins are applied while the game is still starting, long before Forge reads {@code ForgeConfigSpec} files,
 * so the usual config values cannot be asked yet. This class reads the two relevant keys straight from the TOML
 * file instead. That is also why changing them needs a game restart. It deliberately touches no Minecraft class:
 * it runs inside the mixin plugin, where those classes are not ready.
 *
 * <p>A missing or unreadable file means "defaults", which means the patches are on.
 */
public final class PatchSwitch {

    private static final String CONFIG_FILE_NAME = "fluidcompat-compat.toml";
    private static final String GENERAL_SECTION = "general";
    private static final String PATCHES_SECTION = "flowing_fluid_patches";
    private static final Pattern SECTION_HEADER = Pattern.compile("^\\s*\\[([^\\]]+)]\\s*(#.*)?$");
    private static final Pattern BOOLEAN_ENTRY = Pattern.compile("^\\s*([A-Za-z0-9_]+)\\s*=\\s*(true|false)\\b.*$");

    private PatchSwitch() {
    }

    /**
     * @return why the patches must stay off, or {@code null} when they may be applied
     */
    public static String reasonToStayOff() {
        Map<String, Boolean> values = readBooleans();
        if (Boolean.TRUE.equals(values.get(PATCHES_SECTION + ".disablePatches"))) {
            return "flowing_fluid_patches.disablePatches is true";
        }
        if (Boolean.FALSE.equals(values.get(GENERAL_SECTION + ".compatEnabled"))) {
            return "general.compatEnabled is false";
        }
        return null;
    }

    /**
     * The fast interaction check patch has a switch of its own because it is the one patch that trades a little
     * theoretical exactness for server speed. It is on unless the player turned it off.
     */
    public static boolean fastInteractionChecksSwitchedOff() {
        return Boolean.FALSE.equals(readBooleans().get(PATCHES_SECTION + ".fastInteractionChecks"));
    }

    /** All {@code key = true/false} lines of the file as "section.key" to value; empty when the file cannot be read. */
    private static Map<String, Boolean> readBooleans() {
        Map<String, Boolean> values = new HashMap<>();
        List<String> lines;
        try {
            lines = Files.readAllLines(configFile(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException | LinkageError unreadable) {
            return values;
        }
        String section = "";
        for (String line : lines) {
            Matcher header = SECTION_HEADER.matcher(line);
            if (header.matches()) {
                section = header.group(1).trim();
                continue;
            }
            Matcher entry = BOOLEAN_ENTRY.matcher(line);
            if (entry.matches()) {
                values.put(section + "." + entry.group(1), Boolean.parseBoolean(entry.group(2)));
            }
        }
        return values;
    }

    private static Path configFile() {
        return FMLPaths.CONFIGDIR.get().resolve(CONFIG_FILE_NAME);
    }
}
