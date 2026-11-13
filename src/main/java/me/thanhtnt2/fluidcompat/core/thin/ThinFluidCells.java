package me.thanhtnt2.fluidcompat.core.thin;

import java.util.List;

import me.thanhtnt2.fluidcompat.api.FiniteFluidService;
import me.thanhtnt2.fluidcompat.api.FluidCompatApi;
import me.thanhtnt2.fluidcompat.api.FluidIntakeRequest;
import me.thanhtnt2.fluidcompat.api.FluidIntakeResult;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

/**
 * The rule that makes a thin Flowing Fluids layer drainable, in one place.
 *
 * <p>Flowing Fluids drains a pond into partly filled blocks that Minecraft calls "flowing" and that no vanilla-style
 * pump will take, because pumps only look for full source blocks. Machines that walk the cells themselves (the
 * Immersive Engineering pump) can use the rule here: the unit is one cell, and the cell gives exactly the levels it
 * holds, paid through the normal intake so events, backend choice and clamping apply. Machines that cannot walk cells
 * use the sweep service instead.
 *
 * <p>Nothing here changes the world except through {@link FiniteFluidService#requestIntake}, so a thin cell can only
 * ever give up levels that are really in the world.
 */
public final class ThinFluidCells {

    private ThinFluidCells() {
    }

    /** @return the source variant of a flowing fluid, or the fluid itself; water and flowing water are one fluid to Fluid Compat */
    public static Fluid normalised(Fluid fluid) {
        return fluid instanceof FlowingFluid flowing ? flowing.getSource() : fluid;
    }

    /** @return true for a free-standing, partly filled cell of a fluid that Fluid Compat keeps finite */
    public static boolean isThinManagedCell(Level level, BlockPos pos) {
        if (!FluidCompatApi.isReady()) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        FluidState fluid = state.getFluidState();
        return state.getBlock() instanceof LiquidBlock
                && !fluid.isEmpty()
                && !fluid.isSource()
                && fluid.getAmount() >= 1
                && FluidCompatApi.service().isFinite(normalised(fluid.getType()));
    }

    /** @return the levels the cell holds (0 when it holds no free-standing fluid) */
    public static int levelsIn(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        FluidState fluid = state.getFluidState();
        return state.getBlock() instanceof LiquidBlock && !fluid.isEmpty() ? fluid.getAmount() : 0;
    }

    /**
     * Takes the fluid of one cell out of the world.
     *
     * @param machinePos where the machine is; {@code pos} is the cell it drains
     * @return the levels really removed, never more than {@code levels} and never more than the cell held
     */
    public static int drainCell(ServerLevel level, BlockPos machinePos, BlockPos pos, ResourceLocation machineId, int levels) {
        int inCell = Math.min(levels, levelsIn(level, pos));
        if (inCell <= 0 || !FluidCompatApi.isReady()) {
            return 0;
        }
        Fluid fluid = normalised(level.getFluidState(pos).getType());
        FluidIntakeResult result = FluidCompatApi.service().requestIntake(
                new FluidIntakeRequest(level, machinePos, machineId, fluid, inCell, List.of(pos)));
        return result.managed() ? Math.min(result.drainedLevels(), inCell) : 0;
    }
}
