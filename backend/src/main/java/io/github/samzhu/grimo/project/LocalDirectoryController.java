package io.github.samzhu.grimo.project;

import org.springframework.web.bind.annotation.GetMapping;
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
	LocalDirectoryResponse listLocalDirectories(@RequestParam(required = false) String path) {
		return localDirectoryService.listDirectories(path);
	}
}
