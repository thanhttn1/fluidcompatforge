package me.thanhtnt2.fluidcompat.compat.fluidlogged.guard;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bytecode check for the Fluidlogged block list hook.
 *
 * <p>Fluidlogged 2.0.1 decides which blocks can hold a fluid, and which let fluid through, with two static methods of
 * its {@code Config} class that take a {@code BlockState} and answer a boolean. The hook only adds "yes" to their
 * answers, so it is right as long as those two methods exist with that shape. Fluidlogged is not obfuscated, and the
 * descriptor only names vanilla classes, whose names are the same in a real game and in the IDE. Pure ASM and pure
 * strings: nothing here loads Minecraft.
 */
public final class FluidloggedConfigShape {

    private static final Logger LOGGER = LoggerFactory.getLogger("FluidCompat/Mixin");
    private static final String DESCRIPTOR = "(Lnet/minecraft/world/level/block/state/BlockState;)Z";

    private FluidloggedConfigShape() {
    }

    public static boolean hasBlockLists(ClassNode config) {
        String problem = problem(config);
        if (problem != null) {
            LOGGER.warn("[fluidlogged_lists] guard refused Fluidlogged's Config: {}", problem);
        }
        return problem == null;
    }

    /** @return what is missing, in words for the log, or null when both methods exist as static boolean questions */
    public static String problem(ClassNode config) {
        for (String name : new String[] {"isFluidloggable", "isFluidPermeable"}) {
            MethodNode method = find(config, name);
            if (method == null) {
                return "no " + name + DESCRIPTOR;
            }
            if ((method.access & Opcodes.ACC_STATIC) == 0) {
                return name + " is no longer static";
            }
        }
        return null;
    }

    private static MethodNode find(ClassNode owner, String name) {
        for (MethodNode method : owner.methods) {
            if (name.equals(method.name) && DESCRIPTOR.equals(method.desc)) {
                return method;
            }
        }
        return null;
    }
}
