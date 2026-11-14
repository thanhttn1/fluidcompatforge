package me.thanhtnt2.fluidcompat.compat.immersiveengineering.guard;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The check for the Fluid Pump cobblestone hook, stricter than the others because a wrong match would silence a call
 * that is not the cobblestone.
 *
 * <p>In {@code tickServer()} the pump must still do exactly this, in this order: simulate the drain of the last block of
 * its work list, check the tank, drain it for real, put cobblestone there (one {@code Level.setBlockAndUpdate}, whose
 * block comes from a {@code Blocks.COBBLESTONE} read a few instructions earlier), then fill the tank. The hook remembers
 * the fluid just before the second drain call and decides at the cobblestone call, so both calls must exist once in that
 * order, and the fields it reads ({@code closedList}, {@code searchFluid}) must have the types it expects.
 *
 * <p>The vanilla call is matched by its SRG id only, because that is what the hook names and what a real game has. Pure
 * ASM and pure strings: nothing here loads Minecraft.
 */
public final class CobbleContract {

    private static final Logger LOGGER = LoggerFactory.getLogger("FluidCompat/Mixin");

    private static final String IE_UTILS = "blusunrize/immersiveengineering/common/util/Utils";
    private static final String LEVEL = "net/minecraft/world/level/Level";
    private static final String BLOCKS = "net/minecraft/world/level/block/Blocks";
    private static final String SET_BLOCK_SRG = "m_46597_";
    private static final String COBBLESTONE_SRG = "f_50652_";
    private static final String FLUID_TANK = "net/minecraftforge/fluids/capability/templates/FluidTank";
    // How far before the setBlock call the cobblestone read may be (it is followed by defaultBlockState and a few loads)
    private static final int COBBLESTONE_WINDOW = 10;

    private CobbleContract() {
    }

    public static boolean isHookable(ClassNode pump) {
        String problem = problem(pump);
        if (problem != null) {
            LOGGER.warn("[immersiveengineering_cobble] guard refused the Fluid Pump, the cobblestone hook stays off: {}", problem);
        }
        return problem == null;
    }

    /** @return what does not match, in words for the log, or null when the pump has the shape the hook expects */
    public static String problem(ClassNode pump) {
        MethodNode tick = null;
        for (MethodNode method : pump.methods) {
            if ("tickServer".equals(method.name) && "()V".equals(method.desc)) {
                tick = method;
            }
        }
        if (tick == null) {
            return "no tickServer()V";
        }
        boolean closedListOk = pump.fields.stream().anyMatch(field -> "closedList".equals(field.name) && "Ljava/util/List;".equals(field.desc));
        boolean searchFluidOk = pump.fields.stream().anyMatch(field -> "searchFluid".equals(field.name)
                && "Lnet/minecraft/world/level/material/Fluid;".equals(field.desc));
        if (!closedListOk || !searchFluidOk) {
            return "closedList (List) or searchFluid (Fluid) is missing or has another type";
        }
        int[] drains = new int[2];
        int drainCount = 0;
        int setBlockIndex = -1;
        int setBlockCount = 0;
        int cobblestoneIndex = -1;
        int firstFill = -1;
        int lastFill = -1;
        int index = 0;
        for (AbstractInsnNode instruction : tick.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                if (IE_UTILS.equals(call.owner) && "drainFluidBlock".equals(call.name)) {
                    if (drainCount < 2) {
                        drains[drainCount] = index;
                    }
                    drainCount++;
                } else if (LEVEL.equals(call.owner) && SET_BLOCK_SRG.equals(call.name)) {
                    setBlockIndex = index;
                    setBlockCount++;
                } else if (FLUID_TANK.equals(call.owner) && "fill".equals(call.name)) {
                    firstFill = firstFill < 0 ? index : firstFill;
                    lastFill = index;
                }
            } else if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC
                    && BLOCKS.equals(field.owner) && COBBLESTONE_SRG.equals(field.name)) {
                cobblestoneIndex = index;
            }
            index++;
        }
        if (drainCount != 2) {
            return "tickServer calls Utils.drainFluidBlock " + drainCount + " times, the hook expects 2";
        }
        if (setBlockCount != 1) {
            return "tickServer calls Level.setBlockAndUpdate (m_46597_) " + setBlockCount + " times, the hook expects 1";
        }
        if (cobblestoneIndex < 0 || setBlockIndex - cobblestoneIndex < 0 || setBlockIndex - cobblestoneIndex > COBBLESTONE_WINDOW) {
            return "the setBlockAndUpdate call is not fed by Blocks.COBBLESTONE";
        }
        if (!(drains[0] < firstFill && firstFill < drains[1] && drains[1] < setBlockIndex && setBlockIndex < lastFill)) {
            return "the order drain (simulate), tank, drain (execute), cobblestone, tank fill is not the pump's order any more";
        }
        return null;
    }
}
