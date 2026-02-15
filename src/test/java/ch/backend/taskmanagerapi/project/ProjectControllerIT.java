package ch.backend.taskmanagerapi.project;

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

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProjectControllerIT {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Setup: initialize secured MockMvc for project endpoint tests.
    @BeforeEach
    void setUpMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    // Test: create project then retrieve it by id.
    @Test
    void shouldCreateAndGetProjectById() throws Exception {
        String token = registerAndLogin("project_get");
        long projectId = createProject(
                token,
                "Project GET API",
                "ACTIVE",
                LocalDate.now().plusDays(1).toString(),
                LocalDate.now().plusDays(7).toString()
        );

        mockMvc.perform(get("/projects/{id}", projectId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(projectId))
                .andExpect(jsonPath("$.projectName").value("Project GET API"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    // Test: project page supports filtering and sorting.
    @Test
    void shouldFilterAndSortProjectsPage() throws Exception {
        String token = registerAndLogin("project_filter");
        createProject(token, "Alpha API", "ACTIVE", LocalDate.now().plusDays(1).toString(), null);
        createProject(token, "Zulu API", "PAUSED", LocalDate.now().plusDays(2).toString(), null);
        createProject(token, "Other Name", "COMPLETED", LocalDate.now().plusDays(3).toString(), null);

        mockMvc.perform(get("/projects/page")
                        .header("Authorization", "Bearer " + token)
                        .param("page", "0")
                        .param("size", "20")
                        .param("projectName", "api")
                        .param("sortBy", "projectName")
                        .param("direction", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].projectName").value("Zulu API"))
                .andExpect(jsonPath("$.content[1].projectName").value("Alpha API"));
    }

    // Test: invalid project sort field is rejected.
    @Test
    void shouldRejectInvalidProjectSortField() throws Exception {
        String token = registerAndLogin("project_sort_invalid");
        createProject(token, "Sort Test", "ACTIVE", LocalDate.now().plusDays(1).toString(), null);

        mockMvc.perform(get("/projects/page")
                        .header("Authorization", "Bearer " + token)
                        .param("page", "0")
                        .param("size", "20")
                        .param("sortBy", "status")
                        .param("direction", "ASC"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid sort field. Allowed: projectName, startDate, endDate."));
    }

    // Test: user cannot access another user's project.
    @Test
    void shouldForbidAccessToAnotherUsersProject() throws Exception {
        String ownerToken = registerAndLogin("project_owner");
        String otherUserToken = registerAndLogin("project_other");
        long ownerProjectId = createProject(
                ownerToken,
                "Private Project",
                "ACTIVE",
                LocalDate.now().plusDays(1).toString(),
                null
        );

        mockMvc.perform(get("/projects/{id}", ownerProjectId)
                        .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isForbidden());
    }

    // Test: owner can update and delete a project he owns.
    @Test
    void shouldUpdateAndDeleteProject() throws Exception {
        String token = registerAndLogin("project_update_delete");
        long projectId = createProject(
                token,
                "Project Before Update",
                "ACTIVE",
                LocalDate.now().plusDays(1).toString(),
                null
        );

        mockMvc.perform(patch("/projects/{id}", projectId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectName", "Project After Update",
                                "status", "PAUSED"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectName").value("Project After Update"))
                .andExpect(jsonPath("$.status").value("PAUSED"));

        mockMvc.perform(delete("/projects/{id}", projectId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    // Test: non-owner cannot update or delete another user's project.
    @Test
    void shouldForbidUpdatingAndDeletingAnotherUsersProject() throws Exception {
        String ownerToken = registerAndLogin("project_owner_ud");
        String otherToken = registerAndLogin("project_other_ud");
        long projectId = createProject(
                ownerToken,
                "Owner Private",
                "ACTIVE",
                LocalDate.now().plusDays(1).toString(),
                null
        );

        mockMvc.perform(patch("/projects/{id}", projectId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("projectName", "Hacked Name"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/projects/{id}", projectId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    // Test: unknown project id returns not found for get and delete.
    @Test
    void shouldReturn404ForUnknownProject() throws Exception {
        String token = registerAndLogin("project_404");

        mockMvc.perform(get("/projects/{id}", 9999999L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Project not found."));

        mockMvc.perform(delete("/projects/{id}", 9999999L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Project not found."));
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

    // Helper: create project and return created id.
    private long createProject(String token, String name, String status, String startDate, String endDate) throws Exception {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("projectName", name);
        payload.put("status", status);
        payload.put("startDate", startDate);
        payload.put("endDate", endDate);

        MvcResult result = mockMvc.perform(post("/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andReturn();

        int statusCode = result.getResponse().getStatus();
        if (statusCode != 200) {
            throw new AssertionError("Expected 200 for create project but got " + statusCode
                    + " body=" + result.getResponse().getContentAsString());
        }

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asLong();
    }
}
