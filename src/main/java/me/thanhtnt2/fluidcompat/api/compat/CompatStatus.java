package me.thanhtnt2.fluidcompat.api.compat;

/** Lifecycle state of a {@link CompatModule}, shown in the "Mod Compat List" config tab and in the log file. */
public enum CompatStatus {
    /** Registered but not evaluated yet. */
    PENDING,
    /** The target mod is loaded and the module initialised without errors. */
    ACTIVE,
    /** The target mod is not installed, so the module was skipped on purpose. */
    MISSING_MOD,
    /** Switched off in the config. */
    DISABLED,
    /** The module threw while initialising; the rest of Fluid Compat keeps working. */
    FAILED
}
