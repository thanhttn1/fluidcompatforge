package me.thanhtnt2.fluidcompat.compat.mekanism;

import me.thanhtnt2.fluidcompat.api.sweep.SweepCursor;

/**
 * What one Electric Pump remembers between cycles of leftover sweeping. Lives inside the pump (a field added by the
 * mixin), so it disappears with the block entity: there is no registry of pumps to grow or to clean up.
 *
 * <p>No Minecraft types.
 */
public final class PumpSweepState {

    private boolean filterInstalled;
    private int idleCycles;
    private int cyclesToSkip;
    private final SweepCursor cursor = new SweepCursor();

    /** How far this pump's search for far-away thin water has got; one int, owned by the pump. */
    public SweepCursor cursor() {
        return cursor;
    }

    /** Mekanism passes "has Filter Upgrade" to every source check; the mixin forwards it here. */
    public void noteFilter(boolean installed) {
        this.filterInstalled = installed;
    }

    public boolean filterInstalled() {
        return filterInstalled;
    }

    /** @return true when this cycle must be skipped because the pump recently found nothing */
    public boolean consumeSkip() {
        if (cyclesToSkip <= 0) {
            return false;
        }
        cyclesToSkip--;
        return true;
    }

    /** The pump found water: look again on the very next cycle. */
    public void foundWater() {
        idleCycles = 0;
        cyclesToSkip = 0;
    }

    /** The pump found nothing: wait one cycle more each time, up to the configured maximum. */
    public void foundNothing(int maxBackoffCycles) {
        idleCycles = Math.min(idleCycles + 1, Math.max(0, maxBackoffCycles));
        cyclesToSkip = idleCycles;
    }
}
