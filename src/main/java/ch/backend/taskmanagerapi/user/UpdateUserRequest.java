package ch.backend.taskmanagerapi.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO representing the payload used to update the authenticated user's profile.
 * All fields are optional to support partial updates (PATCH semantics).
 */
@Getter
@Setter
public class UpdateUserRequest {
    @Email
    @Size(max = 254)
    private String email;
}
