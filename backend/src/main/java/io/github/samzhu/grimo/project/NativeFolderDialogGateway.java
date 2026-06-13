package io.github.samzhu.grimo.project;

/**
 * Boundary for selecting a local folder without coupling tests to a real OS window.
 *
 * @see SwingNativeFolderDialogGateway
 */
public interface NativeFolderDialogGateway {

	NativeFolderSelection chooseDirectory(NativeFolderDialogOptions options);
}
