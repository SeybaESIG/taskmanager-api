package ch.backend.taskmanagerapi.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

// Repository for user persistence, lookup, and uniqueness checks.
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    // Finds a user by email.
    Optional<User> findByEmail(String email);

    // Finds a user by username.
    Optional<User> findByUsername(String username);

    // Checks username uniqueness.
    boolean existsByUsername(String username);

    // Checks email uniqueness.
    boolean existsByEmail(String email);
}
