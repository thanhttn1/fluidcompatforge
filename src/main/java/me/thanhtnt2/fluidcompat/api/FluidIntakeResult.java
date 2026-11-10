package me.thanhtnt2.fluidcompat.api;

/**
 * Outcome of a {@link FluidIntakeRequest}.
 *
 * @param requestedLevels levels Fluid Compat tried to drain (after listeners adjusted the request)
 * @param drainedLevels   levels actually removed from the world
 * @param managed         false when the fluid is not finite or a listener bypassed the logic;
 *                        the machine should then behave exactly as it would without Fluid Compat
 */
public record FluidIntakeResult(int requestedLevels, int drainedLevels, boolean managed) {

    public static FluidIntakeResult unmanaged(int requestedLevels) {
        return new FluidIntakeResult(requestedLevels, requestedLevels, false);
    }

    public boolean isFullyServed() {
        return drainedLevels >= requestedLevels;
    }
}
