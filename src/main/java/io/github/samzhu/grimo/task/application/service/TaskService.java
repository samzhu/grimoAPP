package io.github.samzhu.grimo.task.application.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import io.github.samzhu.grimo.core.domain.NanoIds;
import io.github.samzhu.grimo.project.application.port.in.ProjectUseCase;
import io.github.samzhu.grimo.task.application.port.in.TaskUseCase;
import io.github.samzhu.grimo.task.application.port.out.TaskPort;
import io.github.samzhu.grimo.task.domain.Task;
import io.github.samzhu.grimo.task.domain.TaskNotFoundException;
import io.github.samzhu.grimo.task.domain.TaskPriority;
import io.github.samzhu.grimo.task.domain.TaskSource;
import io.github.samzhu.grimo.task.domain.TaskStatus;

@Service
class TaskService implements TaskUseCase {

    private final TaskPort taskPort;
    private final ProjectUseCase projectUseCase;

    TaskService(TaskPort taskPort, ProjectUseCase projectUseCase) {
        this.taskPort = taskPort;
        this.projectUseCase = projectUseCase;
    }

    @Override
    public Task create(@Nullable String projectId, String title,
                       @Nullable String body, @Nullable TaskPriority priority,
                       @Nullable String labelsJson, @Nullable TaskSource source) {
        if (projectId != null) {
            projectUseCase.findById(projectId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Project not found: " + projectId));
        }

        var now = Instant.now();
        var task = new Task(
                NanoIds.compact(),
                taskPort.nextTaskNumber(),
                projectId,
                title, body,
                TaskStatus.OPEN,
                priority != null ? priority : TaskPriority.MEDIUM,
                labelsJson, source,
                now, now, null);
        taskPort.save(task);
        return task;
    }

    @Override
    public List<Task> list(@Nullable String projectId, @Nullable TaskStatus status) {
        if ("none".equals(projectId)) {
            return taskPort.findOrphan();
        }
        if (projectId != null) {
            return taskPort.findByProjectId(projectId);
        }
        if (status != null) {
            return taskPort.findByStatus(status);
        }
        return taskPort.findAll();
    }

    @Override
    public Optional<Task> findByNumber(int taskNumber) {
        return taskPort.findByNumber(taskNumber);
    }

    @Override
    public Task updateStatus(int taskNumber, TaskStatus status) {
        var existing = taskPort.findByNumber(taskNumber)
                .orElseThrow(() -> new TaskNotFoundException(taskNumber));
        var now = Instant.now();
        var closedAt = status.isTerminal() ? now : existing.closedAt();
        var updated = new Task(
                existing.id(), existing.taskNumber(), existing.projectId(),
                existing.title(), existing.body(),
                status, existing.priority(),
                existing.labelsJson(), existing.source(),
                existing.createdAt(), now, closedAt);
        taskPort.save(updated);
        return updated;
    }

    @Override
    public Task update(int taskNumber, @Nullable String title, @Nullable String body,
                       @Nullable TaskPriority priority, @Nullable String labelsJson) {
        var existing = taskPort.findByNumber(taskNumber)
                .orElseThrow(() -> new TaskNotFoundException(taskNumber));
        var updated = new Task(
                existing.id(), existing.taskNumber(), existing.projectId(),
                title != null ? title : existing.title(),
                body != null ? body : existing.body(),
                existing.status(),
                priority != null ? priority : existing.priority(),
                labelsJson != null ? labelsJson : existing.labelsJson(),
                existing.source(),
                existing.createdAt(), Instant.now(), existing.closedAt());
        taskPort.save(updated);
        return updated;
    }

    @Override
    public void delete(int taskNumber) {
        taskPort.findByNumber(taskNumber)
                .orElseThrow(() -> new TaskNotFoundException(taskNumber));
        taskPort.deleteByNumber(taskNumber);
    }
}
