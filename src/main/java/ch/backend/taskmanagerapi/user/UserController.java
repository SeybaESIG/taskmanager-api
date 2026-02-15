package ch.backend.taskmanagerapi.user;

import ch.backend.taskmanagerapi.config.Role;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

// REST controller exposing authenticated user profile and user-search endpoints.
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    // Injects user service dependencies for profile and search operations.
    public UserController(UserService userService) {
        this.userService = userService;
    }

    // Returns the profile of the currently authenticated user.
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        Object principal = authentication.getPrincipal();

        Long id;
        String username;
        String email;
        Role role;

        if (principal instanceof User user) {
            id = user.getId();
            username = user.getUsername();
            email = user.getEmail();
            role = user.getRole();
        } else if (principal instanceof org.springframework.security.core.userdetails.User userDetails) {
            String usernameValue = userDetails.getUsername();
            User domainUser = userService.findByUsername(usernameValue)
                    .orElseThrow(() -> new IllegalStateException("Authenticated user not found in database."));
            id = domainUser.getId();
            username = domainUser.getUsername();
            email = domainUser.getEmail();
            role = domainUser.getRole();
        } else if (principal instanceof String usernameString) {
            User domainUser = userService.findByUsername(usernameString)
                    .orElseThrow(() -> new IllegalStateException("Authenticated user not found in database."));
            id = domainUser.getId();
            username = domainUser.getUsername();
            email = domainUser.getEmail();
            role = domainUser.getRole();
        } else {
            throw new IllegalStateException("Unsupported principal type: " + principal.getClass().getName());
        }

        UserResponse response = new UserResponse(
                id,
                username,
                email,
                role
        );

        return ResponseEntity.ok(response);
    }

    // Partially updates editable fields of the current user profile.
    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateMe(
            Authentication authentication,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        Object principal = authentication.getPrincipal();
        User currentUser;

        if (principal instanceof User user) {
            currentUser = user;
        } else {
            // Fallback: resolve from username if principal is not the domain User.
            String username;
            if (principal instanceof org.springframework.security.core.userdetails.User userDetails) {
                username = userDetails.getUsername();
            } else if (principal instanceof String usernameString) {
                username = usernameString;
            } else {
                throw new IllegalStateException("Unsupported principal type: " + principal.getClass().getName());
            }

            currentUser = userService.findByUsername(username)
                    .orElseThrow(() -> new IllegalStateException("Authenticated user not found in database."));
        }

        User updated = userService.updateProfile(currentUser, request);

        UserResponse response = new UserResponse(
                updated.getId(),
                updated.getUsername(),
                updated.getEmail(),
                updated.getRole()
        );

        return ResponseEntity.ok(response);
    }

    // Searches other users for collaboration and returns limited public fields.
    @GetMapping("/search")
    public ResponseEntity<Page<UserSearchResponse>> searchUsers(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email
    ) {
        User currentUser = resolveCurrentUser(authentication);
        Page<UserSearchResponse> result = userService.searchUsers(currentUser, page, size, username, email);
        return ResponseEntity.ok(result);
    }

    // Resolves the authenticated principal to the domain User entity.
    private User resolveCurrentUser(Authentication authentication) {
        Object principal = authentication.getPrincipal();

        if (principal instanceof User user) {
            return user;
        }
        if (principal instanceof org.springframework.security.core.userdetails.User userDetails) {
            String usernameValue = userDetails.getUsername();
            return userService.findByUsername(usernameValue)
                    .orElseThrow(() -> new IllegalStateException("Authenticated user not found in database."));
        }
        if (principal instanceof String usernameString) {
            return userService.findByUsername(usernameString)
                    .orElseThrow(() -> new IllegalStateException("Authenticated user not found in database."));
        }

        throw new IllegalStateException("Unsupported principal type: " + principal.getClass().getName());
    }
}
