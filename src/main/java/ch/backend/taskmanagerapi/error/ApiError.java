package ch.backend.taskmanagerapi.error;

import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

/**
 * Standard error payload returned by the REST API.
 * Can be extended with additional metadata if needed.
 */
public class ApiError {

    private final Instant timestamp = Instant.now();
    private final int status;
    private final String error;
    private final String message;
    private final Map<String, String> fieldErrors;

    public ApiError(HttpStatus status, String message, Map<String, String> fieldErrors) {
        this.status = status.value();
        this.error = status.getReasonPhrase();
        this.message = message;
        this.fieldErrors = fieldErrors;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
