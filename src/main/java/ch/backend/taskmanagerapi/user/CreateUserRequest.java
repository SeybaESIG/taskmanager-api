package ch.backend.taskmanagerapi.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO representing the payload required to create a new user account.
 * Validation constraints are applied to enforce basic input quality.
 */
@Setter
@Getter
public class CreateUserRequest {

    @NotBlank
    @Size(min = 3, max = 50)
    private String username;

    @NotBlank
    @Email
    @Size(max = 254)
    private String email;

    @NotBlank
    @Size(min = 8, max = 100)
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#.^()\\-_=+]).{8,100}$",
            message = "Password must contain upper and lower case letters, a digit and a special character."
    )
    private String password;
}
