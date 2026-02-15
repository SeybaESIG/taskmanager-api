package ch.backend.taskmanagerapi.task;

import ch.backend.taskmanagerapi.config.ProjectStatus;
import ch.backend.taskmanagerapi.config.Role;
import ch.backend.taskmanagerapi.config.TaskStatus;
import ch.backend.taskmanagerapi.project.Project;
import ch.backend.taskmanagerapi.project.ProjectRepository;
import ch.backend.taskmanagerapi.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;

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
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ProjectRepository projectRepository;

    private TaskService taskService;

    // Setup: create service with mocked dependencies.
    @BeforeEach
    void setUp() {
        taskService = new TaskService(taskRepository, projectRepository);
    }

    // Test: task creation succeeds under an owned project.
    @Test
    void shouldCreateTaskSuccessfully() {
        User owner = user(1L);
        Project project = project(10L, owner);
        CreateTaskRequest request = new CreateTaskRequest();
        request.setTaskName("Task 1");
        request.setStatus(TaskStatus.TODO);
        request.setDueDate(LocalDate.of(2026, 2, 20));
        request.setDescription("Desc");

        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(taskRepository.save(any(Task.class))).thenAnswer(i -> {
            Task t = i.getArgument(0);
            t.setId(100L);
            return t;
        });

        TaskResponse response = taskService.createTask(owner, 10L, request);

        assertEquals(100L, response.getId());
        assertEquals("Task 1", response.getTaskName());
        assertEquals(10L, response.getProjectId());
    }

    // Test: task creation rejects project not owned by current user.
    @Test
    void shouldRejectTaskCreationWhenProjectNotOwned() {
        User owner = user(1L);
        User other = user(2L);
        Project project = project(10L, other);
        CreateTaskRequest request = new CreateTaskRequest();
        request.setTaskName("Task 1");
        request.setStatus(TaskStatus.TODO);
        request.setDueDate(LocalDate.of(2026, 2, 20));

        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> taskService.createTask(owner, 10L, request)
        );

        assertEquals("Access denied: project does not belong to current user.", ex.getMessage());
    }

    // Test: task listing rejects invalid pagination/sorting inputs.
    @Test
    void shouldRejectInvalidTaskPaginationAndSort() {
        User owner = user(1L);

        IllegalArgumentException pageEx = assertThrows(
                IllegalArgumentException.class,
                () -> taskService.getTasksForProject(owner, 10L, -1, 20, "taskName", "ASC", null, null, null)
        );
        assertEquals("Invalid pagination parameters.", pageEx.getMessage());

        IllegalArgumentException sizeEx = assertThrows(
                IllegalArgumentException.class,
                () -> taskService.getTasksForProject(owner, 10L, 0, 0, "taskName", "ASC", null, null, null)
        );
        assertEquals("Invalid pagination parameters.", sizeEx.getMessage());

        IllegalArgumentException fieldEx = assertThrows(
                IllegalArgumentException.class,
                () -> taskService.getTasksForProject(owner, 10L, 0, 20, "status", "ASC", null, null, null)
        );
        assertEquals("Invalid sort field. Allowed: taskName, dueDate.", fieldEx.getMessage());

        IllegalArgumentException dirEx = assertThrows(
                IllegalArgumentException.class,
                () -> taskService.getTasksForProject(owner, 10L, 0, 20, "taskName", "UP", null, null, null)
        );
        assertEquals("Invalid sort direction. Use ASC or DESC.", dirEx.getMessage());
    }

    // Test: task listing returns mapped DTOs with filters applied.
    @Test
    void shouldGetTasksForProjectWithMapping() {
        User owner = user(1L);
        Project project = project(10L, owner);
        Task task = task(100L, project, "Alpha", TaskStatus.TODO);
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(taskRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(task)));

        Page<TaskResponse> result = taskService.getTasksForProject(
                owner, 10L, 0, 20, "taskName", "ASC", "alp",
                LocalDate.of(2026, 2, 20), "todo"
        );

        assertEquals(1, result.getTotalElements());
        assertEquals("Alpha", result.getContent().get(0).getTaskName());
    }

    // Test: task retrieval by id succeeds for owned project.
    @Test
    void shouldGetTaskByIdForProject() {
        User owner = user(1L);
        Project project = project(10L, owner);
        Task task = task(100L, project, "Alpha", TaskStatus.TODO);
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
        when(taskRepository.findById(100L)).thenReturn(Optional.of(task));

        TaskResponse response = taskService.getTaskByIdForProject(owner, 10L, 100L);

        assertEquals(100L, response.getId());
    }

    // Test: task retrieval rejects task/project mismatch.
    @Test
    void shouldRejectTaskByIdWhenTaskDoesNotBelongToSpecifiedProject() {
        User owner = user(1L);
        Project projectA = project(10L, owner);
        Project projectB = project(11L, owner);
        Task task = task(100L, projectB, "Alpha", TaskStatus.TODO);
        when(projectRepository.findById(10L)).thenReturn(Optional.of(projectA));
        when(taskRepository.findById(100L)).thenReturn(Optional.of(task));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> taskService.getTaskByIdForProject(owner, 10L, 100L)
        );

        assertEquals("Task does not belong to the specified project.", ex.getMessage());
    }

    // Test: task update succeeds for owned task.
    @Test
    void shouldUpdateTaskSuccessfully() {
        User owner = user(1L);
        Project project = project(10L, owner);
        Task existing = task(100L, project, "Old", TaskStatus.TODO);
        when(taskRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(taskRepository.save(any(Task.class))).thenAnswer(i -> i.getArgument(0));

        UpdateTaskRequest request = new UpdateTaskRequest();
        request.setTaskName("New");
        request.setStatus(TaskStatus.DONE);
        request.setDescription("Updated");
        request.setDueDate(LocalDate.of(2026, 2, 25));

        TaskResponse response = taskService.updateTask(owner, 100L, request);

        assertEquals("New", response.getTaskName());
        assertEquals(TaskStatus.DONE, response.getStatus());
        assertEquals("Updated", response.getDescription());
    }

    // Test: task deletion succeeds for owned task.
    @Test
    void shouldDeleteTaskSuccessfully() {
        User owner = user(1L);
        Project project = project(10L, owner);
        Task existing = task(100L, project, "Old", TaskStatus.TODO);
        when(taskRepository.findById(100L)).thenReturn(Optional.of(existing));

        taskService.deleteTask(owner, 100L);

        verify(taskRepository).delete(existing);
    }

    // Test: task operations reject foreign-owned tasks.
    @Test
    void shouldRejectTaskOperationsWhenTaskNotOwned() {
        User owner = user(1L);
        User other = user(2L);
        Project otherProject = project(20L, other);
        Task foreignTask = task(100L, otherProject, "Foreign", TaskStatus.TODO);
        when(taskRepository.findById(100L)).thenReturn(Optional.of(foreignTask));

        UpdateTaskRequest request = new UpdateTaskRequest();
        request.setTaskName("Hacked");

        IllegalArgumentException updateEx = assertThrows(
                IllegalArgumentException.class,
                () -> taskService.updateTask(owner, 100L, request)
        );
        assertEquals("Access denied: task does not belong to a project of current user.", updateEx.getMessage());

        IllegalArgumentException deleteEx = assertThrows(
                IllegalArgumentException.class,
                () -> taskService.deleteTask(owner, 100L)
        );
        assertEquals("Access denied: task does not belong to a project of current user.", deleteEx.getMessage());
        verify(taskRepository, never()).delete(any(Task.class));
    }

    // Test: task operations reject unknown task id.
    @Test
    void shouldRejectTaskOperationsWhenTaskNotFound() {
        User owner = user(1L);
        when(taskRepository.findById(404L)).thenReturn(Optional.empty());

        UpdateTaskRequest request = new UpdateTaskRequest();
        request.setTaskName("Hacked");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> taskService.updateTask(owner, 404L, request)
        );
        assertEquals("Task not found.", ex.getMessage());
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

    // Helper: build a test project fixture.
    private Project project(Long id, User owner) {
        return Project.builder()
                .id(id)
                .owner(owner)
                .projectName("Project " + id)
                .status(ProjectStatus.ACTIVE)
                .startDate(LocalDate.of(2026, 2, 14))
                .endDate(LocalDate.of(2026, 2, 20))
                .build();
    }

    // Helper: build a test task fixture.
    private Task task(Long id, Project project, String name, TaskStatus status) {
        return Task.builder()
                .id(id)
                .project(project)
                .taskName(name)
                .status(status)
                .dueDate(LocalDate.of(2026, 2, 20))
                .description("desc")
                .build();
    }
}
