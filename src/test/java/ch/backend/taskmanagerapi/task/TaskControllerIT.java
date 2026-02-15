package ch.backend.taskmanagerapi.task;

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
class TaskControllerIT {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Setup: initialize secured MockMvc for task endpoint tests.
    @BeforeEach
    void setUpMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    // Test: create task then retrieve it by id.
    @Test
    void shouldCreateAndGetTaskById() throws Exception {
        String token = registerAndLogin("task_get");
        long projectId = createProject(token, "Task Parent Project");
        long taskId = createTask(token, projectId, "Alpha Task", "TODO", "2026-02-20");

        mockMvc.perform(get("/projects/{projectId}/tasks/{id}", projectId, taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(taskId))
                .andExpect(jsonPath("$.taskName").value("Alpha Task"))
                .andExpect(jsonPath("$.projectId").value(projectId));
    }

    // Test: task page supports filtering and sorting.
    @Test
    void shouldFilterAndSortTasksPage() throws Exception {
        String token = registerAndLogin("task_filter");
        long projectId = createProject(token, "Task Filter Project");
        createTask(token, projectId, "beta task", "TODO", "2026-02-20");
        createTask(token, projectId, "Alpha Task", "DONE", "2026-02-18");
        createTask(token, projectId, "gamma API", "IN_PROGRESS", "2026-02-22");

        mockMvc.perform(get("/projects/{projectId}/tasks/page", projectId)
                        .header("Authorization", "Bearer " + token)
                        .param("page", "0")
                        .param("size", "20")
                        .param("taskName", "task")
                        .param("sortBy", "taskName")
                        .param("direction", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].taskName").value("beta task"))
                .andExpect(jsonPath("$.content[1].taskName").value("Alpha Task"));
    }

    // Test: task retrieval fails when task/project association mismatches.
    @Test
    void shouldRejectTaskProjectMismatch() throws Exception {
        String token = registerAndLogin("task_mismatch");
        long projectAId = createProject(token, "Project A");
        long projectBId = createProject(token, "Project B");
        long taskInA = createTask(token, projectAId, "Mismatch Task", "TODO", "2026-02-20");

        mockMvc.perform(get("/projects/{projectId}/tasks/{id}", projectBId, taskInA)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Task does not belong to the specified project."));
    }

    // Test: user cannot access another user's task.
    @Test
    void shouldForbidAccessToAnotherUsersTask() throws Exception {
        String ownerToken = registerAndLogin("task_owner");
        String otherUserToken = registerAndLogin("task_other");
        long ownerProjectId = createProject(ownerToken, "Owner Project");
        long ownerTaskId = createTask(ownerToken, ownerProjectId, "Owner Task", "TODO", "2026-02-20");

        mockMvc.perform(get("/projects/{projectId}/tasks/{id}", ownerProjectId, ownerTaskId)
                        .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isForbidden());
    }

    // Test: owner can update and delete a task he owns.
    @Test
    void shouldUpdateAndDeleteTask() throws Exception {
        String token = registerAndLogin("task_update_delete");
        long projectId = createProject(token, "Task UD Project");
        long taskId = createTask(token, projectId, "Task Before Update", "TODO", "2026-02-20");

        mockMvc.perform(patch("/projects/{projectId}/tasks/{id}", projectId, taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "taskName", "Task After Update",
                                "status", "DONE"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.taskName").value("Task After Update"))
                .andExpect(jsonPath("$.status").value("DONE"));

        mockMvc.perform(delete("/projects/{projectId}/tasks/{id}", projectId, taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    // Test: non-owner cannot update or delete another user's task.
    @Test
    void shouldForbidUpdatingAndDeletingAnotherUsersTask() throws Exception {
        String ownerToken = registerAndLogin("task_owner_ud");
        String otherToken = registerAndLogin("task_other_ud");
        long projectId = createProject(ownerToken, "Owner Task Project");
        long taskId = createTask(ownerToken, projectId, "Owner Task", "TODO", "2026-02-20");

        mockMvc.perform(patch("/projects/{projectId}/tasks/{id}", projectId, taskId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("taskName", "Hacked Task"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/projects/{projectId}/tasks/{id}", projectId, taskId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    // Test: unknown task id returns not found for get and delete.
    @Test
    void shouldReturn404ForUnknownTask() throws Exception {
        String token = registerAndLogin("task_404");
        long projectId = createProject(token, "Task 404 Project");

        mockMvc.perform(get("/projects/{projectId}/tasks/{id}", projectId, 9999999L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Task not found."));

        mockMvc.perform(delete("/projects/{projectId}/tasks/{id}", projectId, 9999999L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Task not found."));
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
                .andReturn();

        int statusCode = result.getResponse().getStatus();
        if (statusCode != 200) {
            throw new AssertionError("Expected 200 for create project but got " + statusCode
                    + " body=" + result.getResponse().getContentAsString());
        }

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asLong();
    }

    // Helper: create task and return created id.
    private long createTask(String token, long projectId, String taskName, String status, String dueDate) throws Exception {
        String safeDueDate = LocalDate.now().plusDays(7).toString();
        MvcResult result = mockMvc.perform(post("/projects/{projectId}/tasks", projectId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "taskName", taskName,
                                "status", status,
                                "dueDate", safeDueDate
                        ))))
                .andReturn();

        int statusCode = result.getResponse().getStatus();
        if (statusCode != 200) {
            throw new AssertionError("Expected 200 for create task but got " + statusCode
                    + " body=" + result.getResponse().getContentAsString());
        }

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asLong();
    }
}
