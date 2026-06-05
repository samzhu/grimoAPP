package io.github.samzhu.grimo.task;

import java.net.URI;

import io.github.samzhu.grimo.project.CollectionResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for manual Task creation under an existing Project.
 *
 * @see TaskService
 */
@RestController
@RequestMapping("/api/projects/{projectId}/tasks")
public class TaskController {

	private final TaskService taskService;

	public TaskController(TaskService taskService) {
		this.taskService = taskService;
	}

	@GetMapping
	CollectionResponse<TaskResponse> listTasks(@PathVariable String projectId) {
		return new CollectionResponse<>(taskService.listTasks(projectId));
	}

	@PostMapping
	ResponseEntity<TaskResponse> createTask(
			@PathVariable String projectId,
			@Valid @RequestBody CreateTaskRequest request
	) {
		TaskResponse task = taskService.createTask(projectId, request);
		return ResponseEntity.created(URI.create("/api/projects/" + projectId + "/tasks/" + task.id())).body(task);
	}
}
