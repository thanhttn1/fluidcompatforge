package me.thanhtnt2.fluidcompat.compat.gtceu;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;

import me.thanhtnt2.fluidcompat.logging.FluidCompatLog;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/**
 * Reads the few things the leftover sweep needs from a GregTech Pump, by name, so Fluid Compat has no compile-time link to
 * GregTech: the pump head depth, whether its pump queue still has work, its output tank, its tier and its energy.
 * Everything is looked up once; if any of it is not as expected the access switches itself off with one log line and the
 * pump keeps working as GregTech made it.
 */
final class GtPumpAccess {

    private static final String GT_VALUES_CLASS = "com.gregtechceu.gtceu.api.GTValues";

    private final Field pumpQueue;
    private final Field cache;
    private final Field pumpHeadY;
    private final Field energyContainer;
    private final Method queueAccessor;
    private final Method fillInternal;
    private final Method getTier;
    private final Method getPos;
    private final Method getLevel;
    private final Method getEnergyStored;
    private final Method changeEnergy;
    private final long[] voltages;

    private GtPumpAccess(Class<?> machine) throws ReflectiveOperationException {
        pumpQueue = field(machine, "pumpQueue");
        cache = field(machine, "cache");
        pumpHeadY = field(machine, "pumpHeadY");
        energyContainer = field(machine, "energyContainer");
        queueAccessor = pumpQueue.getType().getMethod("queue");
        queueAccessor.setAccessible(true);
        fillInternal = cache.getType().getMethod("fillInternal", FluidStack.class, IFluidHandler.FluidAction.class);
        getTier = machine.getMethod("getTier");
        getPos = machine.getMethod("getPos");
        getLevel = machine.getMethod("getLevel");
        getEnergyStored = energyContainer.getType().getMethod("getEnergyStored");
        changeEnergy = energyContainer.getType().getMethod("changeEnergy", long.class);
        voltages = (long[]) Class.forName(GT_VALUES_CLASS, true, machine.getClassLoader()).getField("V").get(null);
    }

    private static volatile GtPumpAccess instance;
    private static volatile boolean failed;

    /** @return the access for this machine class, or null when GregTech is not shaped as expected */
    static GtPumpAccess of(Object machine) {
        if (failed) {
            return null;
        }
        GtPumpAccess ready = instance;
        if (ready != null) {
            return ready;
        }
        try {
            ready = new GtPumpAccess(machine.getClass());
            instance = ready;
            return ready;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            failed = true;
            FluidCompatLog.warn("[gtceu] pump leftover sweep switched off, the pump is not shaped as expected: {}", String.valueOf(error));
            return null;
        }
    }

    private static Field field(Class<?> start, String name) throws NoSuchFieldException {
        for (Class<?> type = start; type != null; type = type.getSuperclass()) {
            try {
                Field found = type.getDeclaredField(name);
                found.setAccessible(true);
                return found;
            } catch (NoSuchFieldException notHere) {
                // try the superclass
            }
        }
        throw new NoSuchFieldException(name);
    }

    BlockPos pos(Object machine) throws ReflectiveOperationException {
        return (BlockPos) getPos.invoke(machine);
    }

    Level level(Object machine) throws ReflectiveOperationException {
        return (Level) getLevel.invoke(machine);
    }

    int pumpHeadY(Object machine) throws ReflectiveOperationException {
        return pumpHeadY.getInt(machine);
    }

    /** @return true when the pump's search queue holds no work, which is when the original has nothing left to pump */
    boolean queueIsEmpty(Object machine) throws ReflectiveOperationException {
        Object queue = pumpQueue.get(machine);
        if (queue == null) {
            return true;
        }
        Object inner = queueAccessor.invoke(queue);
        return inner instanceof Collection<?> collection && collection.isEmpty();
    }

    int fill(Object machine, FluidStack stack, IFluidHandler.FluidAction action) throws ReflectiveOperationException {
        return (Integer) fillInternal.invoke(cache.get(machine), stack, action);
    }

    /** What one pump cycle costs in energy, as in GregTech's own code: twice the voltage of the pump's tier. */
    long cycleCost(Object machine) throws ReflectiveOperationException {
        return voltages[(Integer) getTier.invoke(machine)] * 2;
    }

    long energyStored(Object machine) throws ReflectiveOperationException {
        return (Long) getEnergyStored.invoke(energyContainer.get(machine));
    }

    void spendEnergy(Object machine, long amount) throws ReflectiveOperationException {
        try {
            changeEnergy.invoke(energyContainer.get(machine), -amount);
        } catch (InvocationTargetException error) {
            throw error;
        }
    }
}
