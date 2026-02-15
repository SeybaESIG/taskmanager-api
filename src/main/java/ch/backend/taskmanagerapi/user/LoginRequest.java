package ch.backend.taskmanagerapi.user;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO representing the credentials provided to authenticate a user.
 * The identifier can be either a username or an email address.
 */
@Getter
@Setter
public class LoginRequest {

    @NotBlank
    private String identifier;

    @NotBlank
    private String password;
}
