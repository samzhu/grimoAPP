package io.github.samzhu.grimo.project;

/**
 * User-readable API error body.
 *
 * @param error Traditional Chinese message safe to show in the UI
 * @see ProjectErrorHandler
 */
public record ErrorResponse(String error) {
}
