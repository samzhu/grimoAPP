package io.github.samzhu.grimo.project;

/**
 * User-provided hint for opening the Project path native folder dialog.
 *
 * @param initialPath optional directory hint for the first dialog location
 * @param title optional dialog title shown by the native chooser
 * @see NativeFolderDialogController
 */
public record NativeFolderDialogRequest(String initialPath, String title) {
}
