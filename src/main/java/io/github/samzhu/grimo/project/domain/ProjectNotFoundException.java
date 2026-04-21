package io.github.samzhu.grimo.project.domain;

/**
 * Thrown when a project is not found by ID.
 */
public class ProjectNotFoundException extends RuntimeException {

    public ProjectNotFoundException(String id) {
        super("Project not found: " + id);
    }
}
