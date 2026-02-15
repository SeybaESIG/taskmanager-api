package ch.backend.taskmanagerapi.project;

import ch.backend.taskmanagerapi.config.ProjectStatus;
import ch.backend.taskmanagerapi.config.Role;
import ch.backend.taskmanagerapi.user.User;
import ch.backend.taskmanagerapi.user.UserService;
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
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private UserService userService;

    private ProjectService projectService;

    // Setup: create service with mocked dependencies.
    @BeforeEach
    void setUp() {
        projectService = new ProjectService(projectRepository, userService);
    }

    // Test: project creation succeeds with valid dates.
    @Test
    void shouldCreateProjectSuccessfully() {
        User owner = user(1L);
        CreateProjectRequest request = new CreateProjectRequest();
        request.setProjectName("Alpha");
        request.setStatus(ProjectStatus.ACTIVE);
        request.setStartDate(LocalDate.of(2026, 2, 14));
        request.setEndDate(LocalDate.of(2026, 2, 20));

        when(projectRepository.save(any(Project.class))).thenAnswer(i -> {
            Project p = i.getArgument(0);
            p.setId(100L);
            return p;
        });

        ProjectResponse response = projectService.createProject(owner, request);

        assertEquals(100L, response.getId());
        assertEquals("Alpha", response.getProjectName());
        assertEquals(ProjectStatus.ACTIVE, response.getStatus());
    }

    // Test: project creation rejects end date before start date.
    @Test
    void shouldRejectProjectCreationWhenEndDateBeforeStartDate() {
        User owner = user(1L);
        CreateProjectRequest request = new CreateProjectRequest();
        request.setProjectName("Alpha");
        request.setStatus(ProjectStatus.ACTIVE);
        request.setStartDate(LocalDate.of(2026, 2, 20));
        request.setEndDate(LocalDate.of(2026, 2, 14));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> projectService.createProject(owner, request)
        );

        assertEquals("End date must be on or after start date.", ex.getMessage());
    }

    // Test: project listing rejects invalid pagination/sorting inputs.
    @Test
    void shouldRejectInvalidProjectPaginationAndSort() {
        User owner = user(1L);

        IllegalArgumentException pageEx = assertThrows(
                IllegalArgumentException.class,
                () -> projectService.getProjectsForOwner(owner, -1, 20, "projectName", "ASC", null, null, null, null)
        );
        assertEquals("Invalid pagination parameters.", pageEx.getMessage());

        IllegalArgumentException sizeEx = assertThrows(
                IllegalArgumentException.class,
                () -> projectService.getProjectsForOwner(owner, 0, 0, "projectName", "ASC", null, null, null, null)
        );
        assertEquals("Invalid pagination parameters.", sizeEx.getMessage());

        IllegalArgumentException fieldEx = assertThrows(
                IllegalArgumentException.class,
                () -> projectService.getProjectsForOwner(owner, 0, 20, "status", "ASC", null, null, null, null)
        );
        assertEquals("Invalid sort field. Allowed: projectName, startDate, endDate.", fieldEx.getMessage());

        IllegalArgumentException dirEx = assertThrows(
                IllegalArgumentException.class,
                () -> projectService.getProjectsForOwner(owner, 0, 20, "projectName", "UP", null, null, null, null)
        );
        assertEquals("Invalid sort direction. Use ASC or DESC.", dirEx.getMessage());
    }

    // Test: project listing returns mapped DTOs with filters applied.
    @Test
    void shouldGetProjectsForOwnerWithFiltersAndMapping() {
        User owner = user(1L);
        Project p = project(10L, owner, "Alpha API", ProjectStatus.ACTIVE);
        when(projectRepository.findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p)));

        Page<ProjectResponse> page = projectService.getProjectsForOwner(
                owner, 0, 20, "projectName", "ASC", "api",
                LocalDate.of(2026, 2, 14), null, "active"
        );

        assertEquals(1, page.getTotalElements());
        assertEquals("Alpha API", page.getContent().get(0).getProjectName());
    }

    // Test: owner can fetch project by id.
    @Test
    void shouldGetProjectByIdForOwner() {
        User owner = user(1L);
        Project p = project(10L, owner, "Alpha", ProjectStatus.ACTIVE);
        when(projectRepository.findById(10L)).thenReturn(Optional.of(p));

        ProjectResponse response = projectService.getProjectById(owner, 10L);

        assertEquals(10L, response.getId());
        assertEquals("Alpha", response.getProjectName());
    }

    // Test: fetching unknown project id is rejected.
    @Test
    void shouldRejectGetProjectByIdWhenProjectNotFound() {
        User owner = user(1L);
        when(projectRepository.findById(10L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> projectService.getProjectById(owner, 10L)
        );

        assertEquals("Project not found.", ex.getMessage());
    }

    // Test: fetching project not owned by current user is rejected.
    @Test
    void shouldRejectGetProjectByIdWhenNotOwned() {
        User owner = user(1L);
        User other = user(2L);
        Project p = project(10L, other, "Private", ProjectStatus.ACTIVE);
        when(projectRepository.findById(10L)).thenReturn(Optional.of(p));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> projectService.getProjectById(owner, 10L)
        );

        assertEquals("Access denied: project does not belong to current user.", ex.getMessage());
    }

    // Test: project update succeeds for owned project.
    @Test
    void shouldUpdateProjectSuccessfully() {
        User owner = user(1L);
        Project p = project(10L, owner, "Old", ProjectStatus.ACTIVE);
        when(projectRepository.findById(10L)).thenReturn(Optional.of(p));
        when(projectRepository.save(any(Project.class))).thenAnswer(i -> i.getArgument(0));

        UpdateProjectRequest request = new UpdateProjectRequest();
        request.setProjectName("New");
        request.setStatus(ProjectStatus.PAUSED);
        request.setEndDate(LocalDate.of(2026, 3, 1));

        ProjectResponse response = projectService.updateProject(owner, 10L, request);

        assertEquals("New", response.getProjectName());
        assertEquals(ProjectStatus.PAUSED, response.getStatus());
    }

    // Test: project update rejects invalid resulting date range.
    @Test
    void shouldRejectUpdateProjectWhenDatesBecomeInvalid() {
        User owner = user(1L);
        Project p = project(10L, owner, "Old", ProjectStatus.ACTIVE);
        p.setStartDate(LocalDate.of(2026, 2, 20));
        p.setEndDate(null);
        when(projectRepository.findById(10L)).thenReturn(Optional.of(p));

        UpdateProjectRequest request = new UpdateProjectRequest();
        request.setEndDate(LocalDate.of(2026, 2, 14));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> projectService.updateProject(owner, 10L, request)
        );

        assertEquals("End date must be on or after start date.", ex.getMessage());
        verify(projectRepository, never()).save(any(Project.class));
    }

    // Test: owner can delete a project.
    @Test
    void shouldDeleteOwnedProject() {
        User owner = user(1L);
        Project p = project(10L, owner, "ToDelete", ProjectStatus.ACTIVE);
        when(projectRepository.findById(10L)).thenReturn(Optional.of(p));

        projectService.deleteProject(owner, 10L);

        verify(projectRepository).delete(p);
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
    private Project project(Long id, User owner, String name, ProjectStatus status) {
        return Project.builder()
                .id(id)
                .owner(owner)
                .projectName(name)
                .status(status)
                .startDate(LocalDate.of(2026, 2, 14))
                .endDate(LocalDate.of(2026, 2, 20))
                .build();
    }
}
