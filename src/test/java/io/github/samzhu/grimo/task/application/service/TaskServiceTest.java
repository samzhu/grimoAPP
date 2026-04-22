package io.github.samzhu.grimo.task.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.samzhu.grimo.project.application.port.in.ProjectUseCase;
import io.github.samzhu.grimo.project.domain.Project;
import io.github.samzhu.grimo.task.application.port.out.TaskPort;
import io.github.samzhu.grimo.task.domain.Task;
import io.github.samzhu.grimo.task.domain.TaskNotFoundException;
import io.github.samzhu.grimo.task.domain.TaskPriority;
import io.github.samzhu.grimo.task.domain.TaskStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskServiceTest {

    private final TaskPort taskPort = mock(TaskPort.class);
    private final ProjectUseCase projectUseCase = mock(ProjectUseCase.class);
    private final TaskService service = new TaskService(taskPort, projectUseCase);

    // ── create ──

    @Test
    @DisplayName("[S026] AC-2: create with valid projectId saves task")
    void createWithValidProject() {
        // Given
        var project = new Project("p1", "demo", "/tmp", null, Instant.now(), Instant.now());
        when(projectUseCase.findById("p1")).thenReturn(Optional.of(project));
        when(taskPort.nextTaskNumber()).thenReturn(1);

        // When
        Task result = service.create("p1", "Fix bug", "details", TaskPriority.HIGH, null, null);

        // Then
        assertThat(result.projectId()).isEqualTo("p1");
        assertThat(result.title()).isEqualTo("Fix bug");
        assertThat(result.body()).isEqualTo("details");
        assertThat(result.status()).isEqualTo(TaskStatus.OPEN);
        assertThat(result.priority()).isEqualTo(TaskPriority.HIGH);
        assertThat(result.taskNumber()).isEqualTo(1);
        assertThat(result.id()).isNotBlank();
        verify(taskPort).save(any(Task.class));
    }

    @Test
    @DisplayName("[S026] AC-2: create with non-existent projectId throws IllegalArgumentException")
    void createWithInvalidProjectThrows() {
        // Given
        when(projectUseCase.findById("bad")).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.create("bad", "title", null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bad");
        verify(taskPort, never()).save(any());
    }

    @Test
    @DisplayName("[S026] AC-2: create with null projectId skips project validation")
    void createWithNullProjectSkipsCheck() {
        // Given
        when(taskPort.nextTaskNumber()).thenReturn(42);

        // When
        Task result = service.create(null, "Grimo task", null, null, null, null);

        // Then
        assertThat(result.projectId()).isNull();
        assertThat(result.priority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(result.taskNumber()).isEqualTo(42);
        verify(projectUseCase, never()).findById(any());
        verify(taskPort).save(any(Task.class));
    }

    // ── list ──

    @Test
    @DisplayName("[S026] AC-2: list with 'none' sentinel returns orphan tasks")
    void listNoneSentinelReturnsOrphans() {
        // Given
        var orphan = task(1, null);
        when(taskPort.findOrphan()).thenReturn(List.of(orphan));

        // When
        List<Task> result = service.list("none", null);

        // Then
        assertThat(result).containsExactly(orphan);
        verify(taskPort).findOrphan();
    }

    @Test
    @DisplayName("[S026] AC-2: list with projectId delegates to findByProjectId")
    void listByProjectId() {
        // Given
        var task = task(1, "p1");
        when(taskPort.findByProjectId("p1")).thenReturn(List.of(task));

        // When
        List<Task> result = service.list("p1", null);

        // Then
        assertThat(result).containsExactly(task);
    }

    @Test
    @DisplayName("[S026] AC-2: list with status delegates to findByStatus")
    void listByStatus() {
        // Given
        var task = task(1, null);
        when(taskPort.findByStatus(TaskStatus.OPEN)).thenReturn(List.of(task));

        // When
        List<Task> result = service.list(null, TaskStatus.OPEN);

        // Then
        assertThat(result).containsExactly(task);
    }

    @Test
    @DisplayName("[S026] AC-2: list with no filters returns all tasks")
    void listAll() {
        // Given
        var t1 = task(1, null);
        var t2 = task(2, "p1");
        when(taskPort.findAll()).thenReturn(List.of(t1, t2));

        // When
        List<Task> result = service.list(null, null);

        // Then
        assertThat(result).containsExactly(t1, t2);
    }

    // ── update ──

    @Test
    @DisplayName("[S026] AC-2: update applies patch semantics — null fields keep existing values")
    void updatePatchSemantics() {
        // Given
        var existing = new Task("id1", 1, "p1", "Old title", "Old body",
                TaskStatus.OPEN, TaskPriority.LOW, "[\"bug\"]", null,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"), null);
        when(taskPort.findByNumber(1)).thenReturn(Optional.of(existing));

        // When — only update title, leave body/priority/labels as null (keep existing)
        Task result = service.update(1, "New title", null, null, null);

        // Then
        assertThat(result.title()).isEqualTo("New title");
        assertThat(result.body()).isEqualTo("Old body");
        assertThat(result.priority()).isEqualTo(TaskPriority.LOW);
        assertThat(result.labelsJson()).isEqualTo("[\"bug\"]");
        verify(taskPort).save(any(Task.class));
    }

    @Test
    @DisplayName("[S026] AC-2: update on non-existent task throws TaskNotFoundException")
    void updateNotFoundThrows() {
        // Given
        when(taskPort.findByNumber(999)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.update(999, "t", null, null, null))
                .isInstanceOf(TaskNotFoundException.class);
    }

    // ── updateStatus ──

    @Test
    @DisplayName("[S026] AC-2: updateStatus to terminal DONE sets closedAt")
    void updateStatusTerminalSetsClosedAt() {
        // Given
        var existing = task(1, null);
        when(taskPort.findByNumber(1)).thenReturn(Optional.of(existing));

        // When
        Task result = service.updateStatus(1, TaskStatus.DONE);

        // Then
        assertThat(result.status()).isEqualTo(TaskStatus.DONE);
        assertThat(result.closedAt()).isNotNull();
        verify(taskPort).save(any(Task.class));
    }

    @Test
    @DisplayName("[S026] AC-2: updateStatus to non-terminal IN_PROGRESS keeps existing closedAt")
    void updateStatusNonTerminalKeepsClosedAt() {
        // Given
        var existing = task(1, null);
        when(taskPort.findByNumber(1)).thenReturn(Optional.of(existing));

        // When
        Task result = service.updateStatus(1, TaskStatus.IN_PROGRESS);

        // Then
        assertThat(result.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(result.closedAt()).isNull(); // existing.closedAt() was null
        verify(taskPort).save(any(Task.class));
    }

    @Test
    @DisplayName("[S026] AC-2: updateStatus on non-existent task throws TaskNotFoundException")
    void updateStatusNotFoundThrows() {
        // Given
        when(taskPort.findByNumber(999)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.updateStatus(999, TaskStatus.DONE))
                .isInstanceOf(TaskNotFoundException.class);
    }

    // ── delete ──

    @Test
    @DisplayName("[S026] AC-2: delete existing task succeeds")
    void deleteExisting() {
        // Given
        var existing = task(5, null);
        when(taskPort.findByNumber(5)).thenReturn(Optional.of(existing));

        // When
        service.delete(5);

        // Then
        verify(taskPort).deleteByNumber(5);
    }

    @Test
    @DisplayName("[S026] AC-2: delete non-existent task throws TaskNotFoundException")
    void deleteNotFoundThrows() {
        // Given
        when(taskPort.findByNumber(999)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> service.delete(999))
                .isInstanceOf(TaskNotFoundException.class);
    }

    // ── helper ──

    private static Task task(int number, String projectId) {
        var now = Instant.now();
        return new Task("id-" + number, number, projectId, "Task " + number, null,
                TaskStatus.OPEN, TaskPriority.MEDIUM, null, null, now, now, null);
    }
}
