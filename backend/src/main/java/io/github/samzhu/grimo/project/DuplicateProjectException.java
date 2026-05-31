package io.github.samzhu.grimo.project;

/**
 * Raised when a local folder is already bound to an existing Project.
 *
 * @see ProjectService
 */
public class DuplicateProjectException extends RuntimeException {

	DuplicateProjectException(String folderPath) {
		super("Project folder already exists: " + folderPath);
	}
}
