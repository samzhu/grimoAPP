package io.github.samzhu.grimo.task.adapter.in.web;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.samzhu.grimo.task.application.port.in.TaskUseCase;
import io.github.samzhu.grimo.task.domain.Task;
import io.github.samzhu.grimo.task.domain.TaskNotFoundException;
import io.github.samzhu.grimo.task.domain.TaskPriority;
import io.github.samzhu.grimo.task.domain.TaskStatus;

@RestController
@RequestMapping("/api/tasks")
class TaskRestController {

    private final TaskUseCase taskUseCase;

    TaskRestController(TaskUseCase taskUseCase) {
        this.taskUseCase = taskUseCase;
    }

    record CreateTaskRequest(@Nullable String projectId, String title,
                             @Nullable String body, @Nullable String priority,
                             @Nullable String labels) {}

    record UpdateTaskRequest(@Nullable String title, @Nullable String body,
                             @Nullable String status, @Nullable String priority,
                             @Nullable String labels) {}

    @PostMapping
    ResponseEntity<Task> create(@RequestBody CreateTaskRequest req) {
        TaskPriority priority = req.priority() != null
                ? TaskPriority.valueOf(req.priority()) : null;
        var task = taskUseCase.create(
                req.projectId(), req.title(), req.body(),
                priority, req.labels(), null);
        return ResponseEntity.status(HttpStatus.CREATED).body(task);
    }

    @GetMapping
    List<Task> list(@RequestParam(required = false) @Nullable String projectId,
                    @RequestParam(required = false) @Nullable String status) {
        TaskStatus taskStatus = status != null ? TaskStatus.valueOf(status) : null;
        return taskUseCase.list(projectId, taskStatus);
    }

    @GetMapping("/{taskNumber}")
    Task findByNumber(@PathVariable int taskNumber) {
        return taskUseCase.findByNumber(taskNumber)
                .orElseThrow(() -> new TaskNotFoundException(taskNumber));
    }

    @PatchMapping("/{taskNumber}")
    Task update(@PathVariable int taskNumber, @RequestBody UpdateTaskRequest req) {
        if (req.status() != null) {
            return taskUseCase.updateStatus(taskNumber, TaskStatus.valueOf(req.status()));
        }
        TaskPriority priority = req.priority() != null
                ? TaskPriority.valueOf(req.priority()) : null;
        return taskUseCase.update(taskNumber, req.title(), req.body(),
                priority, req.labels());
    }

    @DeleteMapping("/{taskNumber}")
    ResponseEntity<Void> delete(@PathVariable int taskNumber) {
        taskUseCase.delete(taskNumber);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(TaskNotFoundException.class)
    ResponseEntity<Map<String, String>> handleNotFound(TaskNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }
}
