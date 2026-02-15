package ch.backend.taskmanagerapi.file;

import ch.backend.taskmanagerapi.config.ProjectStatus;
import ch.backend.taskmanagerapi.config.Role;
import ch.backend.taskmanagerapi.config.TaskStatus;
import ch.backend.taskmanagerapi.error.ResourceOwnershipException;
import ch.backend.taskmanagerapi.project.Project;
import ch.backend.taskmanagerapi.task.Task;
import ch.backend.taskmanagerapi.task.TaskRepository;
import ch.backend.taskmanagerapi.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private FileRepository fileRepository;

    @Mock
    private TaskRepository taskRepository;

    private FileService fileService;

    // Setup: create service with mocked dependencies.
    @BeforeEach
    void setUp() {
        fileService = new FileService(fileRepository, taskRepository);
    }

    // Test: file upload succeeds for owned task and valid payload.
    @Test
    void shouldUploadFileSuccessfully() throws IOException {
        User owner = user(1L);
        Task task = task(10L, owner);
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", "doc.txt", "text/plain", "hello".getBytes()
        );

        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(fileRepository.save(any(File.class))).thenAnswer(i -> {
            File f = i.getArgument(0);
            f.setId(100L);
            return f;
        });

        FileResponse response = fileService.uploadFile(owner, 10L, multipartFile);

        assertEquals(100L, response.getId());
        assertEquals("doc.txt", response.getFilename());
        assertEquals("text/plain", response.getContentType());
    }

    // Test: file upload rejects empty payload.
    @Test
    void shouldRejectEmptyFileUpload() {
        User owner = user(1L);
        MockMultipartFile empty = new MockMultipartFile("file", "doc.txt", "text/plain", new byte[0]);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> fileService.uploadFile(owner, 10L, empty)
        );

        assertEquals("File is empty.", ex.getMessage());
    }

    // Test: file upload rejects payload larger than configured limit.
    @Test
    void shouldRejectOversizedFileUpload() {
        User owner = user(1L);
        byte[] large = new byte[2 * 1024 * 1024 + 1];
        MockMultipartFile oversized = new MockMultipartFile("file", "big.bin", "application/octet-stream", large);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> fileService.uploadFile(owner, 10L, oversized)
        );

        assertEquals("File exceeds maximum size of 2MB.", ex.getMessage());
    }

    // Test: file operations reject task not owned by current user.
    @Test
    void shouldRejectFileAccessWhenTaskNotOwned() {
        User owner = user(1L);
        User other = user(2L);
        Task foreignTask = task(10L, other);
        when(taskRepository.findById(10L)).thenReturn(Optional.of(foreignTask));

        ResourceOwnershipException ex = assertThrows(
                ResourceOwnershipException.class,
                () -> fileService.getFiles(owner, 10L, 0, 20, "filename", "ASC", null)
        );

        assertEquals("Task not owned by current user.", ex.getMessage());
    }

    // Test: file listing rejects invalid pagination/sorting inputs.
    @Test
    void shouldRejectInvalidFilePaginationAndSort() {
        User owner = user(1L);

        IllegalArgumentException pageEx = assertThrows(
                IllegalArgumentException.class,
                () -> fileService.getFiles(owner, 10L, -1, 20, "filename", "ASC", null)
        );
        assertEquals("Invalid pagination parameters.", pageEx.getMessage());

        IllegalArgumentException sizeEx = assertThrows(
                IllegalArgumentException.class,
                () -> fileService.getFiles(owner, 10L, 0, 0, "filename", "ASC", null)
        );
        assertEquals("Invalid pagination parameters.", sizeEx.getMessage());

        IllegalArgumentException fieldEx = assertThrows(
                IllegalArgumentException.class,
                () -> fileService.getFiles(owner, 10L, 0, 20, "contentType", "ASC", null)
        );
        assertEquals("Invalid sort field. Allowed: filename.", fieldEx.getMessage());

        IllegalArgumentException dirEx = assertThrows(
                IllegalArgumentException.class,
                () -> fileService.getFiles(owner, 10L, 0, 20, "filename", "DOWN", null)
        );
        assertEquals("Invalid sort direction. Use ASC or DESC.", dirEx.getMessage());
    }

    // Test: file listing returns mapped DTOs.
    @Test
    void shouldGetFilesAndMapResponses() {
        User owner = user(1L);
        Task task = task(10L, owner);
        File file = file(100L, task, "alpha.txt");
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(fileRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(file)));

        Page<FileResponse> page = fileService.getFiles(owner, 10L, 0, 20, "filename", "ASC", "alpha");

        assertEquals(1, page.getTotalElements());
        assertEquals("alpha.txt", page.getContent().get(0).getFilename());
    }

    // Test: file retrieval by id succeeds for owned task.
    @Test
    void shouldGetFileByIdSuccessfully() {
        User owner = user(1L);
        Task task = task(10L, owner);
        File file = file(100L, task, "alpha.txt");
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(fileRepository.findById(100L)).thenReturn(Optional.of(file));

        FileResponse response = fileService.getFile(owner, 10L, 100L);

        assertEquals(100L, response.getId());
        assertEquals("alpha.txt", response.getFilename());
    }

    // Test: file retrieval rejects unknown file id.
    @Test
    void shouldRejectFileGetWhenFileNotFound() {
        User owner = user(1L);
        Task task = task(10L, owner);
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(fileRepository.findById(404L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> fileService.getFile(owner, 10L, 404L)
        );

        assertEquals("File not found.", ex.getMessage());
    }

    // Test: file retrieval rejects task/file mismatch.
    @Test
    void shouldRejectFileGetWhenTaskMismatch() {
        User owner = user(1L);
        Task taskA = task(10L, owner);
        Task taskB = task(11L, owner);
        File file = file(100L, taskB, "alpha.txt");
        when(taskRepository.findById(10L)).thenReturn(Optional.of(taskA));
        when(fileRepository.findById(100L)).thenReturn(Optional.of(file));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> fileService.getFile(owner, 10L, 100L)
        );

        assertEquals("File does not belong to the specified task.", ex.getMessage());
    }

    // Test: file deletion succeeds for owned file.
    @Test
    void shouldDeleteFileSuccessfully() {
        User owner = user(1L);
        Task task = task(10L, owner);
        File file = file(100L, task, "alpha.txt");
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(fileRepository.findById(100L)).thenReturn(Optional.of(file));

        fileService.deleteFile(owner, 10L, 100L);

        verify(fileRepository).delete(file);
    }

    // Test: file deletion rejects task/file mismatch.
    @Test
    void shouldRejectDeleteWhenTaskMismatch() {
        User owner = user(1L);
        Task taskA = task(10L, owner);
        Task taskB = task(11L, owner);
        File file = file(100L, taskB, "alpha.txt");
        when(taskRepository.findById(10L)).thenReturn(Optional.of(taskA));
        when(fileRepository.findById(100L)).thenReturn(Optional.of(file));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> fileService.deleteFile(owner, 10L, 100L)
        );

        assertEquals("File does not belong to the specified task.", ex.getMessage());
        verify(fileRepository, never()).delete(any(File.class));
    }

    // Helper: build a test user fixture.
    private User user(Long id) {
        return User.builder()
                .id(id)
                .username("u" + id)
                .email("u" + id + "@example.com")
                .role(Role.USER)
                .password("encoded")
                .build();
    }

    // Helper: build a test task fixture with owned project.
    private Task task(Long id, User owner) {
        Project project = Project.builder()
                .id(100L + id)
                .owner(owner)
                .projectName("P" + id)
                .status(ProjectStatus.ACTIVE)
                .startDate(LocalDate.of(2026, 2, 14))
                .build();

        return Task.builder()
                .id(id)
                .project(project)
                .taskName("T" + id)
                .status(TaskStatus.TODO)
                .dueDate(LocalDate.of(2026, 2, 20))
                .build();
    }

    // Helper: build a test file fixture.
    private File file(Long id, Task task, String filename) {
        return File.builder()
                .id(id)
                .task(task)
                .filename(filename)
                .fileUrl("/mock/" + filename)
                .contentType("text/plain")
                .build();
    }
}
