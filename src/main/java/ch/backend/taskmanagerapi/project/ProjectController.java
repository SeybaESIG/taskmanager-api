package ch.backend.taskmanagerapi.project;

import ch.backend.taskmanagerapi.user.User;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;


/**
 * REST controller exposing project-related endpoints for the authenticated user.
 */
@RestController
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    // Creates a project owned by the current user.
    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(
            Authentication authentication,
            @Valid @RequestBody CreateProjectRequest request
    ) {
        User currentUser = (User) authentication.getPrincipal();
        ProjectResponse response = projectService.createProject(currentUser, request);
        return ResponseEntity.ok(response);
    }

    // Returns owned projects with pagination, sorting, and optional filters.
    @GetMapping("/page")
    public ResponseEntity<Page<ProjectResponse>> getMyProjectsPaged(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "projectName") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String status
    ) {
        User currentUser = (User) authentication.getPrincipal();
        Page<ProjectResponse> projectsPage = projectService.getProjectsForOwner(
                currentUser,
                page,
                size,
                sortBy,
                direction,
                projectName,
                startDate,
                endDate,
                status
        );

        return ResponseEntity.ok(projectsPage);
    }

    // Returns one owned project by id.
    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> getProjectById(
            Authentication authentication,
            @PathVariable Long id
    ) {
        User currentUser = (User) authentication.getPrincipal();
        ProjectResponse response = projectService.getProjectById(currentUser, id);
        return ResponseEntity.ok(response);
    }

    // Partially updates an owned project.
    @PatchMapping("/{id}")
    public ResponseEntity<ProjectResponse> updateProject(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdateProjectRequest request
    ) {
        User currentUser = (User) authentication.getPrincipal();
        ProjectResponse response = projectService.updateProject(currentUser, id, request);
        return ResponseEntity.ok(response);
    }

    // Deletes an owned project (JPA mappings handle dependent records).
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(
            Authentication authentication,
            @PathVariable Long id
    ) {
        User currentUser = (User) authentication.getPrincipal();
        projectService.deleteProject(currentUser, id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

}
