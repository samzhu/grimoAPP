package io.github.samzhu.grimo.subagent.application.port.in;

import java.util.List;
import java.util.Optional;

import io.github.samzhu.grimo.subagent.domain.TaskExecution;

/**
 * Inbound port for delegating task execution to a Docker-sandboxed
 * subagent (S028). Async — {@link #execute} returns immediately with
 * a PENDING execution; background virtual thread performs the actual work.
 */
public interface DelegateTaskUseCase {

    /**
     * Dispatches a task to a subagent for execution.
     * Returns immediately with a PENDING TaskExecution; background
     * virtual thread transitions it to RUNNING → SUCCEEDED | FAILED.
     *
     * @param taskNumber task number (from grimo_task.task_number)
     * @param prompt     the prompt to send to Claude Code
     * @return the created TaskExecution (status is always PENDING on return)
     */
    TaskExecution execute(int taskNumber, String prompt);

    /**
     * Retrieves execution status by execution ID.
     */
    Optional<TaskExecution> findExecution(String executionId);

    /**
     * Lists all executions for a given task number.
     */
    List<TaskExecution> listExecutions(int taskNumber);
}
