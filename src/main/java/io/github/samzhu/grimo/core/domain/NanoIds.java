package io.github.samzhu.grimo.core.domain;

import java.security.SecureRandom;

/**
 * Vendored NanoID generator.
 *
 * <p>Produces 21-character URL-safe IDs backed by {@link SecureRandom}.
 * Collision resistance is equivalent to a random 126-bit identifier,
 * sufficient for all Grimo session / turn / task / correlation ids.
 *
 * <p>Why vendored rather than a library dependency:
 * <ul>
 *   <li>{@code com.aventrix.jnanoid:jnanoid:2.0.0} is stale (unchanged since
 *       2018, no security updates).</li>
 *   <li>{@code io.viascom.nanoid:nanoid:1.0.1} pulls {@code kotlin-stdlib}
 *       onto the classpath — unwanted weight for a ~30-line algorithm.</li>
 *   <li>Owning this code keeps the domain layer dependency-free and
 *       native-image trivial.</li>
 * </ul>
 *
 * <p>Algorithm: draw {@link #SIZE} random bytes; map each byte's low 6 bits
 * to a character in {@link #ALPHABET}. Because {@code ALPHABET.length == 64}
 * (exactly {@code 2^6}), {@code b & 63} is a perfectly uniform mapping with
 * zero bias — no rejection sampling needed.
 *
 * <p>Utility class — {@link #NanoIds()} is private. Thread-safe:
 * {@link SecureRandom} is thread-safe per its javadoc.
 *
 * <p>Decided in S001 D2.
 */
public final class NanoIds {

    private static final SecureRandom RNG = new SecureRandom();

    /**
     * URL-safe alphabet (64 characters) — matches the reference NanoID spec.
     * Length is exactly 64 so {@code byte & 63} maps uniformly without bias.
     */
    private static final char[] ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-".toCharArray();

    /** ID length in characters. Matches the reference NanoID default. */
    private static final int SIZE = 21;

    private NanoIds() {
        // Utility class — not instantiable.
    }

    /**
     * @return a newly generated 21-character NanoID.
     */
    public static String generate() {
        byte[] bytes = new byte[SIZE];
        RNG.nextBytes(bytes);
        char[] out = new char[SIZE];
        for (int i = 0; i < SIZE; i++) {
            out[i] = ALPHABET[bytes[i] & 63];
        }
        return new String(out);
    }
}
