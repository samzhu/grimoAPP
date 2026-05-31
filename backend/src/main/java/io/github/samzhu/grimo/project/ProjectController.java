package io.github.samzhu.grimo.project;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for Project onboarding and workflow recipe selection.
 *
 * @see ProjectService
 * @see WorkflowRecipeCatalog
 */
@RestController
@RequestMapping("/api")
public class ProjectController {

	private final ProjectService projectService;
	private final WorkflowRecipeCatalog workflowRecipeCatalog;

	public ProjectController(ProjectService projectService, WorkflowRecipeCatalog workflowRecipeCatalog) {
		this.projectService = projectService;
		this.workflowRecipeCatalog = workflowRecipeCatalog;
	}

	@GetMapping("/workflow-recipes")
	List<WorkflowRecipeResponse> listWorkflowRecipes() {
		return workflowRecipeCatalog.list();
	}

	@GetMapping("/projects")
	List<ProjectResponse> listProjects() {
		return projectService.listProjects();
	}

	@PostMapping("/projects")
	ResponseEntity<ProjectResponse> createProject(@Valid @RequestBody CreateProjectRequest request) {
		ProjectResponse project = projectService.createProject(request);
		return ResponseEntity.created(URI.create("/api/projects/" + project.id())).body(project);
	}
}
