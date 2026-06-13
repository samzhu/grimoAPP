package io.github.samzhu.grimo.project;

/**
 * Request to create one child directory under the folder browser's current path.
 *
 * @param parentPath current folder browser location from {@link LocalDirectoryResponse#path()}
 * @param name single-level folder name typed by the user
 * @see LocalDirectoryController
 */
public record LocalDirectoryCreateRequest(String parentPath, String name) {
}
