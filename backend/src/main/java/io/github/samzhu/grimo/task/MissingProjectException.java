package io.github.samzhu.grimo.task;

/**
 * Signals that a nested Project Task API request targets an unknown Project.
 *
 * @see TaskService
 */
public class MissingProjectException extends RuntimeException {
}
