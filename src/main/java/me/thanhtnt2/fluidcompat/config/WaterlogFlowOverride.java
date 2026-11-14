package me.thanhtnt2.fluidcompat.config;

/**
 * How Flowing Fluids should treat waterlogged blocks (pipes are waterlogged blocks).
 * The names mirror Flowing Fluids' own WaterLogFlowMode so the value can be mapped by name.
 */
public enum WaterlogFlowOverride {
    /** Leave Flowing Fluids' own setting untouched. */
    KEEP_FLOWING_FLUIDS_SETTING,
    ONLY_IN,
    ONLY_OUT,
    IN_FROM_TOP_ELSE_OUT,
    OUT_DOWN_ELSE_IN,
    /** Waterlogged blocks neither give nor take fluid: a submerged pipe stays submerged. */
    IGNORE
}
