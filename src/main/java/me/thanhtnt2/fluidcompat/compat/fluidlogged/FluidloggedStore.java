package me.thanhtnt2.fluidcompat.compat.fluidlogged;

import java.lang.reflect.Method;

import me.thanhtnt2.fluidcompat.logging.FluidCompatLog;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * Stores water in a block through Fluidlogged's own level extension, for blocks that have no waterlogged state.
 *
 * <p>Fluidlogged adds {@code LevelExtension.setFluid(BlockPos, FluidState, int)} to every level with a mixin. It is
 * reached by name, so Fluid Compat has no compile-time link to Fluidlogged. If that is not as expected the store
 * switches itself off with one log line and only blocks with a waterlogged state are flooded.
 */
final class FluidloggedStore {

    private static final String EXTENSION_CLASS = "de.leximon.fluidlogged.mixin.extensions.LevelExtension";

    private static volatile Method setFluid;
    private static volatile boolean failed;

    private FluidloggedStore() {
    }

    static boolean isAvailable() {
        if (failed) {
            return false;
        }
        if (setFluid != null) {
            return true;
        }
        try {
            Class<?> extension = Class.forName(EXTENSION_CLASS, false, FluidloggedStore.class.getClassLoader());
            setFluid = extension.getMethod("setFluid", BlockPos.class, FluidState.class, int.class);
            return true;
        } catch (ReflectiveOperationException | LinkageError error) {
            fail(error);
            return false;
        }
    }

    /** @return true when Fluidlogged accepted the water; the caller still reads the cell back to be sure */
    static boolean storeWater(ServerLevel level, BlockPos pos) {
        if (!isAvailable()) {
            return false;
        }
        try {
            Object result = setFluid.invoke(level, pos, Fluids.WATER.getSource(false), 3);
            return !(result instanceof Boolean accepted) || accepted;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            fail(error);
            return false;
        }
    }

    private static void fail(Throwable error) {
        failed = true;
        FluidCompatLog.warn("[fluidlogged] storing water through Fluidlogged is switched off: {}", String.valueOf(error));
    }
}
