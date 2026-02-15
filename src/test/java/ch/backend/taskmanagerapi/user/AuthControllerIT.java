package ch.backend.taskmanagerapi.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthControllerIT {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    // Setup: initialize secured MockMvc for integration requests.
    @org.junit.jupiter.api.BeforeEach
    void setUpMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    // Test: register and login return expected success payloads.
    @Test
    void shouldRegisterAndLoginSuccessfully() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "auth_ok_" + suffix;
        String email = username + "@example.com";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "email", email,
                                "password", "StrongP@ss1"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.email").value(email));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", username,
                                "password", "StrongP@ss1"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.username").value(username));
    }

    // Test: duplicate email registration is rejected.
    @Test
    void shouldRejectDuplicateEmailRegistration() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username1 = "auth_dup1_" + suffix;
        String username2 = "auth_dup2_" + suffix;
        String email = "auth_dup_" + suffix + "@example.com";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username1,
                                "email", email,
                                "password", "StrongP@ss1"
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username2,
                                "email", email,
                                "password", "StrongP@ss1"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email is already in use."));
    }

    // Test: duplicate username registration is rejected.
    @Test
    void shouldRejectDuplicateUsernameRegistration() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "auth_dup_user_" + suffix;
        String email1 = "auth_dup_user_a_" + suffix + "@example.com";
        String email2 = "auth_dup_user_b_" + suffix + "@example.com";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "email", email1,
                                "password", "StrongP@ss1"
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "email", email2,
                                "password", "StrongP@ss1"
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Username is already taken."));
    }

    // Test: invalid registration payload returns validation errors.
    @Test
    void shouldRejectInvalidRegistrationPayload() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "ab",
                                "email", "not-an-email",
                                "password", "weak"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed for request."))
                .andExpect(jsonPath("$.fieldErrors.username").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    // Test: login accepts email identifier as an alternative to username.
    @Test
    void shouldLoginWithEmailIdentifier() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "auth_email_" + suffix;
        String email = username + "@example.com";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "email", email,
                                "password", "StrongP@ss1"
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", email,
                                "password", "StrongP@ss1"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.username").value(username));
    }

    // Test: login fails when credentials are invalid.
    @Test
    void shouldRejectInvalidCredentialsOnLogin() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "auth_bad_" + suffix;
        String email = username + "@example.com";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "email", email,
                                "password", "StrongP@ss1"
                        ))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", username,
                                "password", "WrongP@ss1"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid username/email or password."));
    }
}
