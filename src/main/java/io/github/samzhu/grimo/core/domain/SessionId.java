package io.github.samzhu.grimo.core.domain;

/**
 * Typed identifier for a Grimo {@code Session}.
 *
 * <p>Wraps a 21-character NanoID (see {@link NanoIds}). A record-typed
 * wrapper — rather than a raw {@code String} — gives compile-time safety
 * against accidentally passing a {@link TurnId}, {@link TaskId}, or
 * {@link CorrelationId} where a session identifier is expected.
 *
 * <p>Decided in S001 D1 (ID scheme) and D9 (distinct record types per id
 * kind).
 */
public record SessionId(String value) {

    public SessionId {
        if (value == null || value.length() != 21) {
            throw new IllegalArgumentException(
                "SessionId must be a 21-character NanoID, got: " + value);
        }
    }

    /**
     * @return a new {@code SessionId} backed by a freshly generated NanoID.
     */
    public static SessionId random() {
        return new SessionId(NanoIds.generate());
    }
}
