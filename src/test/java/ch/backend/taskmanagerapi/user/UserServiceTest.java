package ch.backend.taskmanagerapi.user;

import ch.backend.taskmanagerapi.config.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    // Setup: create service with mocked dependencies.
    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
    }

    // Test: registration succeeds with unique username/email.
    @Test
    void shouldRegisterUserSuccessfully() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("StrongP@ss1")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });

        User created = userService.registerUser("alice", "alice@example.com", "StrongP@ss1");

        assertEquals(1L, created.getId());
        assertEquals("alice", created.getUsername());
        assertEquals("alice@example.com", created.getEmail());
        assertEquals(Role.USER, created.getRole());
        assertEquals("encoded", created.getPassword());
    }

    // Test: registration rejects duplicate username.
    @Test
    void shouldRejectDuplicateUsernameOnRegister() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> userService.registerUser("alice", "alice@example.com", "StrongP@ss1")
        );

        assertEquals("Username is already taken.", ex.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    // Test: registration rejects duplicate email.
    @Test
    void shouldRejectDuplicateEmailOnRegister() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> userService.registerUser("alice", "alice@example.com", "StrongP@ss1")
        );

        assertEquals("Email is already in use.", ex.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    // Test: authentication succeeds with username identifier.
    @Test
    void shouldAuthenticateWithUsername() {
        User user = user(1L, "alice", "alice@example.com", Role.USER, "encoded");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("StrongP@ss1", "encoded")).thenReturn(true);

        User authenticated = userService.authenticate("alice", "StrongP@ss1");

        assertEquals(1L, authenticated.getId());
        verify(userRepository, never()).findByEmail(any());
    }

    // Test: authentication falls back to email lookup.
    @Test
    void shouldAuthenticateWithEmailFallback() {
        User user = user(1L, "alice", "alice@example.com", Role.USER, "encoded");
        when(userRepository.findByUsername("alice@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("StrongP@ss1", "encoded")).thenReturn(true);

        User authenticated = userService.authenticate("alice@example.com", "StrongP@ss1");

        assertEquals("alice", authenticated.getUsername());
    }

    // Test: authentication fails when user is missing.
    @Test
    void shouldRejectAuthenticationWhenUserNotFound() {
        when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("missing")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> userService.authenticate("missing", "StrongP@ss1")
        );

        assertEquals("Invalid username/email or password.", ex.getMessage());
    }

    // Test: authentication fails when password does not match.
    @Test
    void shouldRejectAuthenticationWhenPasswordDoesNotMatch() {
        User user = user(1L, "alice", "alice@example.com", Role.USER, "encoded");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongP@ss1", "encoded")).thenReturn(false);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> userService.authenticate("alice", "WrongP@ss1")
        );

        assertEquals("Invalid username/email or password.", ex.getMessage());
    }

    // Test: profile update rejects unchanged email value.
    @Test
    void shouldRejectProfileUpdateWhenEmailIsUnchanged() {
        User user = user(1L, "alice", "alice@example.com", Role.USER, "encoded");
        UpdateUserRequest request = new UpdateUserRequest();
        request.setEmail("alice@example.com");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> userService.updateProfile(user, request)
        );

        assertEquals("New email must be different from current email.", ex.getMessage());
    }

    // Test: profile update rejects email already used by another account.
    @Test
    void shouldRejectProfileUpdateWhenEmailAlreadyUsed() {
        User user = user(1L, "alice", "alice@example.com", Role.USER, "encoded");
        UpdateUserRequest request = new UpdateUserRequest();
        request.setEmail("taken@example.com");
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> userService.updateProfile(user, request)
        );

        assertEquals("Email is already in use.", ex.getMessage());
    }

    // Test: profile update accepts a valid new unique email.
    @Test
    void shouldUpdateProfileEmailSuccessfully() {
        User user = user(1L, "alice", "alice@example.com", Role.USER, "encoded");
        UpdateUserRequest request = new UpdateUserRequest();
        request.setEmail("new@example.com");

        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User updated = userService.updateProfile(user, request);

        assertEquals("new@example.com", updated.getEmail());
    }

    // Test: admin delete succeeds for a normal user.
    @Test
    void shouldDeleteNormalUserAsAdmin() {
        User user = user(10L, "normal", "normal@example.com", Role.USER, "encoded");
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        userService.adminDeleteUser(10L);

        verify(userRepository).delete(user);
    }

    // Test: admin delete rejects deleting another admin.
    @Test
    void shouldRejectDeletingAdminUserAsAdmin() {
        User user = user(11L, "admin", "admin@example.com", Role.ADMIN, "encoded");
        when(userRepository.findById(11L)).thenReturn(Optional.of(user));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> userService.adminDeleteUser(11L)
        );

        assertEquals("Access denied: admins cannot be deleted.", ex.getMessage());
        verify(userRepository, never()).delete(any(User.class));
    }

    // Test: admin delete rejects unknown user id.
    @Test
    void shouldRejectDeletingUnknownUserAsAdmin() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> userService.adminDeleteUser(404L)
        );

        assertEquals("User not found.", ex.getMessage());
    }

    // Test: admin listing rejects invalid pagination/sorting inputs.
    @Test
    void shouldRejectInvalidAdminUserPaginationAndSort() {
        IllegalArgumentException pageEx = assertThrows(
                IllegalArgumentException.class,
                () -> userService.getUsersForAdmin(-1, 20, "username", "ASC", null, null, null, null)
        );
        assertEquals("Invalid pagination parameters.", pageEx.getMessage());

        IllegalArgumentException sortFieldEx = assertThrows(
                IllegalArgumentException.class,
                () -> userService.getUsersForAdmin(0, 20, "role", "ASC", null, null, null, null)
        );
        assertEquals("Invalid sort field. Allowed: username, email, creationDate.", sortFieldEx.getMessage());

        IllegalArgumentException directionEx = assertThrows(
                IllegalArgumentException.class,
                () -> userService.getUsersForAdmin(0, 20, "username", "UP", null, null, null, null)
        );
        assertEquals("Invalid sort direction. Use ASC or DESC.", directionEx.getMessage());
    }

    // Test: user search maps domain users to limited response payload.
    @Test
    void shouldSearchUsersAndMapToLimitedResponse() {
        User current = user(1L, "owner", "owner@example.com", Role.USER, "encoded");
        User other = user(2L, "other", "other@example.com", Role.USER, "encoded");

        when(userRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(other)));

        Page<UserSearchResponse> result = userService.searchUsers(current, 0, 20, "oth", "example.com");

        assertEquals(1, result.getTotalElements());
        assertEquals("other", result.getContent().get(0).username());
        assertEquals("other@example.com", result.getContent().get(0).email());
    }

    // Test: user search rejects invalid pagination.
    @Test
    void shouldRejectInvalidSearchPagination() {
        User current = user(1L, "owner", "owner@example.com", Role.USER, "encoded");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> userService.searchUsers(current, 0, 0, null, null)
        );

        assertEquals("Invalid pagination parameters.", ex.getMessage());
    }

    // Test: registration uses password encoder before persisting user.
    @Test
    void shouldUsePasswordEncoderDuringRegistration() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("StrongP@ss1")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        userService.registerUser("alice", "alice@example.com", "StrongP@ss1");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertTrue(captor.getValue().getCreationDate() == null || captor.getValue().getCreationDate() instanceof Instant);
        assertEquals("encoded", captor.getValue().getPassword());
        verify(passwordEncoder).encode(eq("StrongP@ss1"));
    }

    // Helper: build a test user fixture.
    private User user(Long id, String username, String email, Role role, String password) {
        return User.builder()
                .id(id)
                .username(username)
                .email(email)
                .role(role)
                .password(password)
                .build();
    }
}
