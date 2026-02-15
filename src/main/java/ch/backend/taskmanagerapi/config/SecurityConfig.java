package ch.backend.taskmanagerapi.config;

import ch.backend.taskmanagerapi.user.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for HTTP endpoints.
 * This initial version disables form login and allows public access to authentication endpoints.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService customUserDetailsService;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          CustomUserDetailsService customUserDetailsService) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.customUserDetailsService = customUserDetailsService;
    }

    // Exposes DB-backed user loading so role authorities can be resolved in access checks.
    @Bean
    public UserDetailsService userDetailsService() {
        return customUserDetailsService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF is disabled for stateless REST APIs using token-based authentication
                .csrf(csrf -> csrf.disable())
                // Use stateless sessions because authentication is handled via JWT
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints for registration and login
                        .requestMatchers("/auth/**").permitAll()
                        // Admin endpoints require ADMIN role (i.e. authority ROLE_ADMIN)
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // Allow access to error path
                        .requestMatchers("/error").permitAll()
                        // Everything else requires authentication via a valid JWT
                        .anyRequest().authenticated()
                )
                // Disable default login form and HTTP Basic auth
                .formLogin(form -> form.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                // Ensures authentication lookups use the custom DB-backed user details service.
                .userDetailsService(customUserDetailsService);

        // Inject the JWT filter before the standard UsernamePasswordAuthenticationFilter
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
