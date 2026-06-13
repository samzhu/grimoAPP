package io.github.samzhu.grimo.project;

/**
 * Indicates the local runtime cannot currently show a native folder dialog.
 *
 * @see ProjectErrorHandler
 */
public class NativeFolderDialogUnavailableException extends RuntimeException {

	NativeFolderDialogUnavailableException(String message) {
		super(message);
	}
}
