package io.github.samzhu.grimo.subagent.application.port.out;

import java.util.List;
import java.util.Optional;

import io.github.samzhu.grimo.subagent.domain.TaskExecution;

/**
 * Outbound port for persisting and querying TaskExecution records (S028).
 */
public interface TaskExecutionPort {

    void save(TaskExecution execution);

    Optional<TaskExecution> findById(String id);

    List<TaskExecution> findByTaskId(String taskId);
}
