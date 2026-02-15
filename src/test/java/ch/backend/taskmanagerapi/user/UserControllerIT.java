package ch.backend.taskmanagerapi.user;

import com.fasterxml.jackson.databind.JsonNode;
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
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserControllerIT {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Setup: initialize secured MockMvc for user endpoint tests.
    @BeforeEach
    void setUpMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    // Test: user search returns limited fields and supports combined filters.
    @Test
    void shouldSearchUsersByUsernameAndEmailWithLimitedFields() throws Exception {
        String ownerToken = registerAndLoginRandom("search_owner");
        registerAndLoginRandom("search_target");

        MvcResult result = mockMvc.perform(get("/users/search")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("page", "0")
                        .param("size", "20")
                        .param("username", "search_target")
                        .param("email", "example.com"))
                .andReturn();

        int statusCode = result.getResponse().getStatus();
        if (statusCode != 200) {
            throw new AssertionError("Expected 200 for /users/search but got " + statusCode
                    + " body=" + result.getResponse().getContentAsString());
        }

        mockMvc.perform(get("/users/search")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("page", "0")
                        .param("size", "20")
                        .param("username", "search_target")
                        .param("email", "example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].username").value(org.hamcrest.Matchers.containsString("search_target")))
                .andExpect(jsonPath("$.content[0].email").value(org.hamcrest.Matchers.containsString("@example.com")))
                .andExpect(jsonPath("$.content[0].id").doesNotExist())
                .andExpect(jsonPath("$.content[0].role").doesNotExist());
    }

    // Test: current authenticated user is excluded from search results.
    @Test
    void shouldExcludeCurrentUserFromSearchResults() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String ownerUsername = "search_me_" + suffix;
        String ownerEmail = ownerUsername + "@example.com";
        String targetUsername = "search_me_target_" + suffix;
        String targetEmail = targetUsername + "@example.com";

        String ownerToken = registerAndLogin(ownerUsername, ownerEmail);
        registerAndLogin(targetUsername, targetEmail);

        MvcResult result = mockMvc.perform(get("/users/search")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("page", "0")
                        .param("size", "20")
                        .param("username", "search_me"))
                .andReturn();

        int statusCode = result.getResponse().getStatus();
        if (statusCode != 200) {
            throw new AssertionError("Expected 200 for /users/search but got " + statusCode
                    + " body=" + result.getResponse().getContentAsString());
        }

        mockMvc.perform(get("/users/search")
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("page", "0")
                        .param("size", "20")
                        .param("username", "search_me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].username", hasItem(targetUsername)))
                .andExpect(jsonPath("$.content[*].username", not(hasItem(ownerUsername))));
    }

    // Test: /users/me returns the authenticated profile.
    @Test
    void shouldGetCurrentUserProfile() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "me_profile_" + suffix;
        String email = username + "@example.com";
        String token = registerAndLogin(username, email);

        mockMvc.perform(get("/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.email").value(email));
    }

    // Test: authenticated user can update profile email.
    @Test
    void shouldUpdateCurrentUserEmail() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "me_update_" + suffix;
        String email = username + "@example.com";
        String token = registerAndLogin(username, email);
        String updatedEmail = "me_updated_" + suffix + "@example.com";

        mockMvc.perform(patch("/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", updatedEmail))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(updatedEmail));
    }

    // Test: profile update rejects email already used by another account.
    @Test
    void shouldRejectUserEmailUpdateWhenAlreadyUsed() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String ownerToken = registerAndLogin("me_conflict_owner_" + suffix, "me_conflict_owner_" + suffix + "@example.com");
        String takenEmail = "me_conflict_taken_" + suffix + "@example.com";
        registerAndLogin("me_conflict_taken_" + suffix, takenEmail);

        mockMvc.perform(patch("/users/me")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", takenEmail))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email is already in use."));
    }

    // Test: /users/me requires authentication.
    @Test
    void shouldRequireAuthenticationForUsersMe() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isForbidden());
    }

    // Helper: create random credentials and return access token.
    private String registerAndLoginRandom(String prefix) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = prefix + "_" + suffix;
        String email = username + "@example.com";
        return registerAndLogin(username, email);
    }

    // Helper: register a user and return JWT from login response.
    private String registerAndLogin(String username, String email) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "email", email,
                                "password", "StrongP@ss1"
                        ))))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", username,
                                "password", "StrongP@ss1"
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginBody = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        return loginBody.get("accessToken").asText();
    }
}
