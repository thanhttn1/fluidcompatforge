package me.thanhtnt2.fluidcompat.compat.mekanism.guard;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Bytecode check for the Mekanism Electric Pump hook.
 *
 * <p>In Mekanism 1.20.1 (10.4.x) the pump's method {@code suck(BlockPos, boolean, boolean)} contains this decision for
 * a source block of a fluid it can pick up:
 * <pre>
 *   if (fluid == Fluids.WATER &amp;&amp; !MekanismConfig.general.pumpWaterSources.get()) { skip the pickup }
 *   else { BucketPickup.pickupBlock(...) }
 * </pre>
 * The setting is off by default, so water is never taken out of the world and the pump re-reads the same block for
 * ever. The hook changes only the value that {@code Fluids.WATER} yields at that one comparison, so the pump falls
 * into the very branch it already uses for lava. This guard confirms, on the real bytecode, every assumption that
 * relies on: one read of {@code Fluids.WATER}, straight into an {@code if_acmpne}; the jump target leads into
 * {@code pickupBlock}; the fall-through contains the {@code pumpWaterSources} gate.
 *
 * <p>Pure ASM and pure strings: nothing here loads Minecraft or Mekanism.
 */
public final class ElectricPumpShape {

    /** {@code suck(BlockPos, boolean, boolean)}: the method that decides whether to take the source block. */
    public static final String SUCK_DESCRIPTOR = "(Lnet/minecraft/core/BlockPos;ZZ)Z";

    private static final String SUCK_NAME = "suck";
    private static final String FLUIDS_OWNER = "net/minecraft/world/level/material/Fluids";
    // Fluids.WATER: the Mojang name in the IDE, the SRG id in a real game (read from the Mekanism bytecode)
    private static final String WATER_NAMED = "WATER";
    private static final String WATER_SRG = "f_76193_";
    private static final String WATER_DESCRIPTOR = "Lnet/minecraft/world/level/material/FlowingFluid;";
    private static final String CACHED_BOOLEAN_OWNER = "mekanism/common/config/value/CachedBooleanValue";
    private static final String GENERAL_CONFIG_OWNER = "mekanism/common/config/GeneralConfig";
    private static final String PUMP_WATER_SOURCES_FIELD = "pumpWaterSources";
    private static final String BUCKET_PICKUP_OWNER = "net/minecraft/world/level/block/BucketPickup";
    // BucketPickup.pickupBlock: the Mojang name in the IDE, the SRG id in a real game
    private static final String PICKUP_BLOCK_NAMED = "pickupBlock";
    private static final String PICKUP_BLOCK_SRG = "m_142598_";

    private ElectricPumpShape() {
    }

    public static boolean waterComparisonLeadsIntoPickup(ClassNode pump) {
        MethodNode suck = findSuck(pump);
        if (suck == null) {
            return false;
        }
        AbstractInsnNode[] instructions = suck.instructions.toArray();
        int waterReadIndex = indexOfOnlyWaterRead(instructions);
        if (waterReadIndex < 0) {
            return false;
        }
        JumpInsnNode comparison = comparisonAfter(instructions, waterReadIndex);
        if (comparison == null) {
            return false;
        }
        int jumpTargetIndex = suck.instructions.indexOf(comparison.label);
        int comparisonIndex = indexOf(instructions, comparison);
        return jumpTargetLeadsIntoPickup(instructions, jumpTargetIndex)
                && fallThroughHasWaterSourcesGate(instructions, comparisonIndex, jumpTargetIndex);
    }

    private static MethodNode findSuck(ClassNode pump) {
        for (MethodNode method : pump.methods) {
            if (SUCK_NAME.equals(method.name) && SUCK_DESCRIPTOR.equals(method.desc)) {
                return method;
            }
        }
        return null;
    }

    /** @return the index of the single read of {@code Fluids.WATER}, or -1 when there are none or several */
    private static int indexOfOnlyWaterRead(AbstractInsnNode[] instructions) {
        int foundIndex = -1;
        for (int index = 0; index < instructions.length; index++) {
            if (!isWaterRead(instructions[index])) {
                continue;
            }
            if (foundIndex >= 0) {
                return -1; // A second read would make the redirect ordinal ambiguous
            }
            foundIndex = index;
        }
        return foundIndex;
    }

    private static boolean isWaterRead(AbstractInsnNode instruction) {
        return instruction instanceof FieldInsnNode field
                && field.getOpcode() == Opcodes.GETSTATIC
                && FLUIDS_OWNER.equals(field.owner)
                && (WATER_NAMED.equals(field.name) || WATER_SRG.equals(field.name))
                && WATER_DESCRIPTOR.equals(field.desc);
    }

    // "fluid == WATER?": a local was pushed first and the very next instruction compares the two references
    private static JumpInsnNode comparisonAfter(AbstractInsnNode[] instructions, int waterReadIndex) {
        AbstractInsnNode previous = realInstruction(instructions, waterReadIndex, -1);
        AbstractInsnNode next = realInstruction(instructions, waterReadIndex, 1);
        boolean fluidWasLoaded = previous != null && previous.getOpcode() == Opcodes.ALOAD;
        boolean isNotEqualJump = next instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IF_ACMPNE;
        return fluidWasLoaded && isNotEqualJump ? (JumpInsnNode) next : null;
    }

    // The target of "not water" must reach pickupBlock before any other call, only loading its arguments on the way
    private static boolean jumpTargetLeadsIntoPickup(AbstractInsnNode[] instructions, int jumpTargetIndex) {
        if (jumpTargetIndex < 0) {
            return false;
        }
        for (int index = jumpTargetIndex; index < instructions.length; index++) {
            AbstractInsnNode instruction = instructions[index];
            if (instruction.getOpcode() < 0 || isArgumentLoad(instruction)) {
                continue;
            }
            return instruction instanceof MethodInsnNode call && isPickupBlock(call);
        }
        return false;
    }

    private static boolean isArgumentLoad(AbstractInsnNode instruction) {
        return instruction.getOpcode() == Opcodes.ALOAD || instruction.getOpcode() == Opcodes.GETFIELD;
    }

    // Between the comparison and its target: "pumpWaterSources.get()" followed by "if false, skip the pickup"
    private static boolean fallThroughHasWaterSourcesGate(AbstractInsnNode[] instructions, int fromIndex, int toIndex) {
        boolean readsSetting = false;
        boolean readsValue = false;
        boolean skipsWhenFalse = false;
        for (int index = fromIndex + 1; index < toIndex && index < instructions.length; index++) {
            AbstractInsnNode instruction = instructions[index];
            readsSetting |= isPumpWaterSourcesField(instruction);
            readsValue |= isCachedBooleanGet(instruction);
            skipsWhenFalse |= instruction.getOpcode() == Opcodes.IFEQ;
        }
        return readsSetting && readsValue && skipsWhenFalse;
    }

    private static boolean isPumpWaterSourcesField(AbstractInsnNode instruction) {
        return instruction instanceof FieldInsnNode field
                && field.getOpcode() == Opcodes.GETFIELD
                && GENERAL_CONFIG_OWNER.equals(field.owner)
                && PUMP_WATER_SOURCES_FIELD.equals(field.name);
    }

    private static boolean isCachedBooleanGet(AbstractInsnNode instruction) {
        return instruction instanceof MethodInsnNode call
                && CACHED_BOOLEAN_OWNER.equals(call.owner)
                && "get".equals(call.name)
                && "()Z".equals(call.desc);
    }

    private static boolean isPickupBlock(MethodInsnNode call) {
        return BUCKET_PICKUP_OWNER.equals(call.owner)
                && (PICKUP_BLOCK_NAMED.equals(call.name) || PICKUP_BLOCK_SRG.equals(call.name));
    }

    private static int indexOf(AbstractInsnNode[] instructions, AbstractInsnNode wanted) {
        for (int index = 0; index < instructions.length; index++) {
            if (instructions[index] == wanted) {
                return index;
            }
        }
        return -1;
    }

    private static AbstractInsnNode realInstruction(AbstractInsnNode[] instructions, int fromIndex, int step) {
        for (int index = fromIndex + step; index >= 0 && index < instructions.length; index += step) {
            if (instructions[index].getOpcode() >= 0) {
                return instructions[index];
            }
        }
        return null;
    }
}
