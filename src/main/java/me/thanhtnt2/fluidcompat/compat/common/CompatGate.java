package me.thanhtnt2.fluidcompat.compat.common;

import me.thanhtnt2.fluidcompat.config.FluidCompatModulesConfig;

/**
 * One place that answers "is the master compat switch on?" for every optional hook.
 *
 * <p>Mixins cannot be removed once the game has started, so turning {@code general.compatEnabled} off only stops the
 * module setup code. Each hook therefore has to ask this gate at run time and hand control back to the original
 * code when it says no. Modern Industrialization's core pump limit deliberately does not ask: the setting promises
 * that this one hook keeps working.
 */
public final class CompatGate {

    private CompatGate() {
    }

    /** False when the player switched every compat module off. Cheap: a plain config value read. */
    public static boolean masterEnabled() {
        return FluidCompatModulesConfig.COMPAT_ENABLED.get();
    }
}
