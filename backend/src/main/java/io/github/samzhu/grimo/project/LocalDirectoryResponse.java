package io.github.samzhu.grimo.project;

import java.util.List;

/**
 * Read-only local directory listing returned to the folder picker UI.
 *
 * @param path absolute path currently being viewed
 * @param parentPath parent directory path, or {@code null} at filesystem root
 * @param directories immediate child directories sorted by name
 * @see LocalDirectoryController
 */
public record LocalDirectoryResponse(
		String path,
		String parentPath,
		List<LocalDirectoryEntryResponse> directories
) {
}
