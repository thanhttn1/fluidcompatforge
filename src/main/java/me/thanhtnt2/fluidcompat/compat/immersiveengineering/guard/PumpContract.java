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
 * The all-or-nothing check for the Immersive Engineering Fluid Pump hooks.
 *
 * <p>The hooks only work together: the infinite rule and the search filter both ask {@code canConvertToSource}, the
 * thin-block stack must come out of the same {@code Utils.drainFluidBlock} calls the pump uses, the tank fill that
 * follows must be the one that receives that stack, and the cobblestone call must be the one after the drain. So the
 * hooks are switched on only when each of those calls exists exactly as many times as the hooks expect, in the three
 * methods they live in. If an IE update changes the pump, every hook stays off and the log says which count was wrong.
 *
 * <p>Forge 1.20.1 runs with SRG names and the IDE shows Mojang names, so both spellings of the vanilla calls are
 * accepted. IE's own names are never obfuscated. Pure ASM and pure strings: nothing here loads Minecraft.
 */
public final class PumpContract {

    private static final Logger LOGGER = LoggerFactory.getLogger("FluidCompat/Mixin");

    private static final String FLUID_STATE = "net/minecraft/world/level/material/FluidState";
    private static final String LEVEL = "net/minecraft/world/level/Level";
    private static final String BLOCKS = "net/minecraft/world/level/block/Blocks";
    private static final String IE_UTILS = "blusunrize/immersiveengineering/common/util/Utils";
    private static final String FLUID_TANK = "net/minecraftforge/fluids/capability/templates/FluidTank";
    private static final String SEARCH_FLUID = "searchFluid";

    private PumpContract() {
    }

    public static boolean isHookable(ClassNode pump) {
        String problem = problem(pump);
        if (problem != null) {
            LOGGER.warn("[immersiveengineering] guard refused the Fluid Pump, every IE hook stays off: {}", problem);
        }
        return problem == null;
    }

    /** @return what does not match, in words for the log, or null when the pump has the shape the hooks expect */
    public static String problem(ClassNode pump) {
        MethodNode tick = find(pump, "tickServer", "()V");
        MethodNode area = find(pump, "checkAreaTick", "()V");
        MethodNode gather = find(pump, "gatherInfiniteFluidFromWorld", "(Lnet/minecraft/core/Direction;)V");
        if (tick == null || area == null || gather == null) {
            return "tickServer, checkAreaTick or gatherInfiniteFluidFromWorld is missing";
        }
        boolean hasSearchFluid = pump.fields.stream().anyMatch(field -> SEARCH_FLUID.equals(field.name)
                && "Lnet/minecraft/world/level/material/Fluid;".equals(field.desc));
        if (!hasSearchFluid) {
            return "no searchFluid field";
        }
        String wrong = firstWrongCount(
                expect("checkAreaTick canConvertToSource", count(area, FLUID_STATE, "canConvertToSource", null), 2),
                expect("checkAreaTick FluidState.getType", countEither(area, FLUID_STATE, "getType", "m_76152_"), 1),
                expect("checkAreaTick Utils.getRelatedFluid", count(area, IE_UTILS, "getRelatedFluid", null), 1),
                expect("checkAreaTick Utils.drainFluidBlock", count(area, IE_UTILS, "drainFluidBlock", null), 1),
                expect("checkAreaTick writes searchFluid", countPutField(area, SEARCH_FLUID) > 0 ? 1 : 0, 1),
                expect("tickServer Utils.drainFluidBlock", count(tick, IE_UTILS, "drainFluidBlock", null), 2),
                expect("tickServer FluidTank.fill", count(tick, FLUID_TANK, "fill", null), 2),
                expect("tickServer Level.setBlockAndUpdate", countEither(tick, LEVEL, "setBlockAndUpdate", "m_46597_"), 1),
                expect("tickServer reads Blocks.COBBLESTONE", countStatic(tick, BLOCKS, "COBBLESTONE", "f_50652_"), 1),
                expect("gatherInfiniteFluidFromWorld canConvertToSource", count(gather, FLUID_STATE, "canConvertToSource", null), 1));
        return wrong;
    }

    private record Count(String what, int found, int expected) {
    }

    private static Count expect(String what, int found, int expected) {
        return new Count(what, found, expected);
    }

    private static String firstWrongCount(Count... counts) {
        for (Count count : counts) {
            if (count.found() != count.expected()) {
                return count.what() + " is " + count.found() + ", the hooks expect " + count.expected();
            }
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

    private static int count(MethodNode method, String owner, String name, String descriptor) {
        int found = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && owner.equals(call.owner) && name.equals(call.name)
                    && (descriptor == null || descriptor.equals(call.desc))) {
                found++;
            }
        }
        return found;
    }

    private static int countEither(MethodNode method, String owner, String name, String srgName) {
        return count(method, owner, name, null) + count(method, owner, srgName, null);
    }

    private static int countPutField(MethodNode method, String field) {
        int found = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode access && access.getOpcode() == Opcodes.PUTFIELD && field.equals(access.name)) {
                found++;
            }
        }
        return found;
    }

    private static int countStatic(MethodNode method, String owner, String name, String srgName) {
        int found = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode access && access.getOpcode() == Opcodes.GETSTATIC
                    && owner.equals(access.owner) && (name.equals(access.name) || srgName.equals(access.name))) {
                found++;
            }
        }
        return found;
    }
}
