package io.github.samzhu.grimo.core.domain;

import java.util.HashSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NanoIdsTest {

    @Test
    @DisplayName("[S018] AC-12: compact() returns 12-char base36 string")
    void compactReturns12CharBase36() {
        // When
        String id = NanoIds.compact();

        // Then
        assertThat(id).hasSize(12);
        assertThat(id).matches("[0-9a-z]{12}");
    }

    @Test
    @DisplayName("[S018] AC-12: compact() generates unique IDs")
    void compactGeneratesUniqueIds() {
        // When
        var ids = new HashSet<String>();
        for (int i = 0; i < 1000; i++) {
            ids.add(NanoIds.compact());
        }

        // Then — all 1000 are unique
        assertThat(ids).hasSize(1000);
    }

    @Test
    @DisplayName("[S018] AC-12: generate() still returns 21-char URL-safe string")
    void generateStillWorks() {
        // When
        String id = NanoIds.generate();

        // Then
        assertThat(id).hasSize(21);
        assertThat(id).matches("[A-Za-z0-9_\\-]{21}");
    }
}
