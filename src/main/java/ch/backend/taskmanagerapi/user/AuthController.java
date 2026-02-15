package ch.backend.taskmanagerapi.user;

import ch.backend.taskmanagerapi.config.JwtUtil;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// REST controller exposing authentication endpoints for registration and login.
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    public AuthController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    // Registers a new user with default USER role and hashed password.
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody CreateUserRequest request
    ) {
        User createdUser = userService.registerUser(
                request.getUsername(),
                request.getEmail(),
                request.getPassword()
        );

        UserResponse response = new UserResponse(
                createdUser.getId(),
                createdUser.getUsername(),
                createdUser.getEmail(),
                createdUser.getRole()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Logs in a user and returns a JWT token.
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        // Identifier supports username or email depending on user input.
        User authenticatedUser = userService.authenticate(
                request.getIdentifier(),
                request.getPassword()
        );

        // Token subject is username; this is what the JWT filter resolves on each request.
        String token = jwtUtil.generateToken(authenticatedUser.getUsername());

        AuthResponse response = new AuthResponse(
                token,
                authenticatedUser.getId(),
                authenticatedUser.getUsername(),
                authenticatedUser.getEmail(),
                authenticatedUser.getRole()
        );

        return ResponseEntity.ok(response);
    }
}
