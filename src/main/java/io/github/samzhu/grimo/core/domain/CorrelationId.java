package io.github.samzhu.grimo.core.domain;

/**
 * Typed identifier for a single SSE stream / request-response correlation
 * scope.
 *
 * <p>Why distinct from {@link TurnId}: a {@code TurnId} persists as a row
 * in the session log, while a {@code CorrelationId} scopes a streaming
 * response and may span a jury fan-out that does NOT share a single turn
 * (e.g., three parallel sub-agents reviewing the same turn each carry
 * their own {@code CorrelationId}). Collapsing both concerns into one
 * type would hide that distinction — S001 D9 documents the rationale.
 *
 * <p>Same compact-constructor validation as the other id records: must
 * be a 21-char NanoID per {@link NanoIds}.
 */
public record CorrelationId(String value) {

    public CorrelationId {
        if (value == null || value.length() != 21) {
            throw new IllegalArgumentException(
                "CorrelationId must be a 21-character NanoID, got: " + value);
        }
    }

    public static CorrelationId random() {
        return new CorrelationId(NanoIds.generate());
    }
}
