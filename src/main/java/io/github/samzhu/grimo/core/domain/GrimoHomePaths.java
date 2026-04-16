package io.github.samzhu.grimo.core.domain;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolver + lazy-creator for subdirectories under Grimo's home directory
 * ({@code ~/.grimo} by default — see {@link #home()} for override rules).
 *
 * <p>Utility class with static methods only — the domain layer is
 * Spring-free and has no state to inject. This matches the JDK's
 * {@code java.nio.file.Paths} idiom and S001 D6 rationale.
 *
 * <p>The typed accessors ({@link #memory()}, {@link #skills()}, …) each
 * <strong>materialize</strong> their directory on first call via
 * {@link Files#createDirectories(Path, java.nio.file.attribute.FileAttribute...)},
 * which is idempotent and thread-safe. Callers receive a path that is
 * guaranteed to exist.
 *
 * <p>{@code dev-standards §11} restricts direct use of
 * {@code System.getProperty} / {@code System.getenv} to
 * {@code core.domain.GrimoHomePaths} and {@code GrimoApplication.main} —
 * this class is the explicit host for those reads.
 */
public final class GrimoHomePaths {

    private GrimoHomePaths() {
        // Utility class — not instantiable.
    }

    /**
     * Resolves Grimo's home directory. Precedence (S001 D7):
     * <ol>
     *   <li>JVM system property {@code grimo.home} — primary, test-friendly
     *       (tests can set it via {@code System.setProperty} without
     *       forking the JVM).</li>
     *   <li>Environment variable {@code $GRIMO_HOME} — production
     *       operator override.</li>
     *   <li>{@code $HOME/.grimo} (via {@code System.getProperty("user.home")}).</li>
     * </ol>
     *
     * @return the home path (does <strong>not</strong> create the directory —
     *         only the typed accessors do).
     */
    public static Path home() {
        String prop = System.getProperty("grimo.home");
        if (prop != null && !prop.isBlank()) {
            return Path.of(prop);
        }
        String env = System.getenv("GRIMO_HOME");
        if (env != null && !env.isBlank()) {
            return Path.of(env);
        }
        return Path.of(System.getProperty("user.home"), ".grimo");
    }

    /** @return {@code <home>/memory}, created if absent. */
    public static Path memory() {
        return ensureDir(home().resolve("memory"));
    }

    /** @return {@code <home>/skills}, created if absent. */
    public static Path skills() {
        return ensureDir(home().resolve("skills"));
    }

    /** @return {@code <home>/sessions}, created if absent. */
    public static Path sessions() {
        return ensureDir(home().resolve("sessions"));
    }

    /** @return {@code <home>/worktrees}, created if absent. */
    public static Path worktrees() {
        return ensureDir(home().resolve("worktrees"));
    }

    /** @return {@code <home>/logs}, created if absent. */
    public static Path logs() {
        return ensureDir(home().resolve("logs"));
    }

    /** @return {@code <home>/config}, created if absent. */
    public static Path config() {
        return ensureDir(home().resolve("config"));
    }

    /** @return {@code <home>/db}, created if absent. */
    public static Path db() {
        return ensureDir(home().resolve("db"));
    }

    /**
     * Creates the directory (and any missing parents) if it does not
     * already exist. {@link Files#createDirectories} is idempotent and
     * thread-safe per its javadoc.
     *
     * <p>{@link IOException} is wrapped as {@link UncheckedIOException}
     * so callers see a clean "path is unusable" signal without checked-
     * exception noise — a domain-layer-appropriate translation.
     */
    private static Path ensureDir(Path p) {
        try {
            Files.createDirectories(p);
            return p;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
