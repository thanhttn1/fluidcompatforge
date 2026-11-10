package me.thanhtnt2.fluidcompat.compat.mekanismelements;

import java.util.List;

import me.thanhtnt2.fluidcompat.api.FiniteFluidService;
import me.thanhtnt2.fluidcompat.api.FluidCompatApi;
import me.thanhtnt2.fluidcompat.api.FluidIntakeRequest;
import me.thanhtnt2.fluidcompat.api.FluidIntakeResult;
import me.thanhtnt2.fluidcompat.compat.CompatMixinState;
import me.thanhtnt2.fluidcompat.compat.common.CompatGate;
import me.thanhtnt2.fluidcompat.config.FluidCompatModulesConfig;
import me.thanhtnt2.fluidcompat.config.ShortfallBehavior;
import me.thanhtnt2.fluidcompat.logging.FluidCompatLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

/**
 * Charges the world for the seawater a Mekanism Elements Seawater Pump makes. Called from the mixin right before the
 * pump inserts its product into its tank. Has no reference to Mekanism or Mekanism Elements classes.
 */
public final class SeawaterPumpBridge {

    // The pump only works with a water source directly below it
    private static final Fluid CONSUMED_FLUID = Fluids.WATER;
    private static final String HOOK_ID = "mekanismelements.seawater_pump";

    /**
     * @param stack      what the pump may insert instead of the stack it wanted to (possibly empty)
     * @param paidAtTick the game tick of a paid intake, or -1 when the world was not charged this time
     */
    public record Charge(FluidStack stack, long paidAtTick) {
    }

    private SeawaterPumpBridge() {
    }

    public static Charge chargeWorld(BlockEntity pump, FluidStack producedSeawater, long lastIntakeTick) {
        try {
            return charge(pump, producedSeawater, lastIntakeTick);
        } catch (RuntimeException | LinkageError error) {
            // Fail open: a broken hook must never stop the pump from working
            FluidCompatLog.errorThrottled("seawater-pump",
                    "Seawater pump hook failed at " + pump.getBlockPos().toShortString(), error);
            return unchanged(producedSeawater);
        }
    }

    private static Charge charge(BlockEntity pump, FluidStack producedSeawater, long lastIntakeTick) {
        if (!(pump.getLevel() instanceof ServerLevel level) || producedSeawater.isEmpty() || !FluidCompatApi.isReady()) {
            return unchanged(producedSeawater);
        }
        if (!CompatGate.masterEnabled() || !FluidCompatModulesConfig.SEAWATER_PUMP_ENABLED.get()) {
            return unchanged(producedSeawater);
        }
        FiniteFluidService service = FluidCompatApi.service();
        if (!service.isFinite(CONSUMED_FLUID)) {
            return unchanged(producedSeawater);
        }
        long gameTime = level.getGameTime();
        if (isCoolingDown(gameTime, lastIntakeTick, FluidCompatModulesConfig.SEAWATER_MIN_TICKS_BETWEEN_INTAKES.get())) {
            return new Charge(FluidStack.EMPTY, -1L);
        }
        FluidIntakeResult result = service.requestIntake(buildRequest(level, pump, producedSeawater));
        reportFirstUse(result);
        if (!result.managed() || result.requestedLevels() <= 0) {
            return unchanged(producedSeawater);
        }
        return new Charge(applyShortfall(producedSeawater, result), result.drainedLevels() > 0 ? gameTime : -1L);
    }

    private static FluidIntakeRequest buildRequest(ServerLevel level, BlockEntity pump, FluidStack producedSeawater) {
        int producedLevels = ceilDiv(producedSeawater.getAmount(), FluidCompatApi.MILLIBUCKETS_PER_LEVEL);
        int percent = FluidCompatModulesConfig.SEAWATER_DRAIN_PERCENT.get();
        int levelsToDrain = ceilDiv(producedLevels * percent, 100);
        BlockPos pumpPos = pump.getBlockPos();
        List<BlockPos> sources = List.of(pumpPos.below());
        return new FluidIntakeRequest(level, pumpPos, machineIdOf(pump), CONSUMED_FLUID, levelsToDrain, sources);
    }

    private static FluidStack applyShortfall(FluidStack producedSeawater, FluidIntakeResult result) {
        if (result.isFullyServed()) {
            return producedSeawater;
        }
        if (FluidCompatModulesConfig.SEAWATER_SHORTFALL.get() == ShortfallBehavior.STOP || result.drainedLevels() <= 0) {
            return FluidStack.EMPTY;
        }
        // Scale down proportionally so a half-paid cycle yields half the seawater
        long scaledAmount = (long) producedSeawater.getAmount() * result.drainedLevels() / result.requestedLevels();
        return scaledAmount <= 0L ? FluidStack.EMPTY : new FluidStack(producedSeawater, (int) scaledAmount);
    }

    private static boolean isCoolingDown(long gameTime, long lastIntakeTick, int minTicksBetweenIntakes) {
        if (minTicksBetweenIntakes <= 0 || lastIntakeTick < 0L) {
            return false;
        }
        return gameTime - lastIntakeTick < minTicksBetweenIntakes;
    }

    // Math.ceilDiv only exists from Java 18, and this mod targets Java 17
    private static int ceilDiv(int dividend, int divisor) {
        return (dividend + divisor - 1) / divisor;
    }

    private static Charge unchanged(FluidStack producedSeawater) {
        return new Charge(producedSeawater, -1L);
    }

    private static ResourceLocation machineIdOf(BlockEntity pump) {
        ResourceLocation key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(pump.getType());
        return key != null ? key : ResourceLocation.fromNamespaceAndPath(FluidCompatApi.MOD_ID, "unknown_seawater_pump");
    }

    private static void reportFirstUse(FluidIntakeResult result) {
        if (!CompatMixinState.markHookReached(HOOK_ID)) {
            return;
        }
        FluidCompatLog.info("[mekanism_elements] Seawater Pump hook reached for the first time: managed={}, drained {} of {} levels",
                result.managed(), result.drainedLevels(), result.requestedLevels());
    }
}
