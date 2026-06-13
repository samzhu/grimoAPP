package io.github.samzhu.grimo.project;

import java.nio.file.Path;

/**
 * Backend-normalized options for the native folder chooser gateway.
 *
 * @param initialPath directory hint for the native dialog
 * @param title user-readable dialog title
 * @see NativeFolderDialogGateway
 */
public record NativeFolderDialogOptions(Path initialPath, String title) {
}
