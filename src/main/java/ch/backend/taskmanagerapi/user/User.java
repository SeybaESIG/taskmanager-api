package ch.backend.taskmanagerapi.user;

import ch.backend.taskmanagerapi.collaborate.Collaborate;
import ch.backend.taskmanagerapi.config.Role;
import ch.backend.taskmanagerapi.project.Project;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// Represents a user entity.
@Entity
@Table(
        name = "tb_users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_tb_user_username", columnNames = "username"),
                @UniqueConstraint(name = "uk_tb_user_email", columnNames = "email")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User  implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "creation_date", nullable = false, updatable = false)
    private Instant creationDate;

    @Column(nullable = false, length = 100)
    private String password;

    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Project> projects = new ArrayList<>();

    @OneToMany(mappedBy = "user")
    private List<Collaborate> collaborations = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (creationDate == null) {
            creationDate = Instant.now();
        }
    }

    // ---- UserDetails implementation ----

    // Returns the authorities granted to the user.
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Role enum: USER, ADMIN, ...
        // Expose as ROLE_USER, ROLE_ADMIN, etc.
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        // If you authenticate by email, return email instead
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
