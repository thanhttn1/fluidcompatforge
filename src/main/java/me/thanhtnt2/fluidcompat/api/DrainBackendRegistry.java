package me.thanhtnt2.fluidcompat.api;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry of {@link DrainBackend}s. Addons register from {@code FMLCommonSetupEvent#enqueueWork}.
 */
public final class DrainBackendRegistry {

    private static final Comparator<DrainBackend> HIGHEST_PRIORITY_FIRST =
            Comparator.comparingInt(DrainBackend::priority).reversed();

    // Copy-on-write because registration is rare while lookups happen every pump cycle
    private static final List<DrainBackend> BACKENDS = new CopyOnWriteArrayList<>();

    // The ordered, immutable list handed to callers. It is rebuilt only when a backend is registered, so a lookup
    // costs one volatile read instead of a fresh copy (some pumps ask on every tick)
    private static volatile List<DrainBackend> orderedSnapshot = List.of();

    private DrainBackendRegistry() {
    }

    public static synchronized void register(DrainBackend backend) {
        Objects.requireNonNull(backend, "backend");
        boolean duplicate = BACKENDS.stream().anyMatch(existing -> existing.id().equals(backend.id()));
        if (duplicate) {
            throw new IllegalArgumentException("Drain backend already registered: " + backend.id());
        }
        BACKENDS.add(backend);
        BACKENDS.sort(HIGHEST_PRIORITY_FIRST);
        orderedSnapshot = List.copyOf(BACKENDS);
    }

    /** Immutable view ordered from highest to lowest priority. */
    public static List<DrainBackend> snapshot() {
        return orderedSnapshot;
    }
}
