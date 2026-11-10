package me.thanhtnt2.fluidcompat.compat.flowingfluids.patches.guard;

/**
 * Names of the vanilla members the bytecode guards look for, in both spellings.
 *
 * <p>Forge 1.20.1 runs the game and every released mod with SRG names ({@code m_6425_} instead of
 * {@code getFluidState}); only the development environment shows the Mojang names. A guard that compared plain
 * Mojang names would therefore find nothing in a real game and switch every patch off. Each name here carries both
 * spellings, so the same guard works in the IDE and in a launcher. The SRG ids were read from the bytecode of the
 * real Flowing Fluids 1.0.6 (1.20.1) jar and from Forge 1.20.1 patch files; none is guessed.
 *
 * <p>Pure strings: nothing here loads Minecraft.
 */
final class ObfNames {

    /** One vanilla member: its Mojang name and its SRG id. */
    record Name(String named, String srg) {
        boolean matches(String actual) {
            return named.equals(actual) || srg.equals(actual);
        }
    }

    // Level / LevelAccessor / BlockGetter
    static final Name GET_BLOCK_ENTITY = new Name("getBlockEntity", "m_7702_");
    static final Name SET_BLOCK = new Name("setBlock", "m_7731_");
    static final Name LEVEL_GET_FLUID_STATE = new Name("getFluidState", "m_6425_");
    static final Name SCHEDULE_TICK = new Name("scheduleTick", "m_186469_");
    // BlockState / FluidState / Fluid
    static final Name STATE_GET_FLUID_STATE = new Name("getFluidState", "m_60819_");
    static final Name FLUID_STATE_GET_TYPE = new Name("getType", "m_76152_");
    static final Name FLUID_STATE_IS_EMPTY = new Name("isEmpty", "m_76178_");
    static final Name GET_TICK_DELAY = new Name("getTickDelay", "m_6718_");
    // Blocks.AIR
    static final Name BLOCKS_AIR = new Name("AIR", "f_50016_");
    // LiquidBlock
    static final Name NEIGHBOR_CHANGED = new Name("neighborChanged", "m_6861_");
    static final Name ON_PLACE = new Name("onPlace", "m_6807_");
    // Added by Forge, so it keeps one name everywhere: seen in the real 1.20.1 game, the patched bodies call getFluid()
    static final Name LIQUID_BLOCK_GET_FLUID = new Name("getFluid", "getFluid");

    private ObfNames() {
    }

    static boolean isFluidStateGetter(String actual) {
        return LEVEL_GET_FLUID_STATE.matches(actual) || STATE_GET_FLUID_STATE.matches(actual);
    }
}
