package io.github.samzhu.grimo.task;

import java.util.List;

import jakarta.validation.constraints.Size;

/**
 * HTTP request body for creating a manual Project-owned Task.
 *
 * @param title Task title typed in the Create Task dialog
 * @param body optional first context typed in the Create Task dialog
 * @param labels optional labels typed in the Create Task dialog
 * @see TaskController
 */
public record CreateTaskRequest(
		@Size(max = 140) String title,
		@Size(max = 8000) String body,
		List<@Size(max = 40) String> labels
) {
}
