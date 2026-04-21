package io.github.samzhu.grimo.task.application.port.out;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import io.github.samzhu.grimo.task.domain.Task;
import io.github.samzhu.grimo.task.domain.TaskStatus;

/**
 * Outbound port for task persistence.
 */
public interface TaskPort {

    void save(Task task);

    Optional<Task> findById(String id);

    Optional<Task> findByNumber(int taskNumber);

    List<Task> findAll();

    List<Task> findByProjectId(@Nullable String projectId);

    List<Task> findByStatus(TaskStatus status);

    List<Task> findOrphan();

    int nextTaskNumber();

    void deleteByNumber(int taskNumber);
}
