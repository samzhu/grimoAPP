package io.github.samzhu.grimo.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SessionId} — exercise AC-1 of S001.
 *
 * <p>Because all four id records ({@link SessionId}, {@link TurnId},
 * {@link TaskId}, {@link CorrelationId}) share an identical compact
 * constructor + {@code random()} factory, {@code SessionId} is the
 * representative under test. Per dev-standards §7, duplicated tests for
 * the sibling records would be tautological.
 */
class SessionIdTest {

    /** URL-safe NanoID alphabet per {@code NanoIds#ALPHABET}. */
    private static final Pattern NANOID_21 = Pattern.compile("[A-Za-z0-9_-]{21}");

    @Test
    @DisplayName("AC-1 SessionId.random() produces a 21-char NanoID string")
    void randomProduces21CharNanoId() {
        // Given — NanoIds is the inlined generator in core.domain
        // When
        SessionId id = SessionId.random();

        // Then — length is 21 and every char is URL-safe alphabet
        assertThat(id.value()).hasSize(21);
        assertThat(id.value()).matches(NANOID_21);
    }

    @Test
    @DisplayName("AC-1 1000 SessionId.random() invocations are all distinct")
    void randomValuesAreDistinct() {
        // Given — 1000 trials (collision probability negligible at this N;
        //         NanoID 21-char has ~126 bits of entropy).
        // When
        Set<String> values = new HashSet<>(1000);
        for (int i = 0; i < 1000; i++) {
            values.add(SessionId.random().value());
        }

        // Then — no duplicates observed
        assertThat(values).hasSize(1000);
    }

    @Test
    @DisplayName("SessionId(null) is rejected by the compact constructor")
    void rejectsNull() {
        // Given — a null value
        // When / Then
        assertThatThrownBy(() -> new SessionId(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("SessionId");
    }

    @Test
    @DisplayName("SessionId with wrong length is rejected by the compact constructor")
    void rejectsWrongLength() {
        // Given — a 20-char string (one short of NanoID)
        String tooShort = "A".repeat(20);

        // When / Then
        assertThatThrownBy(() -> new SessionId(tooShort))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(tooShort);
    }
}
