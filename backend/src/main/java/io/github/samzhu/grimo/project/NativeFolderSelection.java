package io.github.samzhu.grimo.project;

import java.nio.file.Path;

/**
 * Internal native dialog outcome before API response mapping.
 *
 * @see NativeFolderDialogGateway
 * @see NativeFolderDialogService
 */
public sealed interface NativeFolderSelection permits
		NativeFolderSelection.Selected,
		NativeFolderSelection.Cancelled,
		NativeFolderSelection.Unavailable {

	/**
	 * A directory was selected by the user.
	 *
	 * @param path selected directory path from the native dialog
	 */
	record Selected(Path path) implements NativeFolderSelection {
	}

	/**
	 * The user closed or cancelled the native dialog.
	 */
	record Cancelled() implements NativeFolderSelection {
	}

	/**
	 * The runtime cannot show a native dialog.
	 *
	 * @param message user-readable fallback message
	 */
	record Unavailable(String message) implements NativeFolderSelection {
	}
}
