package me.thanhtnt2.fluidcompat.core.sweep;

/**
 * The sweep guard: decides whether one more sweep may run, and force-stops the whole feature when sweeping itself
 * costs too much or the server is in trouble while sweeps are running.
 *
 * <p>Pure logic with primitive state only: no Minecraft types, no references to worlds or machines, a fixed number
 * of fields, so it can never grow or keep anything alive. The caller passes in the game tick, the server's average
 * tick time and the {@link Settings} read from the config.
 *
 * <p>Three layers, from cheap to drastic:
 * <ol>
 *   <li>a cap on sweeps per tick (shrunk by Auto Balanced Compat when the server lags): extra requests are
 *       {@link Decision#THROTTLED} and simply try again on the machine's next cycle;</li>
 *   <li>a cost trip: if sweeping used more than {@code maxMicrosPerTick} on average over the last 20 ticks, the feature
 *       is switched off for a cooldown;</li>
 *   <li>an emergency trip: if the server's average tick time is above {@code emergencyMspt} in a window in which
 *       sweeps ran, the feature is switched off for a cooldown. After {@code maxTripsPerSession} trips it stays off
 *       until the server restarts.</li>
 * </ol>
 */
public final class SweepGovernor {

    /** Values copied from the config for one decision. */
    public record Settings(
            boolean enabled,
            int maxSweepsPerTick,
            long maxNanosPerTick,
            double emergencyMspt,
            int tripCooldownTicks,
            int maxTripsPerSession) {
    }

    public enum Decision { ALLOW, THROTTLED, DISABLED }

    /** What happened when a window closed, so the caller can write one log line. */
    public enum WindowEvent { NONE, TRIPPED_COST, TRIPPED_EMERGENCY }

    static final int WINDOW_TICKS = 20;

    private long windowStartTick = Long.MIN_VALUE;
    private long windowNanos;
    private int windowSweeps;

    private long currentTick = Long.MIN_VALUE;
    private int sweepsThisTick;

    private long disabledUntilTick = Long.MIN_VALUE;
    private int tripCount;
    private boolean permanentlyOff;

    private WindowEvent pendingEvent = WindowEvent.NONE;
    private double pendingAverageMicros;

    private long totalSweeps;
    private long totalDrainedLevels;
    private long totalThrottled;
    private long totalNanos;

    /** True when a window ends at this tick, so the caller only measures the server's tick time then. */
    public boolean windowDue(long tick) {
        return windowStartTick == Long.MIN_VALUE || tick < windowStartTick || tick - windowStartTick >= WINDOW_TICKS;
    }

    /**
     * @param serverMspt average server tick time in milliseconds; only read when {@link #windowDue(long)} was true
     * @param slowdown   1 while the server keeps up, higher when Auto Balanced Compat asks work to be stretched
     */
    public Decision acquire(long tick, double serverMspt, int slowdown, Settings settings) {
        if (windowDue(tick)) {
            closeWindow(tick, serverMspt, settings);
        }
        if (!settings.enabled() || permanentlyOff || tick < disabledUntilTick) {
            return Decision.DISABLED;
        }
        if (tick != currentTick) {
            currentTick = tick;
            sweepsThisTick = 0;
        }
        int cap = Math.max(1, settings.maxSweepsPerTick() / Math.max(1, slowdown));
        if (sweepsThisTick >= cap) {
            totalThrottled++;
            return Decision.THROTTLED;
        }
        sweepsThisTick++;
        return Decision.ALLOW;
    }

    /** Reports what an allowed sweep cost, so the cost trip has something to measure. */
    public void record(long nanos, int drainedLevels) {
        windowNanos += nanos;
        windowSweeps++;
        totalSweeps++;
        totalNanos += nanos;
        totalDrainedLevels += Math.max(0, drainedLevels);
    }

    /** @return the event of the last closed window once, then {@link WindowEvent#NONE} */
    public WindowEvent consumeEvent() {
        WindowEvent event = pendingEvent;
        pendingEvent = WindowEvent.NONE;
        return event;
    }

    public double lastTripAverageMicros() {
        return pendingAverageMicros;
    }

    public boolean isPermanentlyOff() {
        return permanentlyOff;
    }

    public int tripCount() {
        return tripCount;
    }

    public long totalSweeps() {
        return totalSweeps;
    }

    public long totalDrainedLevels() {
        return totalDrainedLevels;
    }

    public long totalThrottled() {
        return totalThrottled;
    }

    public long totalNanos() {
        return totalNanos;
    }

    /** Forgets everything; called when a new server session starts. */
    public void reset() {
        windowStartTick = Long.MIN_VALUE;
        windowNanos = 0L;
        windowSweeps = 0;
        currentTick = Long.MIN_VALUE;
        sweepsThisTick = 0;
        disabledUntilTick = Long.MIN_VALUE;
        tripCount = 0;
        permanentlyOff = false;
        pendingEvent = WindowEvent.NONE;
        totalSweeps = 0L;
        totalDrainedLevels = 0L;
        totalThrottled = 0L;
        totalNanos = 0L;
    }

    private void closeWindow(long tick, double serverMspt, Settings settings) {
        boolean sweptInWindow = windowSweeps > 0;
        double averageNanosPerTick = (double) windowNanos / WINDOW_TICKS;
        windowStartTick = tick;
        windowNanos = 0L;
        windowSweeps = 0;
        if (!sweptInWindow) {
            return;
        }
        if (averageNanosPerTick > settings.maxNanosPerTick()) {
            trip(tick, settings, WindowEvent.TRIPPED_COST, averageNanosPerTick / 1000.0D);
        } else if (serverMspt > settings.emergencyMspt()) {
            trip(tick, settings, WindowEvent.TRIPPED_EMERGENCY, averageNanosPerTick / 1000.0D);
        }
    }

    private void trip(long tick, Settings settings, WindowEvent event, double averageMicros) {
        tripCount++;
        disabledUntilTick = tick + settings.tripCooldownTicks();
        permanentlyOff = tripCount >= settings.maxTripsPerSession();
        pendingEvent = event;
        pendingAverageMicros = averageMicros;
    }
}
