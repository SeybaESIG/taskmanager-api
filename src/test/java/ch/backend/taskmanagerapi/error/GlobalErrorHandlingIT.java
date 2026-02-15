package ch.backend.taskmanagerapi.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GlobalErrorHandlingIT {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Setup: initialize secured MockMvc for global error handler tests.
    @BeforeEach
    void setUpMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    // Test: unknown route is translated to 404 API error.
    @Test
    void shouldReturn404ForMissingRoute() throws Exception {
        mockMvc.perform(get("/this-route-does-not-exist")
                        .with(user("any_user").roles("USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resource not found."));
    }

    // Test: unsupported HTTP method is translated to 405 API error.
    @Test
    void shouldReturn405ForMethodNotAllowed() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.patch("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "identifier", "nobody",
                                "password", "whatever"
                        ))))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").value("HTTP method not allowed for this endpoint."));
    }

    // Test: malformed JSON body is translated to 400 API error.
    @Test
    void shouldReturn400ForMalformedJsonBody() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"bad_json\", \"email\":\"bad@example.com\","))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body."));
    }

    // Test: path/query type mismatch is translated to 400 API error.
    @Test
    void shouldReturn400ForMethodArgumentTypeMismatch() throws Exception {
        String token = registerAndLogin("geh_type_mismatch");

        mockMvc.perform(get("/projects/{id}", "not-a-number")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request parameter type."));
    }

    // Test: invalid enum payload is translated to 400 API error.
    @Test
    void shouldReturn400ForInvalidEnumPayload() throws Exception {
        String token = registerAndLogin("geh_bad_enum");

        mockMvc.perform(post("/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectName", "Enum Project",
                                "status", "INVALID_STATUS",
                                "startDate", "2026-02-14"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body."));
    }

    // Helper: register/login and return access token.
    private String registerAndLogin(String prefix) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = prefix + "_" + suffix;
        String email = username + "@example.com";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "email", email,
                                "password", "StrongP@ss1"
                        ))))
                .andExpect(status().isCreated());

        String loginPayload = objectMapper.writeValueAsString(Map.of(
                "identifier", username,
                "password", "StrongP@ss1"
        ));

        String response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).get("accessToken").asText();
    }
}
