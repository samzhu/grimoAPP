package io.github.samzhu.grimo.task;

import io.github.samzhu.grimo.project.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Converts manual Task API failures into user-readable JSON responses.
 *
 * @see TaskController
 */
@RestControllerAdvice(assignableTypes = TaskController.class)
public class TaskErrorHandler {

	private static final Logger logger = LoggerFactory.getLogger(TaskErrorHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorResponse> validationError() {
		logger.atWarn().log("task.request.validation_failed");
		return ResponseEntity.badRequest().body(new ErrorResponse("Task 欄位長度超過限制"));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<ErrorResponse> invalidRequest(IllegalArgumentException exception) {
		logger.atWarn()
				.addKeyValue("reason", exception.getMessage())
				.log("task.request.invalid");
		return ResponseEntity.badRequest().body(new ErrorResponse(exception.getMessage()));
	}

	@ExceptionHandler(MissingProjectException.class)
	ResponseEntity<ErrorResponse> missingProject() {
		logger.atWarn().log("task.request.missing_project");
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("找不到 Project"));
	}
}
