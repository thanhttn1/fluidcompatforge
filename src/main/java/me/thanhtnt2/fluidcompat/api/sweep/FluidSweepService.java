package me.thanhtnt2.fluidcompat.api.sweep;

/**
 * Takes leftover fluid on behalf of a machine, under a work budget that protects the server.
 *
 * <p>Every call runs on the server thread, reads only loaded chunks and does a bounded amount of work: a handful of
 * block reads to find fluid next to the machine, then one drain through the active backend (for Flowing Fluids that is
 * its own connected-fluid collector, which is itself capped). Fluid Compat's sweep guard may refuse calls with
 * {@link FluidSweepResult.Status#THROTTLED} or {@link FluidSweepResult.Status#DISABLED}.
 */
public interface FluidSweepService {

    FluidSweepResult sweep(FluidSweepRequest request);
}
