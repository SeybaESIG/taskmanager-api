package ch.backend.taskmanagerapi.file;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FileControllerIT {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    // Setup: initialize secured MockMvc for file endpoint tests.
    @BeforeEach
    void setUpMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    // Test: upload files then filter/sort by filename.
    @Test
    void shouldUploadAndFilterFilesByFilename() throws Exception {
        String token = registerAndLogin("file_it");
        long projectId = createProject(token, "File IT Project");
        long taskId = createTask(token, projectId, "File IT Task");

        uploadFile(token, taskId, "beta-notes.txt", "b");
        uploadFile(token, taskId, "alpha-doc.txt", "a");
        uploadFile(token, taskId, "alpha-zeta.txt", "z");

        mockMvc.perform(get("/tasks/{taskId}/files/page", taskId)
                        .header("Authorization", "Bearer " + token)
                        .param("page", "0")
                        .param("size", "20")
                        .param("filename", "alpha")
                        .param("sortBy", "filename")
                        .param("direction", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].filename").value("alpha-zeta.txt"))
                .andExpect(jsonPath("$.content[1].filename").value("alpha-doc.txt"));
    }

    // Test: invalid file sort field is rejected.
    @Test
    void shouldRejectInvalidFileSortField() throws Exception {
        String token = registerAndLogin("file_sort_invalid");
        long projectId = createProject(token, "File Sort Project");
        long taskId = createTask(token, projectId, "File Sort Task");
        uploadFile(token, taskId, "alpha.txt", "a");

        mockMvc.perform(get("/tasks/{taskId}/files/page", taskId)
                        .header("Authorization", "Bearer " + token)
                        .param("page", "0")
                        .param("size", "20")
                        .param("sortBy", "contentType")
                        .param("direction", "ASC"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid sort field. Allowed: filename."));
    }

    // Test: user cannot list files from another user's task.
    @Test
    void shouldForbidAccessToAnotherUsersFiles() throws Exception {
        String ownerToken = registerAndLogin("file_owner");
        String otherUserToken = registerAndLogin("file_other");
        long projectId = createProject(ownerToken, "Owner File Project");
        long taskId = createTask(ownerToken, projectId, "Owner File Task");
        uploadFile(ownerToken, taskId, "private.txt", "secret");

        mockMvc.perform(get("/tasks/{taskId}/files/page", taskId)
                        .header("Authorization", "Bearer " + otherUserToken)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isForbidden());
    }

    // Test: owner can get and delete file by id.
    @Test
    void shouldGetAndDeleteFileById() throws Exception {
        String token = registerAndLogin("file_get_delete");
        long projectId = createProject(token, "File GetDelete Project");
        long taskId = createTask(token, projectId, "File GetDelete Task");
        long fileId = uploadFileAndGetId(token, taskId, "single.txt", "content");

        mockMvc.perform(get("/tasks/{taskId}/files/{fileId}", taskId, fileId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(fileId))
                .andExpect(jsonPath("$.filename").value("single.txt"));

        mockMvc.perform(delete("/tasks/{taskId}/files/{fileId}", taskId, fileId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    // Test: non-owner cannot get or delete another user's file.
    @Test
    void shouldForbidGetAndDeleteAnotherUsersFile() throws Exception {
        String ownerToken = registerAndLogin("file_owner_gd");
        String otherToken = registerAndLogin("file_other_gd");
        long projectId = createProject(ownerToken, "Owner GetDelete Project");
        long taskId = createTask(ownerToken, projectId, "Owner GetDelete Task");
        long fileId = uploadFileAndGetId(ownerToken, taskId, "private-gd.txt", "secret");

        mockMvc.perform(get("/tasks/{taskId}/files/{fileId}", taskId, fileId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/tasks/{taskId}/files/{fileId}", taskId, fileId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden());
    }

    // Test: unknown file id returns not found for get and delete.
    @Test
    void shouldReturn404ForUnknownFile() throws Exception {
        String token = registerAndLogin("file_404");
        long projectId = createProject(token, "File 404 Project");
        long taskId = createTask(token, projectId, "File 404 Task");

        mockMvc.perform(get("/tasks/{taskId}/files/{fileId}", taskId, 9999999L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("File not found."));

        mockMvc.perform(delete("/tasks/{taskId}/files/{fileId}", taskId, 9999999L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("File not found."));
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

    // Helper: upload a file and assert creation success.
    private void uploadFile(String token, long taskId, String filename, String content) throws Exception {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                filename,
                MediaType.TEXT_PLAIN_VALUE,
                content.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/tasks/{taskId}/files", taskId)
                        .file(multipartFile)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());
    }

    // Helper: upload a file and return generated file id.
    private long uploadFileAndGetId(String token, long taskId, String filename, String content) throws Exception {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                filename,
                MediaType.TEXT_PLAIN_VALUE,
                content.getBytes(StandardCharsets.UTF_8)
        );

        MvcResult result = mockMvc.perform(multipart("/tasks/{taskId}/files", taskId)
                        .file(multipartFile)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
