package me.thanhtnt2.fluidcompat.compat.gtceu;

import me.thanhtnt2.fluidcompat.api.sweep.SweepCursor;

/** What one GregTech Pump remembers between cycles: where its leftover search stopped and how long to rest when idle. */
public final class GtPumpState {

    private int idleCycles;
    private int cyclesToSkip;
    private final SweepCursor cursor = new SweepCursor();

    SweepCursor cursor() {
        return cursor;
    }

    boolean consumeSkip() {
        if (cyclesToSkip <= 0) {
            return false;
        }
        cyclesToSkip--;
        return true;
    }

    void foundWater() {
        idleCycles = 0;
        cyclesToSkip = 0;
    }

    void foundNothing(int maxBackoffCycles) {
        idleCycles = Math.min(idleCycles + 1, Math.max(0, maxBackoffCycles));
        cyclesToSkip = idleCycles;
    }
}
