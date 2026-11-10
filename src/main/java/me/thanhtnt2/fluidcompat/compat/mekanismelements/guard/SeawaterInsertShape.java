package me.thanhtnt2.fluidcompat.compat.mekanismelements.guard;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Bytecode check for the Seawater Pump hook.
 *
 * <p>In Mekanism Elements 1.20.1 the pump's {@code onUpdateServer()} makes seawater out of nothing with exactly one
 * call, {@code fluidTank.insert(SEAWATER_STACK, Action.EXECUTE, AutomationType.INTERNAL)}. The hook replaces the first
 * argument of that call with what the world could pay for. It is only correct while that call exists exactly once and
 * its first argument is still the pump's seawater stack, which is what this guard confirms.
 *
 * <p>Pure ASM and pure strings: nothing here loads Minecraft or Mekanism.
 */
public final class SeawaterInsertShape {

    public static final String UPDATE_DESCRIPTOR = "()V";

    private static final String UPDATE_NAME = "onUpdateServer";
    private static final String TANK_OWNER = "mekanism/common/capabilities/fluid/BasicFluidTank";
    private static final String INSERT_DESCRIPTOR =
            "(Lnet/minecraftforge/fluids/FluidStack;Lmekanism/api/Action;Lmekanism/api/AutomationType;)Lnet/minecraftforge/fluids/FluidStack;";
    private static final String SEAWATER_FIELD = "SEAWATER_STACK";
    private static final String ACTION_OWNER = "mekanism/api/Action";
    private static final String AUTOMATION_OWNER = "mekanism/api/AutomationType";

    private SeawaterInsertShape() {
    }

    public static boolean insertsOnlyTheSeawaterStack(ClassNode pump) {
        MethodNode update = findUpdate(pump);
        if (update == null) {
            return false;
        }
        AbstractInsnNode[] instructions = update.instructions.toArray();
        int insertIndex = indexOfOnlyInsert(instructions);
        return insertIndex >= 0 && argumentsAreSeawaterExecuteInternal(instructions, insertIndex);
    }

    private static MethodNode findUpdate(ClassNode pump) {
        for (MethodNode method : pump.methods) {
            if (UPDATE_NAME.equals(method.name) && UPDATE_DESCRIPTOR.equals(method.desc)) {
                return method;
            }
        }
        return null;
    }

    /** @return the index of the single tank insert, or -1 when there are none or several */
    private static int indexOfOnlyInsert(AbstractInsnNode[] instructions) {
        int foundIndex = -1;
        for (int index = 0; index < instructions.length; index++) {
            if (!isTankInsert(instructions[index])) {
                continue;
            }
            if (foundIndex >= 0) {
                return -1;
            }
            foundIndex = index;
        }
        return foundIndex;
    }

    private static boolean isTankInsert(AbstractInsnNode instruction) {
        return instruction instanceof MethodInsnNode call
                && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                && TANK_OWNER.equals(call.owner)
                && "insert".equals(call.name)
                && INSERT_DESCRIPTOR.equals(call.desc);
    }

    // The three values pushed right before the call, in order: the stack, Action.EXECUTE, AutomationType.INTERNAL
    private static boolean argumentsAreSeawaterExecuteInternal(AbstractInsnNode[] instructions, int insertIndex) {
        AbstractInsnNode automation = realInstructionBefore(instructions, insertIndex);
        AbstractInsnNode action = automation == null ? null : realInstructionBefore(instructions, indexOf(instructions, automation));
        AbstractInsnNode stack = action == null ? null : realInstructionBefore(instructions, indexOf(instructions, action));
        return isStatic(automation, AUTOMATION_OWNER, "INTERNAL")
                && isStatic(action, ACTION_OWNER, "EXECUTE")
                && stack instanceof FieldInsnNode field
                && field.getOpcode() == Opcodes.GETSTATIC
                && SEAWATER_FIELD.equals(field.name);
    }

    private static boolean isStatic(AbstractInsnNode instruction, String owner, String name) {
        return instruction instanceof FieldInsnNode field
                && field.getOpcode() == Opcodes.GETSTATIC
                && owner.equals(field.owner)
                && name.equals(field.name);
    }

    private static int indexOf(AbstractInsnNode[] instructions, AbstractInsnNode wanted) {
        for (int index = 0; index < instructions.length; index++) {
            if (instructions[index] == wanted) {
                return index;
            }
        }
        return -1;
    }

    private static AbstractInsnNode realInstructionBefore(AbstractInsnNode[] instructions, int index) {
        for (int previous = index - 1; previous >= 0; previous--) {
            if (instructions[previous].getOpcode() >= 0) {
                return instructions[previous];
            }
        }
        return null;
    }
}
