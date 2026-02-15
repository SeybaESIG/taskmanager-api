package ch.backend.taskmanagerapi.collaborate;

import ch.backend.taskmanagerapi.config.ProjectStatus;
import ch.backend.taskmanagerapi.config.Role;
import ch.backend.taskmanagerapi.config.TaskStatus;
import ch.backend.taskmanagerapi.project.Project;
import ch.backend.taskmanagerapi.task.Task;
import ch.backend.taskmanagerapi.task.TaskRepository;
import ch.backend.taskmanagerapi.user.User;
import ch.backend.taskmanagerapi.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollaborateServiceTest {

    @Mock
    private CollaborateRepository collaborateRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

    private CollaborateService collaborateService;

    // Setup: create service with mocked dependencies.
    @BeforeEach
    void setUp() {
        collaborateService = new CollaborateService(collaborateRepository, taskRepository, userRepository);
    }

    // Test: adding a collaborator succeeds for owned task.
    @Test
    void shouldAddCollaboratorSuccessfully() {
        User owner = user(1L);
        User collaborator = user(2L);
        Task task = task(10L, owner);

        AddCollaboratorRequest request = new AddCollaboratorRequest();
        request.setUserId(2L);
        request.setResponsible(false);

        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(userRepository.findById(2L)).thenReturn(Optional.of(collaborator));
        when(collaborateRepository.findByTaskAndUser(task, collaborator)).thenReturn(Optional.empty());
        when(collaborateRepository.save(any(Collaborate.class))).thenAnswer(i -> {
            Collaborate c = i.getArgument(0);
            c.setId(100L);
            return c;
        });

        CollaboratorResponse response = collaborateService.addOrUpdateCollaborator(owner, 10L, request);

        assertEquals(2L, response.getUserId());
        assertEquals(false, response.isResponsible());
    }

    // Test: setting already-responsible collaborator as responsible is rejected.
    @Test
    void shouldRejectAlreadyResponsibleCollaborator() {
        User owner = user(1L);
        User collaborator = user(2L);
        Task task = task(10L, owner);

        AddCollaboratorRequest request = new AddCollaboratorRequest();
        request.setUserId(2L);
        request.setResponsible(true);

        Collaborate existing = Collaborate.builder()
                .id(100L)
                .task(task)
                .user(collaborator)
                .responsible(true)
                .build();

        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(userRepository.findById(2L)).thenReturn(Optional.of(collaborator));
        when(collaborateRepository.findByTaskAndUser(task, collaborator)).thenReturn(Optional.of(existing));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> collaborateService.addOrUpdateCollaborator(owner, 10L, request)
        );

        assertEquals("User is already responsible for this task.", ex.getMessage());
    }

    // Test: assigning a new responsible collaborator demotes previous responsible.
    @Test
    void shouldSwitchResponsibleCollaboratorWhenNewResponsibleProvided() {
        User owner = user(1L);
        User currentResponsibleUser = user(2L);
        User newResponsibleUser = user(3L);
        Task task = task(10L, owner);

        AddCollaboratorRequest request = new AddCollaboratorRequest();
        request.setUserId(3L);
        request.setResponsible(true);

        Collaborate currentResponsible = Collaborate.builder()
                .id(100L)
                .task(task)
                .user(currentResponsibleUser)
                .responsible(true)
                .build();

        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(userRepository.findById(3L)).thenReturn(Optional.of(newResponsibleUser));
        when(collaborateRepository.findByTaskAndUser(task, newResponsibleUser)).thenReturn(Optional.empty());
        when(collaborateRepository.findByTaskAndResponsibleTrue(task)).thenReturn(Optional.of(currentResponsible));
        when(collaborateRepository.save(any(Collaborate.class))).thenAnswer(i -> i.getArgument(0));

        CollaboratorResponse response = collaborateService.addOrUpdateCollaborator(owner, 10L, request);

        assertEquals(3L, response.getUserId());
        assertEquals(true, response.isResponsible());
        assertEquals(false, currentResponsible.isResponsible());
    }

    // Test: collaborator listing rejects invalid pagination/sorting inputs.
    @Test
    void shouldRejectInvalidCollaboratorPaginationAndSort() {
        User owner = user(1L);

        IllegalArgumentException pageEx = assertThrows(
                IllegalArgumentException.class,
                () -> collaborateService.getCollaborators(owner, 10L, -1, 20, "username", "ASC", null, null)
        );
        assertEquals("Invalid pagination parameters.", pageEx.getMessage());

        IllegalArgumentException sizeEx = assertThrows(
                IllegalArgumentException.class,
                () -> collaborateService.getCollaborators(owner, 10L, 0, 0, "username", "ASC", null, null)
        );
        assertEquals("Invalid pagination parameters.", sizeEx.getMessage());

        IllegalArgumentException fieldEx = assertThrows(
                IllegalArgumentException.class,
                () -> collaborateService.getCollaborators(owner, 10L, 0, 20, "responsible", "ASC", null, null)
        );
        assertEquals("Invalid sort field. Allowed: username, email.", fieldEx.getMessage());

        IllegalArgumentException dirEx = assertThrows(
                IllegalArgumentException.class,
                () -> collaborateService.getCollaborators(owner, 10L, 0, 20, "username", "UP", null, null)
        );
        assertEquals("Invalid sort direction. Use ASC or DESC.", dirEx.getMessage());
    }

    // Test: collaborator listing returns mapped DTOs.
    @Test
    void shouldGetCollaboratorsWithMapping() {
        User owner = user(1L);
        User collaborator = user(2L);
        Task task = task(10L, owner);
        Collaborate collab = Collaborate.builder()
                .id(100L)
                .task(task)
                .user(collaborator)
                .responsible(false)
                .build();

        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(collaborateRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(collab)));

        Page<CollaboratorResponse> page = collaborateService.getCollaborators(
                owner, 10L, 0, 20, "username", "ASC", "u2", "example"
        );

        assertEquals(1, page.getTotalElements());
        assertEquals(2L, page.getContent().get(0).getUserId());
    }

    // Test: responsible collaborator retrieval succeeds when present.
    @Test
    void shouldGetResponsibleCollaborator() {
        User owner = user(1L);
        User collaborator = user(2L);
        Task task = task(10L, owner);
        Collaborate responsible = Collaborate.builder()
                .id(100L)
                .task(task)
                .user(collaborator)
                .responsible(true)
                .build();

        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(collaborateRepository.findByTaskAndResponsibleTrue(task)).thenReturn(Optional.of(responsible));

        CollaboratorResponse response = collaborateService.getResponsible(owner, 10L);

        assertEquals(2L, response.getUserId());
        assertEquals(true, response.isResponsible());
    }

    // Test: responsible collaborator retrieval fails when none is set.
    @Test
    void shouldRejectGetResponsibleWhenNoneExists() {
        User owner = user(1L);
        Task task = task(10L, owner);
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(collaborateRepository.findByTaskAndResponsibleTrue(task)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> collaborateService.getResponsible(owner, 10L)
        );

        assertEquals("No responsible collaborator defined for this task.", ex.getMessage());
    }

    // Test: removing a non-responsible collaborator succeeds.
    @Test
    void shouldRemoveCollaboratorWhenNotResponsible() {
        User owner = user(1L);
        User collaborator = user(2L);
        Task task = task(10L, owner);
        Collaborate collab = Collaborate.builder()
                .id(100L)
                .task(task)
                .user(collaborator)
                .responsible(false)
                .build();
        task.setCollaborations(new ArrayList<>(List.of(collab)));

        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(userRepository.findById(2L)).thenReturn(Optional.of(collaborator));
        when(collaborateRepository.findByTaskAndUser(task, collaborator)).thenReturn(Optional.of(collab));

        collaborateService.removeCollaborator(owner, 10L, 2L);

        verify(collaborateRepository).delete(collab);
    }

    // Test: removing the only responsible collaborator is rejected.
    @Test
    void shouldRejectRemovingOnlyResponsibleCollaborator() {
        User owner = user(1L);
        User collaborator = user(2L);
        Task task = task(10L, owner);
        Collaborate collab = Collaborate.builder()
                .id(100L)
                .task(task)
                .user(collaborator)
                .responsible(true)
                .build();

        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(userRepository.findById(2L)).thenReturn(Optional.of(collaborator));
        when(collaborateRepository.findByTaskAndUser(task, collaborator)).thenReturn(Optional.of(collab));
        when(collaborateRepository.findByTaskAndResponsibleTrue(task)).thenReturn(Optional.of(collab));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> collaborateService.removeCollaborator(owner, 10L, 2L)
        );

        assertEquals("Cannot remove the responsible collaborator without assigning another responsible first.", ex.getMessage());
        verify(collaborateRepository, never()).delete(any(Collaborate.class));
    }

    // Test: remove collaborator rejects unknown collaborator user.
    @Test
    void shouldRejectRemoveCollaboratorWhenUserNotFound() {
        User owner = user(1L);
        Task task = task(10L, owner);
        when(taskRepository.findById(10L)).thenReturn(Optional.of(task));
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> collaborateService.removeCollaborator(owner, 10L, 404L)
        );

        assertEquals("Collaborator user not found.", ex.getMessage());
    }

    // Test: collaborator operations reject task not owned by current user.
    @Test
    void shouldRejectCollaboratorOperationsWhenTaskNotOwned() {
        User owner = user(1L);
        User other = user(2L);
        Task foreignTask = task(10L, other);
        when(taskRepository.findById(10L)).thenReturn(Optional.of(foreignTask));

        AddCollaboratorRequest request = new AddCollaboratorRequest();
        request.setUserId(2L);
        request.setResponsible(false);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> collaborateService.addOrUpdateCollaborator(owner, 10L, request)
        );

        assertEquals("Access denied: task does not belong to a project of current user.", ex.getMessage());
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
}
