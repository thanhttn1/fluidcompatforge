package me.thanhtnt2.fluidcompat.compat.info;

import me.thanhtnt2.fluidcompat.api.compat.CompatModule;

/**
 * Describes an integration that is already wired elsewhere (the original Modern Industrialization hook,
 * the Flowing Fluids backend, Jade, JEI) so it shows up in the "Mod Compat List" and in the log.
 * It initialises nothing on purpose, which keeps those integrations untouched.
 */
public final class InfoCompatModule implements CompatModule {

    private final String id;
    private final String modId;
    private final String displayName;

    public InfoCompatModule(String id, String modId, String displayName) {
        this.id = id;
        this.modId = modId;
        this.displayName = displayName;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String modId() {
        return modId;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public void initialize() {
        // Nothing to do: the integration is registered by its original code path
    }
}
