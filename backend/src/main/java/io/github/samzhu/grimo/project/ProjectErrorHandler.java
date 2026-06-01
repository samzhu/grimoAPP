package io.github.samzhu.grimo.project;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Converts Project onboarding failures into user-readable JSON responses.
 *
 * @see ProjectController
 */
@RestControllerAdvice(assignableTypes = { ProjectController.class, LocalDirectoryController.class })
public class ProjectErrorHandler {

	private static final Logger logger = LoggerFactory.getLogger(ProjectErrorHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorResponse> validationError() {
		logger.atWarn().log("project.request.validation_failed");
		return ResponseEntity.badRequest().body(new ErrorResponse("請填寫專案名稱與專案工作區"));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<ErrorResponse> invalidRequest(IllegalArgumentException exception) {
		logger.atWarn()
				.addKeyValue("reason", exception.getMessage())
				.log("project.request.invalid");
		return ResponseEntity.badRequest().body(new ErrorResponse(exception.getMessage()));
	}

	@ExceptionHandler(DuplicateProjectException.class)
	ResponseEntity<ErrorResponse> duplicateProject() {
		logger.atWarn().log("project.request.duplicate_workspace");
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ErrorResponse("這個工作區已綁定到既有專案"));
	}
}
