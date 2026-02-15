package ch.backend.taskmanagerapi.user;

import ch.backend.taskmanagerapi.config.Role;
import lombok.AllArgsConstructor;
import lombok.Getter;

// Represents a response containing authentication information.
@Getter
@AllArgsConstructor
public class AuthResponse {

    private String accessToken;
    private Long userId;
    private String username;
    private String email;
    private Role role;
}
