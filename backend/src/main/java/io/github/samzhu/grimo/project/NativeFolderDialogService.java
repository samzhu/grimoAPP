package io.github.samzhu.grimo.project;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Maps native folder dialog outcomes into the Project path API contract.
 *
 * @see NativeFolderDialogGateway
 * @see NativeFolderDialogController
 */
@Service
public class NativeFolderDialogService {

	private static final Logger logger = LoggerFactory.getLogger(NativeFolderDialogService.class);
	private static final String DEFAULT_TITLE = "選擇 Project 資料夾";

	private final NativeFolderDialogGateway gateway;

	public NativeFolderDialogService(NativeFolderDialogGateway gateway) {
		this.gateway = gateway;
	}

	public NativeFolderDialogResponse chooseProjectPath(NativeFolderDialogRequest request) {
		NativeFolderDialogOptions options = new NativeFolderDialogOptions(
				resolveInitialPath(request == null ? null : request.initialPath()),
				resolveTitle(request == null ? null : request.title())
		);
		return switch (gateway.chooseDirectory(options)) {
			case NativeFolderSelection.Selected selected -> selectedResponse(selected.path());
			case NativeFolderSelection.Cancelled ignored -> NativeFolderDialogResponse.cancelled();
			case NativeFolderSelection.Unavailable unavailable -> throw unavailable(unavailable.message());
		};
	}

	private NativeFolderDialogResponse selectedResponse(Path selectedPath) {
		Path normalized = selectedPath.toAbsolutePath().normalize();
		if (!Files.exists(normalized) || !Files.isDirectory(normalized) || !Files.isReadable(normalized)) {
			logger.atWarn()
					.addKeyValue("projectPath", normalized)
					.log("nativeFolderDialog.invalid_selected_path");
			throw new IllegalArgumentException("請選擇有效的本機資料夾");
		}
		logger.atInfo()
				.addKeyValue("projectPath", normalized)
				.log("nativeFolderDialog.selected");
		return NativeFolderDialogResponse.selected(normalized.toString());
	}

	private NativeFolderDialogUnavailableException unavailable(String message) {
		String fallbackMessage = message == null || message.isBlank()
				? "無法開啟系統資料夾選擇器，請手動貼上路徑"
				: message;
		logger.atWarn()
				.addKeyValue("reason", fallbackMessage)
				.log("nativeFolderDialog.unavailable");
		return new NativeFolderDialogUnavailableException(fallbackMessage);
	}

	private static Path resolveInitialPath(String requestedPath) {
		String fallbackPath = System.getProperty("user.home");
		if (requestedPath == null || requestedPath.isBlank()) {
			return Path.of(fallbackPath).toAbsolutePath().normalize();
		}
		try {
			Path path = Path.of(requestedPath.trim()).toAbsolutePath().normalize();
			return Files.isDirectory(path) ? path : Path.of(fallbackPath).toAbsolutePath().normalize();
		}
		catch (InvalidPathException exception) {
			return Path.of(fallbackPath).toAbsolutePath().normalize();
		}
	}

	private static String resolveTitle(String title) {
		return title == null || title.isBlank() ? DEFAULT_TITLE : title.trim();
	}
}
