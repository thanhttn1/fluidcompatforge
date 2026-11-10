package me.thanhtnt2.fluidcompat.compat.mekanism.guard;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bytecode check for the Electric Pump leftover sweep.
 *
 * <p>The sweep hangs on the pump's private search: {@code suck()} is called once per work cycle from
 * {@code onUpdateServer}, tries the block below and the remembered nodes through
 * {@code suck(BlockPos, boolean, boolean)}, and answers false when it found nothing. The sweep runs at that false,
 * borrows the pump's {@code getOutput(Fluid, boolean)} for the per-bucket yield and hands the result to the pump's
 * own {@code suck(FluidStack, BlockPos, boolean)}, which puts it into the tank. The guard confirms that this whole
 * chain still exists, so a Mekanism update that reshapes the pump switches the sweep off instead of breaking it.
 *
 * <p>Pure ASM and pure strings: nothing here loads Minecraft or Mekanism.
 */
public final class ElectricPumpSweepShape {

    private static final Logger LOGGER = LoggerFactory.getLogger("FluidCompat/Mixin");

    private static final String PUMP_OWNER = "mekanism/common/tile/machine/TileEntityElectricPump";
    // The tank may be typed as BasicFluidTank or as one of Mekanism's tank interfaces; any Mekanism owner counts
    private static final String TANK_OWNER_PREFIX = "mekanism/";

    private static final String SEARCH_NAME = "suck";
    private static final String SEARCH_DESCRIPTOR = "()Z";
    private static final String SOURCE_CHECK_DESCRIPTOR = "(Lnet/minecraft/core/BlockPos;ZZ)Z";
    private static final String TANK_FILL_DESCRIPTOR = "(Lnet/minecraftforge/fluids/FluidStack;Lnet/minecraft/core/BlockPos;Z)V";
    private static final String OUTPUT_NAME = "getOutput";
    private static final String OUTPUT_DESCRIPTOR = "(Lnet/minecraft/world/level/material/Fluid;Z)Lnet/minecraftforge/fluids/FluidStack;";
    private static final String UPDATE_NAME = "onUpdateServer";
    private static final String UPDATE_DESCRIPTOR = "()V";

    private ElectricPumpSweepShape() {
    }

    public static boolean sweepChainIsIntact(ClassNode pump) {
        String problem = problem(pump);
        if (problem != null) {
            // The shared plugin only says "changed shape"; this says which part of the chain is missing
            LOGGER.warn("[mekanism_sweep] guard refused the pump: {}", problem);
        }
        return problem == null;
    }

    /** @return the first missing part of the chain, in words for the log, or null when it is intact */
    static String problem(ClassNode pump) {
        MethodNode search = find(pump, SEARCH_NAME, SEARCH_DESCRIPTOR);
        MethodNode update = find(pump, UPDATE_NAME, UPDATE_DESCRIPTOR);
        MethodNode tankFill = find(pump, SEARCH_NAME, TANK_FILL_DESCRIPTOR);
        MethodNode output = find(pump, OUTPUT_NAME, OUTPUT_DESCRIPTOR);
        if (search == null) {
            return "no " + SEARCH_NAME + SEARCH_DESCRIPTOR;
        }
        if (update == null) {
            return "no " + UPDATE_NAME + UPDATE_DESCRIPTOR;
        }
        if (tankFill == null) {
            return "no " + SEARCH_NAME + TANK_FILL_DESCRIPTOR;
        }
        if (output == null) {
            return "no " + OUTPUT_NAME + OUTPUT_DESCRIPTOR;
        }
        if (!callsPumpMethod(update, SEARCH_NAME, SEARCH_DESCRIPTOR)) {
            return UPDATE_NAME + " does not call " + SEARCH_NAME + SEARCH_DESCRIPTOR;
        }
        if (!callsPumpMethod(search, SEARCH_NAME, SOURCE_CHECK_DESCRIPTOR)) {
            return SEARCH_NAME + SEARCH_DESCRIPTOR + " does not call " + SEARCH_NAME + SOURCE_CHECK_DESCRIPTOR;
        }
        if (find(pump, SEARCH_NAME, SOURCE_CHECK_DESCRIPTOR) == null) {
            return "no " + SEARCH_NAME + SOURCE_CHECK_DESCRIPTOR;
        }
        if (!insertsIntoTank(tankFill)) {
            return SEARCH_NAME + TANK_FILL_DESCRIPTOR + " has no insert call on the tank";
        }
        return null;
    }

    private static MethodNode find(ClassNode owner, String name, String descriptor) {
        for (MethodNode method : owner.methods) {
            if (name.equals(method.name) && descriptor.equals(method.desc)) {
                return method;
            }
        }
        return null;
    }

    private static boolean callsPumpMethod(MethodNode caller, String name, String descriptor) {
        for (AbstractInsnNode instruction : caller.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && (call.getOpcode() == Opcodes.INVOKEVIRTUAL || call.getOpcode() == Opcodes.INVOKESPECIAL)
                    && PUMP_OWNER.equals(call.owner)
                    && name.equals(call.name)
                    && descriptor.equals(call.desc)) {
                return true;
            }
        }
        return false;
    }

    // The sweep trusts this method to put the stack into the tank, so it has to still do that
    private static boolean insertsIntoTank(MethodNode tankFill) {
        for (AbstractInsnNode instruction : tankFill.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.startsWith(TANK_OWNER_PREFIX) && "insert".equals(call.name)) {
                return true;
            }
        }
        return false;
    }
}
