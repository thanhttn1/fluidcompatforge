package me.thanhtnt2.fluidcompat.compat.flowingfluids.probe;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import me.thanhtnt2.fluidcompat.api.compat.CompatModule;
import me.thanhtnt2.fluidcompat.logging.FluidCompatLog;

/**
 * Start-up self check for the 1.20.1 line ("Gate 0"): does the installed Flowing Fluids offer the API that the
 * Fluid Compat core calls, and does this game run with the names the patches expect?
 *
 * <p>It only reads class shapes through reflection and writes the answers to {@code logs/fluidcompat.log}. It
 * changes nothing, has no compile-time link to Flowing Fluids and cannot stop the game: every failure is caught and
 * reported as a line in the log. Keeping it as its own module means it can be removed without touching any other code.
 */
public final class FlowingFluidsProbeCompatModule implements CompatModule {

    public static final String ID = "flowing_fluids_probe";

    private static final String API_CLASS = "traben.flowing_fluids.api.FlowingFluidsAPI";
    private static final String CONFIG_CLASS = "traben.flowing_fluids.config.FFConfig";

    // Method signatures of the Flowing Fluids API that the core calls, as name(simple parameter types)
    private static final List<String> REQUIRED_API_METHODS = List.of(
            "getInstance(String)",
            "isModEnabled()",
            "doesModifyThisFluid(Fluid)",
            "getConfig()",
            "removeFluidAmountFromPos(LevelAccessor,BlockPos,Fluid,int,int)",
            "placeFluidAmountFromPos(LevelAccessor,BlockPos,Fluid,int,boolean,boolean)",
            "registerBlockThatWontDisplaceFluid(Fluid,Block)",
            "registerBlockTagThatWontDisplaceFluid(Fluid,TagKey)");

    // Public fields of FFConfig that the Auto Balanced Compat probe reads
    private static final List<String> REQUIRED_CONFIG_FIELDS = List.of(
            "enableMod", "autoPerformanceMode", "autoPerformanceMSPTargetMultiplier");

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
        return "Flowing Fluids (start-up probe)";
    }

    @Override
    public void initialize() {
        List<String> problems = new ArrayList<>();
        try {
            reportNaming();
            reportRefmap(problems);
            checkApi(problems);
            checkConfig(problems);
            checkForgeFluidHooks(problems);
        } catch (RuntimeException | LinkageError unexpected) {
            FluidCompatLog.error("[probe] the probe itself failed", unexpected);
            return;
        }
        if (problems.isEmpty()) {
            FluidCompatLog.info("[probe] GATE 0 PASSED: Flowing Fluids offers every method and field the core uses");
        } else {
            for (String problem : problems) {
                FluidCompatLog.warn("[probe] {}", problem);
            }
            FluidCompatLog.warn("[probe] GATE 0 FAILED: {} problem(s) above. Features that depend on them stay unsafe", problems.size());
        }
    }

    /** Forge 1.20.1 runs with SRG names in a real game and with Mojang names in the IDE. Both are expected; say which. */
    private static void reportNaming() {
        String runtime = "unknown";
        try {
            Class<?> level = Class.forName("net.minecraft.world.level.Level");
            Class<?> blockPos = Class.forName("net.minecraft.core.BlockPos");
            if (hasMethod(level, "m_6425_", blockPos)) {
                runtime = "SRG names (a real game: bytecode guards use the SRG ids)";
            } else if (hasMethod(level, "getFluidState", blockPos)) {
                runtime = "Mojang names (a development environment)";
            }
        } catch (ClassNotFoundException missing) {
            runtime = "unknown (Level class not found)";
        }
        FluidCompatLog.info("[probe] vanilla names at runtime: {}", runtime);
    }

    /** The LiquidBlock patch needs the refmap the annotation processor writes; check it is really inside the jar. */
    private static void reportRefmap(List<String> problems) {
        String refmap = readResource("/fluidcompat.refmap.json");
        if (refmap == null) {
            problems.add("fluidcompat.refmap.json is missing from the mod: the fast interaction patch cannot find LiquidBlock in a real game");
            return;
        }
        boolean mapsNeighborChanged = refmap.contains("neighborChanged") && refmap.contains("m_6861_");
        boolean mapsOnPlace = refmap.contains("onPlace") && refmap.contains("m_6807_");
        FluidCompatLog.info("[probe] refmap present: neighborChanged->m_6861_ {} onPlace->m_6807_ {}", mapsNeighborChanged, mapsOnPlace);
        if (!mapsNeighborChanged || !mapsOnPlace) {
            problems.add("fluidcompat.refmap.json does not map LiquidBlock.neighborChanged/onPlace to their SRG ids");
        }
    }

    private static void checkApi(List<String> problems) {
        Class<?> api;
        try {
            api = Class.forName(API_CLASS);
        } catch (ClassNotFoundException | LinkageError missing) {
            problems.add(API_CLASS + " not found: this Flowing Fluids build has no public API");
            return;
        }
        Set<String> present = new TreeSet<>();
        for (Method method : api.getMethods()) {
            present.add(signatureOf(method));
        }
        for (String required : REQUIRED_API_METHODS) {
            if (!present.contains(required)) {
                problems.add("Flowing Fluids API lacks " + required);
            }
        }
        try {
            Object version = api.getField("VERSION").get(null);
            FluidCompatLog.info("[probe] FlowingFluidsAPI.VERSION = {}", version);
        } catch (ReflectiveOperationException | RuntimeException unreadable) {
            FluidCompatLog.info("[probe] FlowingFluidsAPI.VERSION is not readable");
        }
    }

    private static void checkConfig(List<String> problems) {
        Class<?> config;
        try {
            config = Class.forName(CONFIG_CLASS);
        } catch (ClassNotFoundException | LinkageError missing) {
            problems.add(CONFIG_CLASS + " not found");
            return;
        }
        for (String field : REQUIRED_CONFIG_FIELDS) {
            try {
                config.getField(field);
            } catch (NoSuchFieldException absent) {
                problems.add("FFConfig lacks the public field " + field);
            }
        }
    }

    /** The performance patch skips Forge's FluidInteractionRegistry.canInteract, so that method has to exist. */
    private static void checkForgeFluidHooks(List<String> problems) {
        try {
            Class<?> registry = Class.forName("net.minecraftforge.fluids.FluidInteractionRegistry");
            boolean found = false;
            for (Method method : registry.getMethods()) {
                found |= "canInteract".equals(method.getName()) && method.getParameterCount() == 2;
            }
            if (!found) {
                problems.add("Forge FluidInteractionRegistry.canInteract(Level, BlockPos) not found");
            }
        } catch (ClassNotFoundException | LinkageError missing) {
            problems.add("Forge FluidInteractionRegistry not found");
        }
    }

    private static boolean hasMethod(Class<?> owner, String name, Class<?>... parameters) {
        try {
            owner.getMethod(name, parameters);
            return true;
        } catch (NoSuchMethodException absent) {
            return false;
        }
    }

    private static String signatureOf(Method method) {
        StringBuilder text = new StringBuilder(method.getName()).append('(');
        Class<?>[] parameters = method.getParameterTypes();
        for (int index = 0; index < parameters.length; index++) {
            if (index > 0) {
                text.append(',');
            }
            text.append(parameters[index].getSimpleName());
        }
        return text.append(')').toString();
    }

    private static String readResource(String path) {
        try (InputStream stream = FlowingFluidsProbeCompatModule.class.getResourceAsStream(path)) {
            return stream == null ? null : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            return null;
        }
    }
}
