package io.github.samzhu.grimo.project;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for read-only local directory browsing during Project creation.
 *
 * @see LocalDirectoryService
 */
@RestController
@RequestMapping("/api")
public class LocalDirectoryController {

	private final LocalDirectoryService localDirectoryService;

	public LocalDirectoryController(LocalDirectoryService localDirectoryService) {
		this.localDirectoryService = localDirectoryService;
	}

	@GetMapping("/local-directories")
	LocalDirectoryResponse listLocalDirectories(
			@RequestParam(required = false) String path,
			@RequestParam(required = false) String location) {
		return localDirectoryService.listDirectories(path, location);
	}

	@PostMapping("/local-directories")
	ResponseEntity<LocalDirectoryEntryResponse> createLocalDirectory(@RequestBody LocalDirectoryCreateRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(localDirectoryService.createDirectory(request));
	}
}
