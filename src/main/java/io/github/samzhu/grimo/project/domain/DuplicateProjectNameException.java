package io.github.samzhu.grimo.project.domain;

/**
 * Thrown when creating a project with a name that already exists.
 */
public class DuplicateProjectNameException extends RuntimeException {

    public DuplicateProjectNameException(String name) {
        super("Project name already exists: " + name);
    }
}
