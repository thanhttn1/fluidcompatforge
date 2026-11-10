package me.thanhtnt2.fluidcompat.logging;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import me.thanhtnt2.fluidcompat.FluidCompat;
import me.thanhtnt2.fluidcompat.config.FluidCompatModulesConfig;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Dedicated log file ({@code <game folder>/logs/fluidcompat.log}) so developers and players can
 * share Fluid Compat's own trail without digging through latest.log.
 *
 * <p>Writing is synchronous on purpose: volume is tiny (verbose lines are throttled) and an
 * asynchronous writer could lose the last lines of a crash. Warnings and errors are mirrored to
 * the normal game log as well. This class never throws.
 */
public final class FluidCompatLog {

    private static final String LOG_FILE_NAME = "fluidcompat.log";
    private static final String PREVIOUS_LOG_FILE_NAME = "fluidcompat-previous.log";
    private static final String ROLLED_LOG_FILE_NAME = "fluidcompat-rolled.log";
    // Debug mode can write a lot. At this size the file is rolled over once, so one run never needs more than two files
    private static final long MAX_LOG_FILE_BYTES = 16L * 1024L * 1024L;
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT);

    private static final Object LOCK = new Object();
    private static final Map<String, Long> LAST_VERBOSE_MILLIS = new ConcurrentHashMap<>();
    // Per failing hook: {time of the last written error, repeats hidden since then}
    private static final Map<String, long[]> ERROR_THROTTLE_STATE = new ConcurrentHashMap<>();
    private static final long ERROR_THROTTLE_MILLIS = 60_000L;
    // Above this many remembered keys, entries whose throttle window has passed are dropped (see pruneThrottleKeys)
    private static final int THROTTLE_KEYS_PRUNE_THRESHOLD = 512;

    private static BufferedWriter writer;
    private static Path logFile;
    private static long bytesWritten;
    private static boolean initialized;

    private FluidCompatLog() {
    }

    /** Opens the log file once. Safe to call early: it does not read any config. */
    public static void initialize() {
        synchronized (LOCK) {
            if (initialized) {
                return;
            }
            initialized = true;
            try {
                Path logsFolder = FMLPaths.GAMEDIR.get().resolve("logs");
                Files.createDirectories(logsFolder);
                Path current = logsFolder.resolve(LOG_FILE_NAME);
                keepPreviousRun(current, logsFolder.resolve(PREVIOUS_LOG_FILE_NAME));
                // A rolled file left by the last run is only the tail end of that run, so it must not be mistaken for this one
                Files.deleteIfExists(logsFolder.resolve(ROLLED_LOG_FILE_NAME));
                writer = Files.newBufferedWriter(current, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
                logFile = current;
            } catch (IOException | RuntimeException exception) {
                FluidCompat.LOGGER.warn("Fluid Compat could not open its own log file, using latest.log only", exception);
            }
        }
        info("Fluid Compat log started. Java {} on {}", System.getProperty("java.version"), System.getProperty("os.name"));
    }

    public static Path logFilePath() {
        synchronized (LOCK) {
            return logFile;
        }
    }

    public static void info(String pattern, Object... arguments) {
        write("INFO", format(pattern, arguments), null);
    }

    public static void warn(String pattern, Object... arguments) {
        String message = format(pattern, arguments);
        FluidCompat.LOGGER.warn("[Fluid Compat] {}", message);
        write("WARN", message, null);
    }

    public static void error(String message, Throwable error) {
        FluidCompat.LOGGER.error("[Fluid Compat] {}", message, error);
        write("ERROR", message, error);
    }

    /**
     * Like {@link #error} but a repeating failure of the same kind is written once per minute, with a count of
     * the hidden repeats. A broken hook inside a machine tick would otherwise write a stack trace on every
     * tick, and every written line is flushed to disk on the server thread.
     *
     * @param key a fixed name for the failing hook (never a position), so the table stays tiny
     */
    public static void errorThrottled(String key, String message, Throwable error) {
        long now = System.currentTimeMillis();
        long[] state = ERROR_THROTTLE_STATE.computeIfAbsent(key, ignored -> new long[2]);
        String suffix;
        synchronized (state) {
            if (state[0] != 0L && now - state[0] < ERROR_THROTTLE_MILLIS) {
                state[1]++;
                return;
            }
            suffix = state[1] > 0 ? " (" + state[1] + " identical error(s) hidden during the last minute)" : "";
            state[0] = now;
            state[1] = 0L;
        }
        error(message + suffix, error);
    }

    /** Only written when {@code logging.verbose} or {@code logging.debugMode} is on. */
    public static void debug(String pattern, Object... arguments) {
        if (isVerbose() || isDebugMode()) {
            write("DEBUG", format(pattern, arguments), null);
        }
    }

    /** Only written in debug mode, for very chatty details such as every pipe block update. Never throttled. */
    public static void trace(String pattern, Object... arguments) {
        if (isDebugMode()) {
            write("TRACE", format(pattern, arguments), null);
        }
    }

    /**
     * True when a debug line could really be written right now. Hot paths use this to skip building
     * message strings that would be thrown away while verbose and debug logging are both off.
     */
    public static boolean isDetailLoggingActive() {
        return isFileEnabled() && (isDebugMode() || isVerbose());
    }

    /** Like {@link #debug} but at most once per throttle window for the same key (for example a machine position). */
    public static void debugThrottled(String key, String pattern, Object... arguments) {
        if (isDebugMode()) {
            // Debug mode means "log everything", so the throttle is skipped
            write("DEBUG", format(pattern, arguments), null);
            return;
        }
        if (!isVerbose()) {
            return;
        }
        long now = System.currentTimeMillis();
        long last = LAST_VERBOSE_MILLIS.getOrDefault(key, 0L);
        if (now - last < throttleMillis()) {
            return;
        }
        LAST_VERBOSE_MILLIS.put(key, now);
        pruneThrottleKeys(now);
        write("DEBUG", format(pattern, arguments), null);
    }

    // A key whose window has passed would be logged again anyway, so forgetting it changes nothing.
    // This keeps the map from growing with every machine that was ever placed on a long-running server
    private static void pruneThrottleKeys(long now) {
        if (LAST_VERBOSE_MILLIS.size() <= THROTTLE_KEYS_PRUNE_THRESHOLD) {
            return;
        }
        long window = throttleMillis();
        LAST_VERBOSE_MILLIS.values().removeIf(lastMillis -> now - lastMillis >= window);
    }

    private static void keepPreviousRun(Path current, Path previous) {
        if (!Files.exists(current)) {
            return;
        }
        try {
            Files.move(current, previous, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            // Not fatal: the new run simply overwrites the old file
            FluidCompat.LOGGER.debug("Could not keep previous Fluid Compat log", exception);
        }
    }

    // Called with LOCK held. Keeps the first part of the run in the rolled file and continues in a fresh one
    private static void rollOver() throws IOException {
        writer.close();
        Files.move(logFile, logFile.resolveSibling(ROLLED_LOG_FILE_NAME), StandardCopyOption.REPLACE_EXISTING);
        writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        bytesWritten = 0L;
        writer.write("[" + LocalDateTime.now().format(TIMESTAMP) + "] [log/INFO] Log rolled over, the earlier part of this run is in "
                + ROLLED_LOG_FILE_NAME);
        writer.newLine();
        writer.flush();
    }

    private static void write(String level, String message, Throwable error) {
        if (!isFileEnabled()) {
            return;
        }
        synchronized (LOCK) {
            if (writer == null) {
                return;
            }
            try {
                String line = "[" + LocalDateTime.now().format(TIMESTAMP) + "] [" + Thread.currentThread().getName()
                        + "/" + level + "] " + message;
                writer.write(line);
                writer.newLine();
                bytesWritten += line.length() + 1L;
                if (error != null) {
                    String stackTrace = stackTraceOf(error);
                    writer.write(stackTrace);
                    bytesWritten += stackTrace.length();
                }
                writer.flush();
                if (bytesWritten >= MAX_LOG_FILE_BYTES) {
                    rollOver();
                }
            } catch (IOException exception) {
                // Stop trying after the first failure so a full disk cannot spam the game log
                writer = null;
                FluidCompat.LOGGER.warn("Fluid Compat log file became unwritable, file logging stopped", exception);
            }
        }
    }

    private static String stackTraceOf(Throwable error) {
        StringWriter buffer = new StringWriter();
        error.printStackTrace(new PrintWriter(buffer));
        return buffer.toString();
    }

    private static String format(String pattern, Object... arguments) {
        StringBuilder result = new StringBuilder(pattern.length() + 16 * arguments.length);
        int argumentIndex = 0;
        int cursor = 0;
        while (cursor < pattern.length()) {
            int placeholder = pattern.indexOf("{}", cursor);
            if (placeholder < 0 || argumentIndex >= arguments.length) {
                break;
            }
            result.append(pattern, cursor, placeholder).append(arguments[argumentIndex++]);
            cursor = placeholder + 2;
        }
        return result.append(pattern, cursor, pattern.length()).toString();
    }

    // The config may not be loaded yet during early startup, so fall back to sensible defaults
    private static boolean isFileEnabled() {
        try {
            return FluidCompatModulesConfig.LOG_FILE_ENABLED.get();
        } catch (IllegalStateException notLoadedYet) {
            return true;
        }
    }

    public static boolean isDebugMode() {
        try {
            return FluidCompatModulesConfig.LOG_DEBUG_MODE.get();
        } catch (IllegalStateException notLoadedYet) {
            return false;
        }
    }

    private static boolean isVerbose() {
        try {
            return FluidCompatModulesConfig.LOG_VERBOSE.get();
        } catch (IllegalStateException notLoadedYet) {
            return false;
        }
    }

    private static long throttleMillis() {
        try {
            return FluidCompatModulesConfig.LOG_VERBOSE_THROTTLE_MS.get();
        } catch (IllegalStateException notLoadedYet) {
            return 1000L;
        }
    }
}
