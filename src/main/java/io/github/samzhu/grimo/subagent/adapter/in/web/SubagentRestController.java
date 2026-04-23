package io.github.samzhu.grimo.subagent.adapter.in.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.samzhu.grimo.subagent.application.port.in.DelegateTaskUseCase;
import io.github.samzhu.grimo.subagent.domain.TaskExecution;

/**
 * REST API for subagent task execution (S028).
 *
 * <ul>
 *   <li>POST /api/tasks/{taskNumber}/execute — dispatch task to subagent (202 Accepted)</li>
 *   <li>GET  /api/tasks/{taskNumber}/executions — list all executions for a task</li>
 *   <li>GET  /api/tasks/{taskNumber}/executions/{executionId} — query single execution</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/tasks/{taskNumber}")
class SubagentRestController {

    private final DelegateTaskUseCase delegateTaskUseCase;

    SubagentRestController(DelegateTaskUseCase delegateTaskUseCase) {
        this.delegateTaskUseCase = delegateTaskUseCase;
    }

    record ExecuteRequest(String prompt) {}

    @PostMapping("/execute")
    ResponseEntity<TaskExecution> execute(@PathVariable int taskNumber,
                                          @RequestBody ExecuteRequest request) {
        var execution = delegateTaskUseCase.execute(taskNumber, request.prompt());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(execution);
    }

    @GetMapping("/executions")
    List<TaskExecution> listExecutions(@PathVariable int taskNumber) {
        return delegateTaskUseCase.listExecutions(taskNumber);
    }

    @GetMapping("/executions/{executionId}")
    ResponseEntity<TaskExecution> findExecution(@PathVariable int taskNumber,
                                                @PathVariable String executionId) {
        return delegateTaskUseCase.findExecution(executionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> handleNotFound(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, String>> handleBadState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }
}
