package io.github.samzhu.grimo.task.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskStatusTest {

    @Test
    @DisplayName("[S018] AC-5: DONE is terminal")
    void doneIsTerminal() {
        assertThat(TaskStatus.DONE.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("[S018] AC-5: CANCELLED is terminal")
    void cancelledIsTerminal() {
        assertThat(TaskStatus.CANCELLED.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("[S018] AC-5: OPEN, IN_PROGRESS, IN_REVIEW are not terminal")
    void nonTerminalStatuses() {
        assertThat(TaskStatus.OPEN.isTerminal()).isFalse();
        assertThat(TaskStatus.IN_PROGRESS.isTerminal()).isFalse();
        assertThat(TaskStatus.IN_REVIEW.isTerminal()).isFalse();
    }
}
