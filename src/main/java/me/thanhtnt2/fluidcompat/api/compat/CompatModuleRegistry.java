package me.thanhtnt2.fluidcompat.api.compat;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Registry of {@link CompatModule}s. Register from your mod constructor, before common setup. */
public final class CompatModuleRegistry {

    private static final List<CompatModule> MODULES = new CopyOnWriteArrayList<>();
    private static final Map<String, CompatStatus> STATUSES = new ConcurrentHashMap<>();

    private CompatModuleRegistry() {
    }

    public static synchronized void register(CompatModule module) {
        Objects.requireNonNull(module, "module");
        boolean duplicate = MODULES.stream().anyMatch(existing -> existing.id().equals(module.id()));
        if (duplicate) {
            throw new IllegalArgumentException("Compat module already registered: " + module.id());
        }
        MODULES.add(module);
        STATUSES.put(module.id(), CompatStatus.PENDING);
    }

    /** Immutable view in registration order. */
    public static List<CompatModule> snapshot() {
        return List.copyOf(MODULES);
    }

    public static CompatStatus statusOf(String moduleId) {
        return STATUSES.getOrDefault(moduleId, CompatStatus.PENDING);
    }

    /** Internal: called by Fluid Compat while it evaluates the modules. */
    public static void updateStatus(String moduleId, CompatStatus status) {
        STATUSES.put(moduleId, status);
    }
}
