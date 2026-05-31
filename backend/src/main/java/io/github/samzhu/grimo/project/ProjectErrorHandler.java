package io.github.samzhu.grimo.project;

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
@RestControllerAdvice(assignableTypes = ProjectController.class)
public class ProjectErrorHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorResponse> validationError() {
		return ResponseEntity.badRequest().body(new ErrorResponse("請填寫專案名稱與對應資料夾"));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<ErrorResponse> invalidRequest(IllegalArgumentException exception) {
		return ResponseEntity.badRequest().body(new ErrorResponse(exception.getMessage()));
	}

	@ExceptionHandler(DuplicateProjectException.class)
	ResponseEntity<ErrorResponse> duplicateProject() {
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ErrorResponse("這個資料夾已綁定到既有專案"));
	}
}
