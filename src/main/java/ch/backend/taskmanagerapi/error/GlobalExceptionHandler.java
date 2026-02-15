package ch.backend.taskmanagerapi.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for REST controllers.
 * Centralizes error handling and ensures consistent error responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Converts @Valid field errors into a field->message map.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();

        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error -> fieldErrors.put(error.getField(), error.getDefaultMessage()));

        ApiError apiError = new ApiError(
                HttpStatus.BAD_REQUEST,
                "Validation failed for request.",
                fieldErrors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(apiError);
    }

    // Maps business IllegalArgumentException messages to the appropriate HTTP status.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgumentException(IllegalArgumentException ex) {
        HttpStatus status = resolveStatusForIllegalArgument(ex.getMessage());
        ApiError apiError = new ApiError(
                status,
                ex.getMessage(),
                null
        );

        return ResponseEntity.status(status).body(apiError);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResourceFoundException(NoResourceFoundException ex) {
        ApiError error = new ApiError(
                HttpStatus.NOT_FOUND,
                "Resource not found.",
                null
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        ApiError error = new ApiError(
                HttpStatus.METHOD_NOT_ALLOWED,
                "HTTP method not allowed for this endpoint.",
                null
        );
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(error);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        ApiError error = new ApiError(
                HttpStatus.BAD_REQUEST,
                "Malformed request body.",
                null
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ApiError error = new ApiError(
                HttpStatus.BAD_REQUEST,
                "Invalid request parameter type.",
                Map.of(ex.getName(), "Invalid value for parameter '" + ex.getName() + "'.")
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingServletRequestParameter(MissingServletRequestParameterException ex) {
        ApiError error = new ApiError(
                HttpStatus.BAD_REQUEST,
                "Missing required request parameter.",
                Map.of(ex.getParameterName(), "Parameter '" + ex.getParameterName() + "' is required.")
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Handles file upload exceptions when the uploaded file exceeds the maximum allowed size.
     * @return a structured error response with HTTP 413 Payload Too Large
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        ApiError error = new ApiError(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "File exceeds maximum allowed size.",
                Map.of("file", "Maximum allowed size is 2MB.")
        );
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(error);
    }

    @ExceptionHandler(ResourceOwnershipException.class)
    public ResponseEntity<ApiError> handleResourceOwnership(ResourceOwnershipException ex) {
        ApiError error = new ApiError(
                HttpStatus.FORBIDDEN,
                ex.getMessage(),
                Map.of("ownership", "You are not allowed to access this task.")
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    // Last-resort handler to avoid leaking internals in unexpected failures.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGenericException(Exception ex) {
        ApiError apiError = new ApiError(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred.",
                null
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(apiError);
    }

    // Maps business IllegalArgumentException messages to the appropriate HTTP status.
    private HttpStatus resolveStatusForIllegalArgument(String message) {
        if (message == null || message.isBlank()) {
            return HttpStatus.BAD_REQUEST;
        }

        String normalized = message.toLowerCase();

        if (normalized.contains("not found")) {
            return HttpStatus.NOT_FOUND;
        }
        if (normalized.contains("access denied") || normalized.contains("not owned")) {
            return HttpStatus.FORBIDDEN;
        }
        if (normalized.contains("already")
                || normalized.contains("in use")
                || normalized.contains("taken")
                || normalized.contains("cannot remove")) {
            return HttpStatus.CONFLICT;
        }
        if (normalized.contains("invalid")
                || normalized.contains("must")
                || normalized.contains("does not belong")) {
            return HttpStatus.BAD_REQUEST;
        }

        return HttpStatus.BAD_REQUEST;
    }

}
