package me.thanhtnt2.fluidcompat.api.sweep;

/**
 * Outcome of a {@link FluidSweepRequest}.
 *
 * @param status        why the sweep ended
 * @param drainedLevels levels really removed from the world; the machine may receive exactly this much and no more
 */
public record FluidSweepResult(Status status, int drainedLevels) {

    public enum Status {
        /** Some fluid was removed; see {@link #drainedLevels()}. */
        DRAINED,
        /** A whole search pass within reach found nothing. The machine may wait a little before asking again. */
        NOTHING_FOUND,
        /** The search is still going: this call used its look-up budget. Ask again on the next cycle. */
        SEARCHING,
        /** The per-tick work budget was used up. Asking again on the next cycle is fine. */
        THROTTLED,
        /** The sweep feature is switched off in the config, or its guard stopped it after the server lagged. */
        DISABLED,
        /** The fluid is not kept finite, so the machine should behave as it would without Fluid Compat. */
        UNMANAGED
    }

    public static FluidSweepResult of(Status status) {
        return new FluidSweepResult(status, 0);
    }

    public boolean drained() {
        return status == Status.DRAINED && drainedLevels > 0;
    }
}
