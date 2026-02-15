package ch.backend.taskmanagerapi.user;

import ch.backend.taskmanagerapi.config.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

//  Service layer that handles user business rules for registration, authentication, search, and admin operations.
@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Registers a USER account after uniqueness checks and password hashing.
    public User registerUser(String username, String email, String rawPassword) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username is already taken.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email is already in use.");
        }

        String encodedPassword = passwordEncoder.encode(rawPassword);

        User user = User.builder()
                .username(username)
                .email(email)
                .role(Role.USER)
                .password(encodedPassword)
                .build();

        return userRepository.save(user);
    }

    // Finds a user by id.
    @Transactional(readOnly = true)
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    // Finds a user by email.
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    // Finds a user by username.
    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    // Retrieves a paginated list of users for admin use.
    @Transactional(readOnly = true)
    public Page<User> getUsersForAdmin(
            int page,
            int size,
            String sortBy,
            String direction,
            String username,
            String email,
            LocalDate creationDate,
            Role role
    ) {
        if (page < 0 || size <= 0) {
            throw new IllegalArgumentException("Invalid pagination parameters.");
        }

        Set<String> sortableFields = Set.of("username", "email", "creationDate");
        if (!sortableFields.contains(sortBy)) {
            throw new IllegalArgumentException("Invalid sort field. Allowed: username, email, creationDate.");
        }

        Sort.Direction sortDirection;
        try {
            sortDirection = Sort.Direction.fromString(direction);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid sort direction. Use ASC or DESC.");
        }

        // Stable secondary sort prevents inconsistent ordering across pages.
        Sort sort = Sort.by(sortDirection, sortBy).and(Sort.by(Sort.Direction.DESC, "id"));
        Pageable pageable = PageRequest.of(page, size, sort);

        // Build optional admin filters dynamically.
        Specification<User> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            if (username != null && !username.isBlank()) {
                predicates.add(
                        cb.like(
                                cb.lower(root.get("username")),
                                "%" + username.trim().toLowerCase() + "%"
                        )
                );
            }

            if (email != null && !email.isBlank()) {
                predicates.add(
                        cb.like(
                                cb.lower(root.get("email")),
                                "%" + email.trim().toLowerCase() + "%"
                        )
                );
            }

            if (creationDate != null) {
                // Match users created during the provided UTC day window.
                Instant startOfDay = creationDate.atStartOfDay().toInstant(ZoneOffset.UTC);
                Instant startOfNextDay = creationDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
                predicates.add(cb.greaterThanOrEqualTo(root.get("creationDate"), startOfDay));
                predicates.add(cb.lessThan(root.get("creationDate"), startOfNextDay));
            }

            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        return userRepository.findAll(spec, pageable);
    }

    // Searches for users based on username and email.
    @Transactional(readOnly = true)
    public Page<UserSearchResponse> searchUsers(
            User currentUser,
            int page,
            int size,
            String username,
            String email
    ) {
        if (page < 0 || size <= 0) {
            throw new IllegalArgumentException("Invalid pagination parameters.");
        }

        // Search endpoint exposes deterministic ordering by username.
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "username"));

        Specification<User> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            // Exclude the current user from search results.
            predicates.add(cb.notEqual(root.get("id"), currentUser.getId()));

            if (username != null && !username.isBlank()) {
                predicates.add(
                        cb.like(
                                cb.lower(root.get("username")),
                                "%" + username.trim().toLowerCase() + "%"
                        )
                );
            }

            if (email != null && !email.isBlank()) {
                predicates.add(
                        cb.like(
                                cb.lower(root.get("email")),
                                "%" + email.trim().toLowerCase() + "%"
                        )
                );
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        return userRepository.findAll(spec, pageable)
                .map(user -> new UserSearchResponse(user.getUsername(), user.getEmail()));
    }

    // Authenticates by username first, then email fallback, with password hash verification.
    @Transactional(readOnly = true)
    public User authenticate(String identifier, String rawPassword) {
        Optional<User> userOpt = userRepository.findByUsername(identifier);

        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByEmail(identifier);
        }

        User user = userOpt.orElseThrow(
                () -> new IllegalArgumentException("Invalid username/email or password.")
        );

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new IllegalArgumentException("Invalid username/email or password.");
        }

        return user;
    }

    // Updates editable profile fields for the authenticated user.
    public User updateProfile(User user, UpdateUserRequest request) {
        // Email change must differ from current value and remain globally unique.
        if (request.getEmail() != null) {
            if (request.getEmail().equals(user.getEmail())) {
                throw new IllegalArgumentException("New email must be different from current email.");
            }
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new IllegalArgumentException("Email is already in use.");
            }
            user.setEmail(request.getEmail());
        }
        return userRepository.save(user);
    }

    // Deletes a non-admin user from an admin context.
    public void adminDeleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));

        // Prevent deleting an ADMIN user account
        if (user.getRole() == Role.ADMIN) {
            throw new IllegalArgumentException("Access denied: admins cannot be deleted.");
        }

        userRepository.delete(user);
    }
}
