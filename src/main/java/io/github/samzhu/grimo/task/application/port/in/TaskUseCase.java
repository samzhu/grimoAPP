package io.github.samzhu.grimo.task.application.port.in;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.samzhu.grimo.task.domain.Task;
import io.github.samzhu.grimo.task.domain.TaskPriority;
import io.github.samzhu.grimo.task.domain.TaskSource;
import io.github.samzhu.grimo.task.domain.TaskStatus;

/**
 * Inbound port for task management (S018).
 */
public interface TaskUseCase {

    Task create(@Nullable String projectId, String title,
                @Nullable String body, @Nullable TaskPriority priority,
                @Nullable String labelsJson, @Nullable TaskSource source);

    List<Task> list(@Nullable String projectId, @Nullable TaskStatus status);

    Optional<Task> findByNumber(int taskNumber);

    Task updateStatus(int taskNumber, TaskStatus status);

    Task update(int taskNumber, @Nullable String title, @Nullable String body,
                @Nullable TaskPriority priority, @Nullable String labelsJson);

    void delete(int taskNumber);
}
