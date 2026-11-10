package me.thanhtnt2.fluidcompat.compat;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers two different facts about each compat mixin, so the log never claims more than is known:
 * <ul>
 *   <li>the guard's verdict: the target still has the expected shape and the mixin was <em>allowed</em> to apply;</li>
 *   <li>proof of use: the hook was actually <em>reached</em> by the running game (reported by the hook itself).</li>
 * </ul>
 * A verdict alone does not prove that Mixin managed to inject: an injection can still fail later, and Mixin then only
 * logs a warning and carries on without the hook.
 */
public final class CompatMixinState {

    private static final Map<String, Boolean> ALLOWED = new ConcurrentHashMap<>();
    private static final Set<String> REACHED = ConcurrentHashMap.newKeySet();

    private CompatMixinState() {
    }

    /** Stores the guard's verdict for a module or patch id. */
    public static void record(String moduleId, boolean allowed) {
        ALLOWED.put(moduleId, allowed);
    }

    /** @return true when the guard allowed the mixin to apply (not proof that the injection succeeded) */
    public static boolean isApplied(String moduleId) {
        return ALLOWED.getOrDefault(moduleId, false);
    }

    /**
     * @return true when the guard has given a verdict at all. No verdict means the mixin plugin never ran, which
     *         is a different problem from a guard that said no (for example a mixin config missing from the jar manifest)
     */
    public static boolean hasVerdict(String moduleId) {
        return ALLOWED.containsKey(moduleId);
    }

    /** Called by a hook the first time the running game reaches it. @return true only for that first time */
    public static boolean markHookReached(String moduleId) {
        return REACHED.add(moduleId);
    }

    public static boolean wasHookReached(String moduleId) {
        return REACHED.contains(moduleId);
    }
}
