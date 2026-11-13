package me.thanhtnt2.fluidcompat.compat.gtceu.guard;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bytecode check for the GregTech Pump leftover sweep.
 *
 * <p>The sweep runs after {@code PumpMachine.pumpCycle()} and reads the pump head depth, the pump queue, the output tank
 * and the energy container by name. It is only right while the pump still has that shape: {@code pumpCycle()} exists and
 * is called by {@code update()}, the four fields exist, and the cycle still ends by charging energy. Pure ASM and pure
 * strings: nothing here loads Minecraft.
 */
public final class PumpMachineShape {

    private static final Logger LOGGER = LoggerFactory.getLogger("FluidCompat/Mixin");

    private PumpMachineShape() {
    }

    public static boolean isSweepable(ClassNode pump) {
        String problem = problem(pump);
        if (problem != null) {
            LOGGER.warn("[gtceu_pump] guard refused the GregTech Pump: {}", problem);
        }
        return problem == null;
    }

    /** @return what is missing, in words for the log, or null when the pump has the expected shape */
    public static String problem(ClassNode pump) {
        MethodNode cycle = find(pump, "pumpCycle", "()V");
        MethodNode update = find(pump, "update", "()V");
        if (cycle == null) {
            return "no pumpCycle()V";
        }
        if (update == null || !calls(update, pump.name, "pumpCycle")) {
            return "update() does not call pumpCycle()";
        }
        for (String field : new String[] {"pumpQueue", "cache", "pumpHeadY"}) {
            if (pump.fields.stream().noneMatch(declared -> field.equals(declared.name))) {
                return "no field " + field;
            }
        }
        if (!calls(cycle, "com/gregtechceu/gtceu/api/machine/trait/NotifiableEnergyContainer", "changeEnergy")) {
            return "pumpCycle() no longer charges energy";
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

    private static boolean calls(MethodNode caller, String owner, String name) {
        for (AbstractInsnNode instruction : caller.instructions) {
            if (instruction instanceof MethodInsnNode call && owner.equals(call.owner) && name.equals(call.name)) {
                return true;
            }
        }
        return false;
    }
}
