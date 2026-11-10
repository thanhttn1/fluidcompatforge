package me.thanhtnt2.fluidcompat.compat.flowingfluids.patches.guard;

import me.thanhtnt2.fluidcompat.compat.flowingfluids.patches.guard.ObfNames.Name;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Bytecode checks that say whether a Flowing Fluids method still has the exact flaw a patch repairs.
 *
 * <p>Each check answers "is the bug still there, in the shape the patch expects?". When Flowing Fluids fixes a bug
 * itself, or reshapes the method, the answer turns false and the patch switches itself off, so a fixed version is
 * never patched twice. Pure ASM and plain strings: nothing here loads Flowing Fluids or Minecraft.
 */
public final class FlowingFluidsShapes {

    private static final String NOTIFY_LISTENERS = "notifyListeners";
    private static final String NOTIFY_LISTENERS_DESC =
            "(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/material/Fluid;Z)V";
    private static final String GET_BLOCK_ENTITY_DESC =
            "(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;";
    private static final String ACCEPT_RECENT_FLOW = "ff$acceptRecentFlow";
    private static final String ACCEPT_RECENT_FLOW_DESC =
            "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/material/Fluid;Z)V";

    private static final String REMOVE_ALL_FLUID = "removeAllFluidAtPos";
    private static final String REMOVE_ALL_FLUID_DESC =
            "(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/material/Fluid;)Z";

    private static final String NEIGHBOR_CHANGED_DESC =
            "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/core/BlockPos;Z)V";
    private static final String ON_PLACE_DESC =
            "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)V";

    private static final String REMOVE_FLUID = "removeFluidAmountFromPos";
    private static final String REMOVE_FLUID_DESC =
            "(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/material/Fluid;II)I";
    private static final String COLLECT_AND_REMOVE = "collectConnectedFluidAmountAndRemove";
    // Local variable slots of the instance method removeFluidAmountFromPos: this, level, pos, fluid, min, max
    private static final int MAX_AMOUNT_SLOT = 5;
    // Local variable slot of the fluid position in the static method notifyListeners: level, fluidPos, ...
    private static final int FLUID_POS_SLOT = 1;

    private FlowingFluidsShapes() {
    }

    /**
     * True while {@code FFFluidUtils.notifyListeners} asks for the block entity at the position of the fluid cell
     * (local variable 1) instead of at the position of the listener it found in the map, and still hands the result
     * to {@code IFFFlowListener.ff$acceptRecentFlow}.
     */
    public static boolean flowListenerLookupIsBroken(ClassNode fluidUtils) {
        MethodNode method = find(fluidUtils, NOTIFY_LISTENERS, NOTIFY_LISTENERS_DESC);
        if (method == null) {
            return false;
        }
        int blockEntityLookups = 0;
        boolean lookupUsesFluidPos = false;
        boolean callsListener = false;
        AbstractInsnNode previous = null;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction.getOpcode() < 0) {
                continue; // labels, line numbers and frames are not real instructions
            }
            if (instruction instanceof MethodInsnNode call) {
                if (ObfNames.GET_BLOCK_ENTITY.matches(call.name) && GET_BLOCK_ENTITY_DESC.equals(call.desc)) {
                    blockEntityLookups++;
                    lookupUsesFluidPos = previous instanceof VarInsnNode load
                            && load.getOpcode() == Opcodes.ALOAD && load.var == FLUID_POS_SLOT;
                }
                if (ACCEPT_RECENT_FLOW.equals(call.name) && ACCEPT_RECENT_FLOW_DESC.equals(call.desc)) {
                    callsListener = true;
                }
            }
            previous = instruction;
        }
        return blockEntityLookups == 1 && lookupUsesFluidPos && callsListener;
    }

    /**
     * True while {@code FlowingFluidsApiImpl.removeFluidAmountFromPos} still hands {@code maxAmount} to the collector
     * without ever comparing it with zero.
     */
    public static boolean removalAcceptsNegativeAmounts(ClassNode apiImplementation) {
        MethodNode method = find(apiImplementation, REMOVE_FLUID, REMOVE_FLUID_DESC);
        if (method == null) {
            return false;
        }
        boolean reachesCollector = false;
        AbstractInsnNode[] instructions = method.instructions.toArray();
        for (int index = 0; index < instructions.length; index++) {
            AbstractInsnNode instruction = instructions[index];
            if (instruction instanceof MethodInsnNode call && COLLECT_AND_REMOVE.equals(call.name)) {
                reachesCollector = true;
            }
            if (instruction instanceof VarInsnNode load && load.getOpcode() == Opcodes.ILOAD
                    && load.var == MAX_AMOUNT_SLOT && isZeroComparison(nextRealInstruction(instructions, index))) {
                return false; // Flowing Fluids validates the amount itself now
            }
        }
        return reachesCollector;
    }

    /**
     * True while {@code FFFluidUtils.removeAllFluidAtPos} still turns any non-air block into air without ever asking
     * whether that block holds fluid: it reaches {@code Blocks.AIR} and {@code setBlock} but has no
     * {@code getFluidState} call and no {@code FluidState} access at all.
     */
    public static boolean removeAllFluidErasesAnyBlock(ClassNode fluidUtils) {
        MethodNode method = find(fluidUtils, REMOVE_ALL_FLUID, REMOVE_ALL_FLUID_DESC);
        if (method == null) {
            return false;
        }
        boolean placesAir = false;
        boolean setsBlock = false;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof FieldInsnNode field && ObfNames.BLOCKS_AIR.matches(field.name)) {
                placesAir = true;
            }
            if (instruction instanceof MethodInsnNode call) {
                if (ObfNames.SET_BLOCK.matches(call.name)) {
                    setsBlock = true;
                }
                if (ObfNames.isFluidStateGetter(call.name)
                        || ObfNames.FLUID_STATE_IS_EMPTY.matches(call.name) && call.owner.endsWith("FluidState")) {
                    return false; // Flowing Fluids already looks at the fluid state here
                }
            }
        }
        return placesAir && setsBlock;
    }

    /**
     * True while {@code LiquidBlock.neighborChanged} and {@code LiquidBlock.onPlace} still have exactly the shape the
     * fast interaction patch copies: one {@code FluidInteractionRegistry.canInteract} call, then (when it said no)
     * one {@code getTickDelay} and one {@code scheduleTick}, and nothing else beyond reading the fluid state. If a
     * game or Forge update adds any other work, skipping the method head would skip that work too, so the patch
     * stays off.
     */
    public static boolean liquidBlockOnlyChecksThenSchedules(ClassNode liquidBlock) {
        return methodOnlyChecksThenSchedules(find(liquidBlock, ObfNames.NEIGHBOR_CHANGED, NEIGHBOR_CHANGED_DESC))
                && methodOnlyChecksThenSchedules(find(liquidBlock, ObfNames.ON_PLACE, ON_PLACE_DESC));
    }

    /**
     * Human-readable list of the calls inside LiquidBlock's two target methods, for the log when the guard says no.
     * Written because the real runtime class was never seen when this guard was made: if the patch is skipped, the log
     * then shows exactly which call or branch is not what the guard expects.
     */
    public static String describeLiquidBlock(ClassNode liquidBlock) {
        StringBuilder text = new StringBuilder();
        for (MethodNode method : liquidBlock.methods) {
            if (!ObfNames.NEIGHBOR_CHANGED.matches(method.name) && !ObfNames.ON_PLACE.matches(method.name)) {
                continue;
            }
            text.append(method.name).append(method.desc).append(" -> ");
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode call) {
                    text.append(call.owner).append('.').append(call.name).append(' ');
                } else if (instruction instanceof JumpInsnNode jump) {
                    text.append("jump#").append(jump.getOpcode()).append(' ');
                }
            }
            text.append("; ");
        }
        if (text.length() == 0) {
            return "no neighborChanged/onPlace method found among " + liquidBlock.methods.size() + " methods";
        }
        return text.toString();
    }

    private static boolean methodOnlyChecksThenSchedules(MethodNode method) {
        if (method == null) {
            return false;
        }
        int canInteractCalls = 0;
        int tickDelayCalls = 0;
        int scheduleTickCalls = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof JumpInsnNode jump && jump.getOpcode() != Opcodes.IFNE) {
                return false; // The only branch of the original is "if (canInteract) return"
            }
            if (!(instruction instanceof MethodInsnNode call)) {
                continue;
            }
            if ("canInteract".equals(call.name)) { // Forge's own method: never obfuscated
                canInteractCalls++;
            } else if (ObfNames.GET_TICK_DELAY.matches(call.name)) {
                tickDelayCalls++;
            } else if (ObfNames.SCHEDULE_TICK.matches(call.name)) {
                scheduleTickCalls++;
            } else if (ObfNames.isFluidStateGetter(call.name) || ObfNames.FLUID_STATE_GET_TYPE.matches(call.name)
                    || ObfNames.LIQUID_BLOCK_GET_FLUID.matches(call.name)) {
                // Reading the fluid state, or the block's own fluid through Forge's getFluid(), is harmless
            } else {
                return false; // Any other call is work the patch would skip
            }
        }
        return canInteractCalls == 1 && tickDelayCalls == 1 && scheduleTickCalls == 1;
    }

    private static boolean isZeroComparison(AbstractInsnNode instruction) {
        if (!(instruction instanceof JumpInsnNode jump)) {
            return false;
        }
        int opcode = jump.getOpcode();
        return opcode == Opcodes.IFLT || opcode == Opcodes.IFLE || opcode == Opcodes.IFGT || opcode == Opcodes.IFGE;
    }

    private static AbstractInsnNode nextRealInstruction(AbstractInsnNode[] instructions, int index) {
        for (int next = index + 1; next < instructions.length; next++) {
            if (instructions[next].getOpcode() >= 0) {
                return instructions[next];
            }
        }
        return null;
    }

    private static MethodNode find(ClassNode classNode, Name name, String descriptor) {
        for (MethodNode method : classNode.methods) {
            if (name.matches(method.name) && descriptor.equals(method.desc)) {
                return method;
            }
        }
        return null;
    }

    private static MethodNode find(ClassNode classNode, String name, String descriptor) {
        for (MethodNode method : classNode.methods) {
            if (name.equals(method.name) && descriptor.equals(method.desc)) {
                return method;
            }
        }
        return null;
    }
}
