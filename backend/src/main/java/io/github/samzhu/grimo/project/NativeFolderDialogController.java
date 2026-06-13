package io.github.samzhu.grimo.project;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API that lets Project Creation ask the local backend for a native folder choice.
 *
 * @see NativeFolderDialogService
 */
@RestController
@RequestMapping("/api/native-folder-dialogs")
public class NativeFolderDialogController {

	private final NativeFolderDialogService service;

	public NativeFolderDialogController(NativeFolderDialogService service) {
		this.service = service;
	}

	@PostMapping("/project-path")
	NativeFolderDialogResponse chooseProjectPath(@RequestBody(required = false) NativeFolderDialogRequest request) {
		return service.chooseProjectPath(request);
	}
}
