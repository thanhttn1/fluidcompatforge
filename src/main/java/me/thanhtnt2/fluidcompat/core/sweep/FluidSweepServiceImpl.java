package me.thanhtnt2.fluidcompat.core.sweep;

import me.thanhtnt2.fluidcompat.api.FiniteFluidService;
import me.thanhtnt2.fluidcompat.api.FluidCompatApi;
import me.thanhtnt2.fluidcompat.api.FluidIntakeRequest;
import me.thanhtnt2.fluidcompat.api.FluidIntakeResult;
import me.thanhtnt2.fluidcompat.api.sweep.FluidSweepRequest;
import me.thanhtnt2.fluidcompat.api.sweep.FluidSweepResult;
import me.thanhtnt2.fluidcompat.api.sweep.FluidSweepResult.Status;
import me.thanhtnt2.fluidcompat.api.sweep.FluidSweepService;
import me.thanhtnt2.fluidcompat.config.FluidCompatModulesConfig;
import me.thanhtnt2.fluidcompat.core.balance.AutoBalance;
import me.thanhtnt2.fluidcompat.logging.FluidCompatLog;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * Default {@link FluidSweepService}: guard, find a start cell, then drain through the normal intake pipeline, so
 * the finite-fluid rules, the events and the backend choice are exactly the ones every other machine goes through.
 */
public final class FluidSweepServiceImpl implements FluidSweepService {

    private static final int TICKS_PER_SECOND = 20;
    private static final long NANOS_PER_MICRO = 1_000L;

    // Holds only counters, never a world or a machine
    private static final SweepGovernor GOVERNOR = new SweepGovernor();

    @Override
    public FluidSweepResult sweep(FluidSweepRequest request) {
        if (request.maxLevels() <= 0) {
            return FluidSweepResult.of(Status.NOTHING_FOUND);
        }
        FiniteFluidService fluids = FluidCompatApi.service();
        if (!fluids.isFinite(request.fluid())) {
            return FluidSweepResult.of(Status.UNMANAGED);
        }
        ServerLevel level = request.level();
        MinecraftServer server = level.getServer();
        long tick = server.getTickCount();
        SweepGovernor.Decision decision = decide(server, tick);
        if (decision != SweepGovernor.Decision.ALLOW) {
            return FluidSweepResult.of(decision == SweepGovernor.Decision.THROTTLED ? Status.THROTTLED : Status.DISABLED);
        }
        long startedAt = System.nanoTime();
        int drainedLevels = 0;
        try {
            FluidSweepResult result = drain(fluids, request);
            drainedLevels = result.drainedLevels();
            return result;
        } finally {
            GOVERNOR.record(System.nanoTime() - startedAt, drainedLevels);
        }
    }

    /** Called when a new server session starts, so a trip in one world does not follow the player into the next. */
    public static void resetGuard() {
        GOVERNOR.reset();
    }

    private static SweepGovernor.Decision decide(MinecraftServer server, long tick) {
        SweepGovernor.Settings settings = readSettings();
        // The tick time is only measured when a window closes, not on every request
        double serverMspt = GOVERNOR.windowDue(tick) ? AutoBalance.averageTickMillis(server) : 0.0D;
        SweepGovernor.Decision decision = GOVERNOR.acquire(tick, serverMspt, AutoBalance.slowdown(server), settings);
        reportWindowEvent(settings);
        return decision;
    }

    private static FluidSweepResult drain(FiniteFluidService fluids, FluidSweepRequest request) {
        SweepPlanner.Search search = SweepPlanner.search(request.level(), request.origin(), request.fluid(), request.cursor(),
                FluidCompatModulesConfig.SWEEP_REACH.get(), FluidCompatModulesConfig.SWEEP_SCAN_CELLS_PER_CALL.get());
        BlockPos start = search.start();
        if (start == null) {
            return FluidSweepResult.of(search.passComplete() ? Status.NOTHING_FOUND : Status.SEARCHING);
        }
        FluidIntakeRequest intake = new FluidIntakeRequest(request.level(), request.machinePos(), request.machineId(),
                request.fluid(), request.maxLevels(), List.of(start));
        FluidIntakeResult result = fluids.requestIntake(intake);
        if (!result.managed()) {
            return FluidSweepResult.of(Status.UNMANAGED);
        }
        // Never report more than was asked for, whatever a backend or a listener did
        int drained = Math.max(0, Math.min(result.drainedLevels(), request.maxLevels()));
        return drained > 0 ? new FluidSweepResult(Status.DRAINED, drained) : FluidSweepResult.of(Status.NOTHING_FOUND);
    }

    private static SweepGovernor.Settings readSettings() {
        return new SweepGovernor.Settings(
                FluidCompatModulesConfig.SWEEP_ENABLED.get(),
                FluidCompatModulesConfig.SWEEP_MAX_PER_TICK.get(),
                FluidCompatModulesConfig.SWEEP_MAX_MICROS_PER_TICK.get() * NANOS_PER_MICRO,
                FluidCompatModulesConfig.SWEEP_EMERGENCY_MSPT.get(),
                FluidCompatModulesConfig.SWEEP_TRIP_COOLDOWN_SECONDS.get() * TICKS_PER_SECOND,
                FluidCompatModulesConfig.SWEEP_MAX_TRIPS_PER_SESSION.get());
    }

    private static void reportWindowEvent(SweepGovernor.Settings settings) {
        SweepGovernor.WindowEvent event = GOVERNOR.consumeEvent();
        if (event == SweepGovernor.WindowEvent.NONE) {
            return;
        }
        String reason = event == SweepGovernor.WindowEvent.TRIPPED_COST
                ? "sweeping used " + Math.round(GOVERNOR.lastTripAverageMicros()) + " microseconds per tick on average"
                : "the server tick time was above " + settings.emergencyMspt() + " ms while sweeps were running";
        FluidCompatLog.warn("[sweep] guard stopped leftover-fluid sweeping for {} seconds because {} (stop {} of {}{})",
                settings.tripCooldownTicks() / TICKS_PER_SECOND, reason, GOVERNOR.tripCount(), settings.maxTripsPerSession(),
                GOVERNOR.isPermanentlyOff() ? ", sweeping stays off until the server restarts" : "");
    }
}
