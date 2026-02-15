package ch.backend.taskmanagerapi.user;

import ch.backend.taskmanagerapi.config.Role;
import lombok.Getter;

// Represents a response containing user information.
@Getter
public class UserResponse {

    private Long id;
    private String username;
    private String email;
    private Role role;

    public UserResponse(Long id, String username, String email, Role role) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.role = role;
    }

}
