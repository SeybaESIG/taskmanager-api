package ch.backend.taskmanagerapi.user;

import ch.backend.taskmanagerapi.config.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminUserControllerIT {

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    // Setup: initialize secured MockMvc for admin endpoint tests.
    @BeforeEach
    void setUpMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    // Test: admin can list users with filtering and sorting.
    @Test
    void shouldAllowAdminToListUsersWithFilterAndSort() throws Exception {
        register("admin_list_a");
        register("admin_list_b");

        mockMvc.perform(get("/admin/users")
                        .with(user("admin").roles("ADMIN"))
                        .param("page", "0")
                        .param("size", "20")
                        .param("username", "admin_list")
                        .param("sortBy", "username")
                        .param("direction", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    // Test: invalid admin sort field is rejected.
    @Test
    void shouldRejectInvalidSortFieldForAdminUsers() throws Exception {
        register("admin_invalid_sort");

        mockMvc.perform(get("/admin/users")
                        .with(user("admin").roles("ADMIN"))
                        .param("page", "0")
                        .param("size", "20")
                        .param("sortBy", "role")
                        .param("direction", "ASC"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid sort field. Allowed: username, email, creationDate."));
    }

    // Test: admin can delete a non-admin user.
    @Test
    void shouldAllowAdminToDeleteNormalUser() throws Exception {
        long userId = register("admin_delete_me");

        mockMvc.perform(delete("/admin/users/{id}", userId)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNoContent());
    }

    // Test: non-admin access to admin user routes is forbidden.
    @Test
    void shouldForbidNonAdminAccessToAdminUsersEndpoint() throws Exception {
        mockMvc.perform(get("/admin/users")
                        .with(user("plain_user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    // Test: deleting an admin user is forbidden.
    @Test
    void shouldForbidDeletingAdminUser() throws Exception {
        long adminUserId = register("admin_cannot_delete");
        User adminUser = userRepository.findById(adminUserId).orElseThrow();
        adminUser.setRole(Role.ADMIN);
        userRepository.save(adminUser);

        mockMvc.perform(delete("/admin/users/{id}", adminUserId)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied: admins cannot be deleted."));
    }

    // Test: deleting an unknown user returns not found.
    @Test
    void shouldReturnNotFoundWhenDeletingUnknownUser() throws Exception {
        mockMvc.perform(delete("/admin/users/{id}", 99999999L)
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found."));
    }

    // Test: admin can filter users by role and creation date.
    @Test
    void shouldFilterAdminUsersByRoleAndCreationDate() throws Exception {
        register("admin_role_filter");

        mockMvc.perform(get("/admin/users")
                        .with(user("admin").roles("ADMIN"))
                        .param("page", "0")
                        .param("size", "20")
                        .param("role", "USER")
                        .param("creationDate", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    // Test: invalid pagination and sort direction are rejected for admin listing.
    @Test
    void shouldRejectInvalidAdminPaginationOrSortDirection() throws Exception {
        mockMvc.perform(get("/admin/users")
                        .with(user("admin").roles("ADMIN"))
                        .param("page", "-1")
                        .param("size", "20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid pagination parameters."));

        mockMvc.perform(get("/admin/users")
                        .with(user("admin").roles("ADMIN"))
                        .param("page", "0")
                        .param("size", "20")
                        .param("direction", "UP"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid sort direction. Use ASC or DESC."));
    }

    // Helper: register a user and return its generated id.
    private long register(String prefix) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = prefix + "_" + suffix;
        String email = username + "@example.com";

        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "email", email,
                                "password", "StrongP@ss1"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
