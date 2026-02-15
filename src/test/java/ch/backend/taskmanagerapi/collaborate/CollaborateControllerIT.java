package ch.backend.taskmanagerapi.collaborate;

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

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CollaborateControllerIT {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    // Setup: initialize secured MockMvc for collaborator endpoint tests.
    @BeforeEach
    void setUpMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    // Test: collaborator page supports filtering and sorting.
    @Test
    void shouldListCollaboratorsWithSortAndFilter() throws Exception {
        String ownerToken = registerAndLogin("collab_owner");
        long targetAUserId = registerAndLoginAndGetId("collab_target_a");
        long targetZUserId = registerAndLoginAndGetId("collab_target_z");
        long projectId = createProject(ownerToken, "Collab Project");
        long taskId = createTask(ownerToken, projectId, "Collab Task");

        addCollaborator(ownerToken, taskId, targetAUserId, true);
        addCollaborator(ownerToken, taskId, targetZUserId, false);

        mockMvc.perform(get("/tasks/{taskId}/collaborators", taskId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("page", "0")
                        .param("size", "20")
                        .param("username", "collab_target")
                        .param("sortBy", "username")
                        .param("direction", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].username").value(org.hamcrest.Matchers.containsString("collab_target_z")))
                .andExpect(jsonPath("$.content[1].username").value(org.hamcrest.Matchers.containsString("collab_target_a")))
                .andExpect(jsonPath("$.content[0].responsible").value(false))
                .andExpect(jsonPath("$.content[1].responsible").value(true));
    }

    // Test: invalid collaborator sort field is rejected.
    @Test
    void shouldRejectInvalidCollaboratorSortField() throws Exception {
        String ownerToken = registerAndLogin("collab_sort_owner");
        long targetUserId = registerAndLoginAndGetId("collab_sort_target");
        long projectId = createProject(ownerToken, "Collab Sort Project");
        long taskId = createTask(ownerToken, projectId, "Collab Sort Task");

        addCollaborator(ownerToken, taskId, targetUserId, false);

        mockMvc.perform(get("/tasks/{taskId}/collaborators", taskId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .param("page", "0")
                        .param("size", "20")
                        .param("sortBy", "responsible")
                        .param("direction", "ASC"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid sort field. Allowed: username, email."));
    }

    // Test: responsible collaborator endpoint returns expected user.
    @Test
    void shouldGetResponsibleCollaborator() throws Exception {
        String ownerToken = registerAndLogin("collab_resp_owner");
        long responsibleUserId = registerAndLoginAndGetId("collab_resp_user");
        long projectId = createProject(ownerToken, "Collab Resp Project");
        long taskId = createTask(ownerToken, projectId, "Collab Resp Task");

        addCollaborator(ownerToken, taskId, responsibleUserId, true);

        mockMvc.perform(get("/tasks/{taskId}/responsible", taskId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(responsibleUserId))
                .andExpect(jsonPath("$.responsible").value(true));
    }

    // Test: owner can remove a non-responsible collaborator.
    @Test
    void shouldRemoveCollaboratorSuccessfully() throws Exception {
        String ownerToken = registerAndLogin("collab_remove_owner");
        long removableUserId = registerAndLoginAndGetId("collab_remove_user");
        long responsibleUserId = registerAndLoginAndGetId("collab_remove_resp");
        long projectId = createProject(ownerToken, "Collab Remove Project");
        long taskId = createTask(ownerToken, projectId, "Collab Remove Task");

        addCollaborator(ownerToken, taskId, responsibleUserId, true);
        addCollaborator(ownerToken, taskId, removableUserId, false);

        mockMvc.perform(delete("/tasks/{taskId}/collaborators/{userId}", taskId, removableUserId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());
    }

    // Test: removing the only responsible collaborator is rejected.
    @Test
    void shouldRejectRemovingOnlyResponsibleCollaborator() throws Exception {
        String ownerToken = registerAndLogin("collab_cannot_remove_owner");
        long responsibleUserId = registerAndLoginAndGetId("collab_cannot_remove_resp");
        long projectId = createProject(ownerToken, "Collab Cannot Remove Project");
        long taskId = createTask(ownerToken, projectId, "Collab Cannot Remove Task");

        addCollaborator(ownerToken, taskId, responsibleUserId, true);

        mockMvc.perform(delete("/tasks/{taskId}/collaborators/{userId}", taskId, responsibleUserId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot remove the responsible collaborator without assigning another responsible first."));
    }

    // Test: unknown task/collaborator ids return not found errors.
    @Test
    void shouldReturn404ForUnknownTaskOrCollaborator() throws Exception {
        String ownerToken = registerAndLogin("collab_404_owner");
        long projectId = createProject(ownerToken, "Collab 404 Project");
        long taskId = createTask(ownerToken, projectId, "Collab 404 Task");

        mockMvc.perform(get("/tasks/{taskId}/collaborators", 9999999L)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Task not found."));

        mockMvc.perform(delete("/tasks/{taskId}/collaborators/{userId}", taskId, 9999999L)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Collaborator user not found."));
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
        return objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("accessToken").asText();
    }

    // Helper: register/login and return created user id.
    private long registerAndLoginAndGetId(String prefix) throws Exception {
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

        return objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("userId").asLong();
    }

    // Helper: create project and return created id.
    private long createProject(String token, String name) throws Exception {
        String startDate = LocalDate.now().plusDays(1).toString();
        MvcResult result = mockMvc.perform(post("/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "projectName", name,
                                "status", "ACTIVE",
                                "startDate", startDate
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    // Helper: create task and return created id.
    private long createTask(String token, long projectId, String name) throws Exception {
        String dueDate = LocalDate.now().plusDays(7).toString();
        MvcResult result = mockMvc.perform(post("/projects/{projectId}/tasks", projectId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "taskName", name,
                                "status", "TODO",
                                "dueDate", dueDate
                        ))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    // Helper: add collaborator to task with requested responsibility flag.
    private void addCollaborator(String token, long taskId, long userId, boolean responsible) throws Exception {
        mockMvc.perform(post("/tasks/{taskId}/collaborators", taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", userId,
                                "responsible", responsible
                        ))))
                .andExpect(status().isCreated());
    }
}
