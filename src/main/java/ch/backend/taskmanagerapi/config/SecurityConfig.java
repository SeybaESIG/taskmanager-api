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

// Security rules for a stateless JWT API.
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
                // CSRF is disabled because this API authenticates with Bearer JWT, not cookie sessions.
                .csrf(csrf -> csrf.disable())
                // Each request is self-authenticated; no server session is created.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        // Public auth endpoints.
                        .requestMatchers("/auth/**").permitAll()
                        // OpenAPI/Swagger docs endpoints.
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Admin endpoints require role ADMIN (authority ROLE_ADMIN).
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // Keep framework error dispatch public.
                        .requestMatchers("/error").permitAll()
                        // All business endpoints require a valid JWT.
                        .anyRequest().authenticated()
                )
                // Disable form and basic auth in favor of JWT auth only.
                .formLogin(form -> form.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                // Ensures authentication lookups use the custom DB-backed user details service.
                .userDetailsService(customUserDetailsService);

        // Inject the JWT filter before the standard UsernamePasswordAuthenticationFilter
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
