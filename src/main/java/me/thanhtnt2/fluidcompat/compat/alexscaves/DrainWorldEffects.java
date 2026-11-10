package me.thanhtnt2.fluidcompat.compat.alexscaves;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import me.thanhtnt2.fluidcompat.logging.FluidCompatLog;
import net.minecraft.core.BlockPos;

/**
 * Plays the Drain's sound and particle effects, which the original logic triggers with a network message.
 *
 * <p>Alex's Caves classes are reached by name, so Fluid Compat has no compile-time link to them. If anything about
 * those classes is not as expected the effects are switched off with one log line; the fluid logic never depends on them.
 */
final class DrainWorldEffects {

    /** Message ids the original Drain uses: 3 when it sucks fluid in, 4 when it pours fluid out. */
    static final int SUCK = 3;
    static final int POUR = 4;

    private static final String MESSAGE_CLASS = "com.github.alexmodguy.alexscaves.server.message.WorldEventMessage";
    private static final String MOD_CLASS = "com.github.alexmodguy.alexscaves.AlexsCaves";

    private static volatile Constructor<?> messageConstructor;
    private static volatile Method sendToAll;
    private static volatile boolean unavailable;

    private DrainWorldEffects() {
    }

    static void play(int messageId, BlockPos pos) {
        if (unavailable) {
            return;
        }
        try {
            resolve();
            Object message = messageConstructor.newInstance(messageId, pos.getX(), pos.getY(), pos.getZ());
            sendToAll.invoke(null, message);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            unavailable = true;
            FluidCompatLog.warn("[alexscaves] Drain sound and particle effects switched off: {}", String.valueOf(error));
        }
    }

    private static void resolve() throws ReflectiveOperationException {
        if (sendToAll != null) {
            return;
        }
        ClassLoader loader = DrainWorldEffects.class.getClassLoader();
        Class<?> messageClass = Class.forName(MESSAGE_CLASS, true, loader);
        Class<?> modClass = Class.forName(MOD_CLASS, true, loader);
        messageConstructor = messageClass.getConstructor(int.class, int.class, int.class, int.class);
        sendToAll = modClass.getMethod("sendMSGToAll", Object.class);
    }
}
