package io.github.samzhu.grimo.project.adapter.in.web;

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
import org.springframework.web.bind.annotation.RestController;

import io.github.samzhu.grimo.project.application.port.in.ProjectUseCase;
import io.github.samzhu.grimo.project.domain.DuplicateProjectNameException;
import io.github.samzhu.grimo.project.domain.Project;
import io.github.samzhu.grimo.project.domain.ProjectNotFoundException;

@RestController
@RequestMapping("/api/projects")
class ProjectRestController {

    private final ProjectUseCase projectUseCase;

    ProjectRestController(ProjectUseCase projectUseCase) {
        this.projectUseCase = projectUseCase;
    }

    record CreateProjectRequest(String name, String workDir, @Nullable String description) {}
    record UpdateProjectRequest(@Nullable String name, @Nullable String workDir,
                                @Nullable String description) {}

    @PostMapping
    ResponseEntity<Project> create(@RequestBody CreateProjectRequest req) {
        var project = projectUseCase.create(req.name(), req.workDir(), req.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(project);
    }

    @GetMapping
    List<Project> listAll() {
        return projectUseCase.listAll();
    }

    @GetMapping("/{id}")
    Project findById(@PathVariable String id) {
        return projectUseCase.findById(id)
                .orElseThrow(() -> new ProjectNotFoundException(id));
    }

    @PatchMapping("/{id}")
    Project update(@PathVariable String id, @RequestBody UpdateProjectRequest req) {
        return projectUseCase.update(id, req.name(), req.workDir(), req.description());
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable String id) {
        projectUseCase.delete(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(ProjectNotFoundException.class)
    ResponseEntity<Map<String, String>> handleNotFound(ProjectNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(DuplicateProjectNameException.class)
    ResponseEntity<Map<String, String>> handleConflict(DuplicateProjectNameException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }
}
