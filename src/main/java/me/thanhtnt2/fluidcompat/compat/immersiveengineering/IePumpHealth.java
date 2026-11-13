package me.thanhtnt2.fluidcompat.compat.immersiveengineering;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import me.thanhtnt2.fluidcompat.logging.FluidCompatLog;

/**
 * Circuit breaker for the Immersive Engineering pump hooks.
 *
 * <p>Every hook falls back to the pump's own behaviour when something throws. If that keeps happening the hooks are
 * switched off for the rest of the session with one log line, so a changed IE can never make the pump tick throw all
 * day. Any success starts the count again.
 */
final class IePumpHealth {

    private static final int FAILURES_TO_SWITCH_OFF = 5;
    private static final AtomicInteger FAILURES_IN_A_ROW = new AtomicInteger();
    private static final AtomicBoolean SWITCHED_OFF = new AtomicBoolean();

    private IePumpHealth() {
    }

    static boolean isActive() {
        return !SWITCHED_OFF.get();
    }

    static void succeeded() {
        if (FAILURES_IN_A_ROW.get() != 0) {
            FAILURES_IN_A_ROW.set(0);
        }
    }

    static void failed(String hook, Throwable error) {
        FluidCompatLog.errorThrottled("ie-pump-" + hook, "Immersive Engineering pump hook '" + hook + "' failed; "
                + "the pump keeps its original behaviour for this call", error);
        if (FAILURES_IN_A_ROW.incrementAndGet() >= FAILURES_TO_SWITCH_OFF && SWITCHED_OFF.compareAndSet(false, true)) {
            FluidCompatLog.warn("[immersiveengineering] pump hooks switched off for this session after {} failures in a row; "
                    + "the pump behaves as IE made it (the world still pays for nothing it gives)", FAILURES_TO_SWITCH_OFF);
        }
    }
}
