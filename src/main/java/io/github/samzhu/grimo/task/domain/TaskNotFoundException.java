package io.github.samzhu.grimo.task.domain;

public class TaskNotFoundException extends RuntimeException {

    public TaskNotFoundException(int taskNumber) {
        super("Task not found: #" + taskNumber);
    }

    public TaskNotFoundException(String id) {
        super("Task not found: " + id);
    }
}
