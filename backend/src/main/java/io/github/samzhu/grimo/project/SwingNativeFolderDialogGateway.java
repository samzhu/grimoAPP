package io.github.samzhu.grimo.project;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;

import org.springframework.stereotype.Component;

/**
 * Java desktop implementation of the local native folder dialog bridge.
 *
 * @see NativeFolderDialogGateway
 */
@Component
public class SwingNativeFolderDialogGateway implements NativeFolderDialogGateway {

	private static final String FALLBACK_MESSAGE = "無法開啟系統資料夾選擇器，請手動貼上路徑";

	@Override
	public NativeFolderSelection chooseDirectory(NativeFolderDialogOptions options) {
		if (GraphicsEnvironment.isHeadless()) {
			return new NativeFolderSelection.Unavailable(FALLBACK_MESSAGE);
		}

		AtomicReference<NativeFolderSelection> selection = new AtomicReference<>();
		try {
			SwingUtilities.invokeAndWait(() -> selection.set(openChooser(options)));
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return new NativeFolderSelection.Unavailable(FALLBACK_MESSAGE);
		}
		catch (InvocationTargetException exception) {
			return new NativeFolderSelection.Unavailable(FALLBACK_MESSAGE);
		}
		return selection.get();
	}

	private NativeFolderSelection openChooser(NativeFolderDialogOptions options) {
		JFileChooser chooser = new JFileChooser(options.initialPath().toFile());
		chooser.setDialogTitle(options.title());
		// S013 only chooses a Project path; reading files or creating Projects stays out of this bridge.
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setAcceptAllFileFilterUsed(false);
		int result = chooser.showOpenDialog(null);
		if (result != JFileChooser.APPROVE_OPTION) {
			return new NativeFolderSelection.Cancelled();
		}
		File selectedFile = chooser.getSelectedFile();
		if (selectedFile == null) {
			return new NativeFolderSelection.Cancelled();
		}
		return new NativeFolderSelection.Selected(selectedFile.toPath());
	}
}
