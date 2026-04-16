package io.github.samzhu.grimo.core.domain;

/**
 * Typed identifier for a single conversational turn within a
 * {@link SessionId}.
 *
 * <p>A {@code TurnId} is a persistent identifier — it corresponds to a
 * row in the session event log and survives process restarts. Distinct
 * from {@link CorrelationId} (per-SSE-stream scope) by design — see
 * S001 D9.
 *
 * <p>Same compact-constructor validation as the other id records: must
 * be a 21-char NanoID per {@link NanoIds}.
 */
public record TurnId(String value) {

    public TurnId {
        if (value == null || value.length() != 21) {
            throw new IllegalArgumentException(
                "TurnId must be a 21-character NanoID, got: " + value);
        }
    }

    public static TurnId random() {
        return new TurnId(NanoIds.generate());
    }
}
