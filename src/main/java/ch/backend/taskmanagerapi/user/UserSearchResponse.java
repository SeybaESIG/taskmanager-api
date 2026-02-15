package ch.backend.taskmanagerapi.user;

// Represents a response containing user search information.
public record UserSearchResponse(
        String username,
        String email
) {
}
