package io.github.samzhu.grimo.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Lists local directories for the Project Creation Page folder picker.
 *
 * @see LocalDirectoryController
 */
@Service
public class LocalDirectoryService {

	private static final Logger logger = LoggerFactory.getLogger(LocalDirectoryService.class);

	public LocalDirectoryResponse listDirectories(String requestedPath) {
		Path path = resolvePath(requestedPath);
		if (!Files.exists(path) || !Files.isDirectory(path) || !Files.isReadable(path)) {
			logger.warn("localDirectory.invalid path={}", path);
			throw new IllegalArgumentException("請選擇有效的本機資料夾");
		}

		try (Stream<Path> children = Files.list(path)) {
			List<LocalDirectoryEntryResponse> directories = children
					.filter(Files::isDirectory)
					.filter(Files::isReadable)
					.sorted(Comparator.comparing(child -> child.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
					.map(child -> new LocalDirectoryEntryResponse(child.getFileName().toString(), child.toAbsolutePath().normalize().toString()))
					.toList();
			Path parent = path.getParent();
			return new LocalDirectoryResponse(
					path.toAbsolutePath().normalize().toString(),
					parent == null ? null : parent.toAbsolutePath().normalize().toString(),
					directories
			);
		}
		catch (IOException exception) {
			logger.warn("localDirectory.unreadable path={}", path, exception);
			throw new IllegalArgumentException("請選擇有效的本機資料夾");
		}
	}

	private Path resolvePath(String requestedPath) {
		String path = requestedPath == null || requestedPath.trim().isEmpty()
				? System.getProperty("user.home")
				: requestedPath.trim();
		return Path.of(path).toAbsolutePath().normalize();
	}
}
