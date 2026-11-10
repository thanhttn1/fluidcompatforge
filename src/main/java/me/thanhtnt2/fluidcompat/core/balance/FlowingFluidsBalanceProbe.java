package me.thanhtnt2.fluidcompat.core.balance;

import java.util.OptionalDouble;

import me.thanhtnt2.fluidcompat.api.FluidCompatApi;
import traben.flowing_fluids.api.FlowingFluidsAPI;
import traben.flowing_fluids.config.FFConfig;

/** Reads (never writes) Flowing Fluids' own tick-time target so both mods agree on when the server is slow. */
final class FlowingFluidsBalanceProbe {

    private static final double MILLIS_PER_TICK_AT_FULL_SPEED = 50.0D;

    // FlowingFluidsAPI.getInstance logs an INFO line on every call, so ask for the wrapper only once.
    // The config object behind it is read fresh each time, which keeps live changes visible
    private static volatile FlowingFluidsAPI cachedApi;

    private FlowingFluidsBalanceProbe() {
    }

    private static FlowingFluidsAPI api() {
        FlowingFluidsAPI api = cachedApi;
        if (api == null) {
            api = FlowingFluidsAPI.getInstance(FluidCompatApi.MOD_ID);
            cachedApi = api;
        }
        return api;
    }

    static OptionalDouble targetMillis() {
        try {
            FFConfig flowingFluidsConfig = api().getConfig();
            if (flowingFluidsConfig.autoPerformanceMode == FFConfig.AutoPerformance.OFF) {
                return OptionalDouble.empty();
            }
            return OptionalDouble.of(MILLIS_PER_TICK_AT_FULL_SPEED * flowingFluidsConfig.autoPerformanceMSPTargetMultiplier);
        } catch (RuntimeException | LinkageError unavailable) {
            // Flowing Fluids changed or is not ready yet: fall back to Fluid Compat's own target
            return OptionalDouble.empty();
        }
    }
}
