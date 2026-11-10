package me.thanhtnt2.fluidcompat.api.compat;

/**
 * One isolated integration with another mod.
 *
 * <p>A module is only initialised when {@link #modId()} is loaded, so its implementation may freely
 * reference classes of that mod. Never touch another module's classes from inside a module.
 * Addons register their own modules from their mod constructor via {@link CompatModuleRegistry}.
 */
public interface CompatModule {

    /** Stable id used in logs and config, for example {@code "mekanism"}. */
    String id();

    /** Mod id that must be loaded for this module to run. */
    String modId();

    /** Human readable name shown in the config screen. */
    String displayName();

    /** Called once on the main thread during common setup, after the core API is ready. */
    void initialize();
}
