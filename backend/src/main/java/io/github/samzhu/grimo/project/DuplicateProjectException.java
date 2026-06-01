package io.github.samzhu.grimo.project;

/**
 * Raised when a Project Workspace is already bound to an existing Project.
 *
 * @see ProjectService
 */
public class DuplicateProjectException extends RuntimeException {

	DuplicateProjectException(String workspacePath) {
		super("Project workspace already exists: " + workspacePath);
	}
}
