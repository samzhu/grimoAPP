package io.github.samzhu.grimo.project;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Result returned after the native folder dialog either selects or cancels.
 *
 * @param selected true when the user selected a valid Project path
 * @param projectPath normalized absolute path, present only when selected is true
 * @see NativeFolderDialogService
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NativeFolderDialogResponse(boolean selected, String projectPath) {

	static NativeFolderDialogResponse selected(String projectPath) {
		return new NativeFolderDialogResponse(true, projectPath);
	}

	static NativeFolderDialogResponse cancelled() {
		return new NativeFolderDialogResponse(false, null);
	}
}
