package me.thanhtnt2.fluidcompat.logging;

import me.thanhtnt2.fluidcompat.api.FluidIntakeRequest;
import me.thanhtnt2.fluidcompat.api.FluidIntakeResult;
import me.thanhtnt2.fluidcompat.api.event.FluidIntakeEvent;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Listens to the public {@link FluidIntakeEvent.Post} so intake logging works for every module,
 * including addons, without any of them having to log by themselves.
 */
public final class IntakeAuditLogger {

    private IntakeAuditLogger() {
    }

    public static void onIntake(FluidIntakeEvent.Post event) {
        // Runs for every intake of every machine: build no strings when nothing would be written
        if (!FluidCompatLog.isDetailLoggingActive()) {
            return;
        }
        FluidIntakeRequest request = event.getRequest();
        FluidIntakeResult result = event.getIntakeResult();
        String machine = String.valueOf(request.machineId()) + "@" + request.machinePos().toShortString();
        FluidCompatLog.debugThrottled(machine,
                "Intake machine={} fluid={} requestedLevels={} drainedLevels={} sources={} fullyServed={}",
                machine, BuiltInRegistries.FLUID.getKey(request.fluid()), result.requestedLevels(),
                result.drainedLevels(), request.sourcePositions().size(), result.isFullyServed());
    }
}
