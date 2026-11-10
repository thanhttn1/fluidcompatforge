package me.thanhtnt2.fluidcompat.compat.alexscaves.guard;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bytecode check for the Drain hook.
 *
 * <p>The hook replaces {@code DrainBlock.drainLogic(BlockState, Level, BlockPos)} while the fluid is finite. It models
 * the original as "search the fluid above, remove it, pour it out below, tick again". That model is only right while
 * the original still has exactly those parts, which is what this guard confirms: the method, its two helpers (the
 * removal search and the pour search) and the scheduled follow-up tick it ends with.
 *
 * <p>Alex's Caves is not obfuscated, so only the vanilla call to {@code scheduleTick} needs both spellings (Forge 1.20.1
 * runs with SRG names, the IDE shows Mojang names). Pure ASM and pure strings: nothing here loads Minecraft.
 */
public final class DrainLogicShape {

    private static final Logger LOGGER = LoggerFactory.getLogger("FluidCompat/Mixin");

    private static final String LOGIC_NAME = "drainLogic";
    private static final String LOGIC_DESCRIPTOR =
            "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V";
    private static final String REMOVE_SEARCH_NAME = "removeWaterBreadthFirstSearch";
    private static final String REMOVE_SEARCH_DESCRIPTOR = "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)I";
    private static final String POUR_SEARCH_NAME = "getFirstEmptyNeighborPosition";
    private static final String SCHEDULE_TICK_MOJANG = "scheduleTick";
    private static final String SCHEDULE_TICK_SRG = "m_186460_";
    private static final String SCHEDULE_TICK_DESCRIPTOR = "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;I)V";

    private DrainLogicShape() {
    }

    public static boolean isReplaceable(ClassNode drainBlock) {
        String problem = problem(drainBlock);
        if (problem != null) {
            LOGGER.warn("[alexscaves_drain] guard refused the Drain: {}", problem);
        }
        return problem == null;
    }

    /** @return what is missing, in words for the log, or null when the class has the expected shape */
    public static String problem(ClassNode drainBlock) {
        MethodNode logic = find(drainBlock, LOGIC_NAME, LOGIC_DESCRIPTOR);
        if (logic == null) {
            return "no drainLogic(BlockState, Level, BlockPos)";
        }
        if (!callsOwnMethod(logic, drainBlock, REMOVE_SEARCH_NAME, REMOVE_SEARCH_DESCRIPTOR)) {
            return "drainLogic no longer calls " + REMOVE_SEARCH_NAME;
        }
        if (!callsOwnMethod(logic, drainBlock, POUR_SEARCH_NAME, null)) {
            return "drainLogic no longer calls " + POUR_SEARCH_NAME;
        }
        if (!callsScheduleTick(logic)) {
            return "drainLogic no longer schedules its next tick";
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

    private static boolean callsOwnMethod(MethodNode caller, ClassNode owner, String name, String descriptor) {
        for (AbstractInsnNode instruction : caller.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && owner.name.equals(call.owner)
                    && name.equals(call.name)
                    && (descriptor == null || descriptor.equals(call.desc))) {
                return true;
            }
        }
        return false;
    }

    private static boolean callsScheduleTick(MethodNode caller) {
        for (AbstractInsnNode instruction : caller.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && SCHEDULE_TICK_DESCRIPTOR.equals(call.desc)
                    && (SCHEDULE_TICK_MOJANG.equals(call.name) || SCHEDULE_TICK_SRG.equals(call.name))) {
                return true;
            }
        }
        return false;
    }
}
