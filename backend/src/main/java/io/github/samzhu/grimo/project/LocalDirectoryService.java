package io.github.samzhu.grimo.project;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
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
		return listDirectories(requestedPath, null);
	}

	public LocalDirectoryResponse listDirectories(String requestedPath, String requestedLocation) {
		Path path = resolvePath(requestedPath, requestedLocation);
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

	public LocalDirectoryEntryResponse createDirectory(LocalDirectoryCreateRequest request) {
		Path parent = resolveCreateParent(request == null ? null : request.parentPath());
		String name = resolveCreateName(request == null ? null : request.name());
		Path child = parent.resolve(name).toAbsolutePath().normalize();
		try {
			Files.createDirectory(child);
			return new LocalDirectoryEntryResponse(name, child.toString());
		}
		catch (FileAlreadyExistsException exception) {
			logger.warn("localDirectory.create.duplicate path={}", child);
			throw new IllegalArgumentException("資料夾已存在");
		}
		catch (IOException exception) {
			logger.warn("localDirectory.create.failed parent={} name={}", parent, name, exception);
			throw new IllegalArgumentException("無法建立資料夾");
		}
	}

	private Path resolvePath(String requestedPath, String requestedLocation) {
		String path = trimToNull(requestedPath);
		String location = trimToNull(requestedLocation);
		if (path != null && location != null) {
			throw new IllegalArgumentException("請選擇一種資料夾位置");
		}
		if ("home".equals(location)) {
			return homeRoot();
		}
		if ("default".equals(location) || (path == null && location == null)) {
			return defaultProjectRoot();
		}
		if (location != null) {
			throw new IllegalArgumentException("請選擇有效的本機資料夾");
		}
		return Path.of(path).toAbsolutePath().normalize();
	}

	private Path homeRoot() {
		return Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
	}

	private Path defaultProjectRoot() {
		Path path = Path.of(System.getProperty("user.home"), ".grimo", "projects")
				.toAbsolutePath()
				.normalize();
		try {
			Files.createDirectories(path);
			return path;
		}
		catch (IOException exception) {
			logger.warn("localDirectory.default_root.create_failed path={}", path, exception);
			throw new IllegalArgumentException("請選擇有效的本機資料夾");
		}
	}

	private static String trimToNull(String value) {
		if (value == null || value.trim().isEmpty()) {
			return null;
		}
		return value.trim();
	}

	private Path resolveCreateParent(String requestedParentPath) {
		String parentPath = trimToNull(requestedParentPath);
		if (parentPath == null) {
			throw new IllegalArgumentException("無法建立資料夾");
		}
		Path parent = Path.of(parentPath).toAbsolutePath().normalize();
		if (!Files.exists(parent) || !Files.isDirectory(parent) || !Files.isReadable(parent) || !Files.isWritable(parent)) {
			logger.warn("localDirectory.create.invalid_parent path={}", parent);
			throw new IllegalArgumentException("無法建立資料夾");
		}
		return parent;
	}

	private String resolveCreateName(String requestedName) {
		String name = trimToNull(requestedName);
		if (name == null) {
			throw new IllegalArgumentException("請輸入資料夾名稱");
		}
		Path namePath = Path.of(name);
		// S014 keeps folder creation as one child directory, not a hidden path jump.
		if (namePath.isAbsolute() || name.contains("/") || name.contains("\\") || ".".equals(name) || "..".equals(name)) {
			throw new IllegalArgumentException("資料夾名稱只能是一層資料夾名稱");
		}
		return name;
	}
}
