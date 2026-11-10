package me.thanhtnt2.fluidcompat.core.balance;

import me.thanhtnt2.fluidcompat.config.FluidCompatModulesConfig;
import net.minecraft.server.MinecraftServer;

/**
 * "Auto Balanced Compat": tells Fluid Compat's optional periodic work how much to back off while the server lags.
 *
 * <p>Only intervals of Fluid Compat's own housekeeping are stretched. The finite-fluid accounting
 * (drains, pump cycles) is never delayed or skipped, so no water is ever created by this class.
 */
public final class AutoBalance {

    // The factor is recomputed about once a second; between those moments it is a plain field read
    private static final long REFRESH_INTERVAL_TICKS = 20L;
    private static final double NANOS_PER_MILLI = 1_000_000.0D;

    private static volatile int cachedSlowdown = 1;
    private static volatile long refreshedAtTick = Long.MIN_VALUE;

    private AutoBalance() {
    }

    /** 1 while the server keeps up, up to {@code maxSlowdown} while it does not. */
    public static int slowdown(MinecraftServer server) {
        if (!FluidCompatModulesConfig.AUTO_BALANCED_ENABLED.get()) {
            return 1;
        }
        long tick = server.getTickCount();
        boolean fresh = tick >= refreshedAtTick && tick - refreshedAtTick < REFRESH_INTERVAL_TICKS;
        if (fresh) {
            return cachedSlowdown;
        }
        cachedSlowdown = computeSlowdown(server);
        refreshedAtTick = tick;
        return cachedSlowdown;
    }

    /** {@code baseTicks} stretched by the current slowdown, never below {@code baseTicks}. */
    public static int scaleTicks(MinecraftServer server, int baseTicks) {
        long scaled = (long) baseTicks * slowdown(server);
        return (int) Math.min(scaled, Integer.MAX_VALUE);
    }

    private static int computeSlowdown(MinecraftServer server) {
        double averageMillis = averageTickMillis(server);
        double targetMillis = targetMillis();
        if (averageMillis <= targetMillis) {
            return 1;
        }
        int factor = (int) Math.ceil(averageMillis / targetMillis);
        return Math.min(factor, FluidCompatModulesConfig.AUTO_BALANCED_MAX_SLOWDOWN.get());
    }

    /**
     * Mean of the last 100 tick durations in milliseconds. Minecraft 1.20.1 has no averageTickTimeNanos getter, so this
     * reads the same public array Forge's own {@code /forge tps} command uses (nanoseconds per tick).
     */
    public static double averageTickMillis(MinecraftServer server) {
        long[] tickTimes = server.tickTimes;
        if (tickTimes == null || tickTimes.length == 0) {
            return 0.0D;
        }
        long sum = 0L;
        for (long nanos : tickTimes) {
            sum += nanos;
        }
        return (double) sum / tickTimes.length / NANOS_PER_MILLI;
    }

    private static double targetMillis() {
        double configured = FluidCompatModulesConfig.AUTO_BALANCED_TARGET_MSPT.get();
        if (!FluidCompatModulesConfig.AUTO_BALANCED_FOLLOW_FLOWING_FLUIDS.get()) {
            return configured;
        }
        return FlowingFluidsBalanceProbe.targetMillis().orElse(configured);
    }
}
