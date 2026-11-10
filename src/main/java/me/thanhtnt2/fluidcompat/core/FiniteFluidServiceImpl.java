package me.thanhtnt2.fluidcompat.core;

import java.util.Optional;

import me.thanhtnt2.fluidcompat.api.DrainBackend;
import me.thanhtnt2.fluidcompat.api.DrainBackendRegistry;
import me.thanhtnt2.fluidcompat.api.FiniteFluidService;
import me.thanhtnt2.fluidcompat.api.FluidCompatTags;
import me.thanhtnt2.fluidcompat.api.FluidIntakeRequest;
import me.thanhtnt2.fluidcompat.api.FluidIntakeResult;
import me.thanhtnt2.fluidcompat.api.event.FluidIntakeEvent;
import me.thanhtnt2.fluidcompat.config.BackendPreference;
import me.thanhtnt2.fluidcompat.config.FluidCompatConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.MinecraftForge;

/** Default {@link FiniteFluidService}: applies the config, fires events and delegates to a backend. */
public final class FiniteFluidServiceImpl implements FiniteFluidService {

    @Override
    public boolean isFinite(Fluid fluid) {
        if (!FluidCompatConfig.ENABLE_FINITE_FLUIDS.get()) {
            return false;
        }
        if (fluid.is(FluidTags.WATER)) {
            return FluidCompatConfig.FINITE_WATER.get();
        }
        if (fluid.is(FluidTags.LAVA)) {
            return FluidCompatConfig.FINITE_LAVA.get();
        }
        return fluid.is(FluidCompatTags.MANAGED_FLUIDS) && FluidCompatConfig.FINITE_TAGGED_FLUIDS.get();
    }

    @Override
    public FluidIntakeResult requestIntake(FluidIntakeRequest request) {
        if (!isFinite(request.fluid())) {
            return FluidIntakeResult.unmanaged(request.requestedLevels());
        }

        FluidIntakeEvent.Pre preEvent = new FluidIntakeEvent.Pre(request);
        MinecraftForge.EVENT_BUS.post(preEvent);
        if (preEvent.isCanceled()) {
            return FluidIntakeResult.unmanaged(request.requestedLevels());
        }

        Optional<DrainBackend> backend = activeBackendFor(request.level(), request.fluid());
        if (backend.isEmpty()) {
            // Nobody can drain this fluid, so keeping the machine working beats silently breaking it
            return FluidIntakeResult.unmanaged(request.requestedLevels());
        }

        int levelsToDrain = preEvent.getRequestedLevels();
        int drainedLevels = drainAcrossSources(backend.get(), request, levelsToDrain);
        FluidIntakeResult result = new FluidIntakeResult(levelsToDrain, drainedLevels, true);
        MinecraftForge.EVENT_BUS.post(new FluidIntakeEvent.Post(request, result));
        return result;
    }

    @Override
    public Optional<DrainBackend> activeBackendFor(ServerLevel level, Fluid fluid) {
        BackendPreference preference = FluidCompatConfig.BACKEND_PREFERENCE.get();
        // A plain loop: this runs for every intake, and a stream with two lambdas allocates on each call
        for (DrainBackend backend : DrainBackendRegistry.snapshot()) {
            if (preference.accepts(backend.id()) && backend.supports(level, fluid)) {
                return Optional.of(backend);
            }
        }
        return Optional.empty();
    }

    private int drainAcrossSources(DrainBackend backend, FluidIntakeRequest request, int levelsToDrain) {
        int remaining = levelsToDrain;
        for (BlockPos sourcePos : request.sourcePositions()) {
            if (remaining <= 0) {
                break;
            }
            int removed = backend.drain(request.level(), sourcePos, request.fluid(), remaining);
            // Clamp so a misbehaving addon backend can never create fluid out of thin air
            remaining -= Math.max(0, Math.min(removed, remaining));
        }
        return levelsToDrain - remaining;
    }
}
