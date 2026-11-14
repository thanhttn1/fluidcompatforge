package me.thanhtnt2.fluidcompat.compat.mekanismelements.guard;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bytecode check for the Seawater Pump thin water hook.
 *
 * <p>In Mekanism Elements 1.20.1 the pump's {@code onUpdateServer()} decides whether it may run with one chain: the
 * biome must be an ocean, the cell below must satisfy {@code FluidState.isSource()} and its {@code getType()} must be
 * {@code Fluids.WATER}. The hook answers those two questions, so it is only right while each is asked exactly once and
 * the type is still compared with {@code Fluids.WATER}.
 *
 * <p>The hook names the two vanilla calls by their SRG ids, which is what a real game has, so those are checked. Pure ASM and
 * pure strings: nothing here loads Minecraft.
 */
public final class SeawaterWaterCheckShape {

    private static final Logger LOGGER = LoggerFactory.getLogger("FluidCompat/Mixin");

    private static final String UPDATE_NAME = "onUpdateServer";
    private static final String UPDATE_DESCRIPTOR = "()V";
    private static final String FLUID_STATE = "net/minecraft/world/level/material/FluidState";
    private static final String FLUIDS = "net/minecraft/world/level/material/Fluids";
    private static final String IS_SOURCE_SRG = "m_76170_";
    private static final String GET_TYPE_SRG = "m_76152_";
    private static final String WATER_MOJANG = "WATER";
    private static final String WATER_SRG = "f_76193_";

    private SeawaterWaterCheckShape() {
    }

    public static boolean isThinWaterHookable(ClassNode pump) {
        String problem = problem(pump);
        if (problem != null) {
            LOGGER.warn("[mekanism_elements_thin_water] guard refused the pump: {}", problem);
        }
        return problem == null;
    }

    /** @return what is missing, in words for the log, or null when the check chain has the expected shape */
    public static String problem(ClassNode pump) {
        MethodNode update = null;
        for (MethodNode method : pump.methods) {
            if (UPDATE_NAME.equals(method.name) && UPDATE_DESCRIPTOR.equals(method.desc)) {
                update = method;
            }
        }
        if (update == null) {
            return "no onUpdateServer()V";
        }
        int isSourceCalls = 0;
        int getTypeCalls = 0;
        int waterReads = 0;
        for (AbstractInsnNode instruction : update.instructions) {
            if (instruction instanceof MethodInsnNode call && FLUID_STATE.equals(call.owner)) {
                if (IS_SOURCE_SRG.equals(call.name)) {
                    isSourceCalls++;
                } else if (GET_TYPE_SRG.equals(call.name)) {
                    getTypeCalls++;
                }
            } else if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
                    && FLUIDS.equals(field.owner) && (WATER_MOJANG.equals(field.name) || WATER_SRG.equals(field.name))) {
                waterReads++;
            }
        }
        if (isSourceCalls != 1) {
            return "onUpdateServer asks FluidState.isSource " + isSourceCalls + " times, not once";
        }
        if (getTypeCalls != 1) {
            return "onUpdateServer asks FluidState.getType " + getTypeCalls + " times, not once";
        }
        if (waterReads != 1) {
            return "onUpdateServer reads Fluids.WATER " + waterReads + " times, not once";
        }
        return null;
    }
}
