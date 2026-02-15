package ch.backend.taskmanagerapi.project;

import ch.backend.taskmanagerapi.user.User;
import ch.backend.taskmanagerapi.user.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Service layer for managing projects and enforcing project-related business rules.
@Service
@Transactional
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserService userService;

    public ProjectService(ProjectRepository projectRepository, UserService userService) {
        this.projectRepository = projectRepository;
        this.userService = userService;
    }

    // Creates a project for the authenticated owner.
    public ProjectResponse createProject(User owner, CreateProjectRequest request) {
        if (request.getEndDate() != null
                && request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("End date must be on or after start date.");
        }

        Project project = Project.builder()
                .owner(owner)
                .projectName(request.getProjectName())
                .status(request.getStatus())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .build();

        Project saved = projectRepository.save(project);
        return toResponse(saved);
    }

    // Returns owner projects with validated pagination, sorting, and optional filters.
    @Transactional(readOnly = true)
    public Page<ProjectResponse> getProjectsForOwner(
            User owner,
            int page,
            int size,
            String sortBy,
            String direction,
            String projectName,
            LocalDate startDate,
            LocalDate endDate,
            String status
    ) {
        if (page < 0 || size <= 0) {
            throw new IllegalArgumentException("Invalid pagination parameters.");
        }

        Set<String> sortableFields = Set.of("projectName", "startDate", "endDate");
        if (!sortableFields.contains(sortBy)) {
            throw new IllegalArgumentException("Invalid sort field. Allowed: projectName, startDate, endDate.");
        }

        Sort.Direction sortDirection;
        try {
            sortDirection = Sort.Direction.fromString(direction);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid sort direction. Use ASC or DESC.");
        }

        // Stable secondary sort keeps pagination order deterministic when primary keys are equal.
        Sort sort = Sort.by(sortDirection, sortBy).and(Sort.by(Sort.Direction.DESC, "id"));
        Pageable pageable = PageRequest.of(page, size, sort);

        // Build dynamic query predicates from optional filters.
        Specification<Project> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            // Security boundary: always scope results to the current owner.
            predicates.add(cb.equal(root.get("owner"), owner));

            if (projectName != null && !projectName.isBlank()) {
                predicates.add(
                        cb.like(
                                cb.lower(root.get("projectName")),
                                "%" + projectName.trim().toLowerCase() + "%"
                        )
                );
            }

            if (startDate != null) {
                predicates.add(cb.equal(root.get("startDate"), startDate));
            }

            if (endDate != null) {
                predicates.add(cb.equal(root.get("endDate"), endDate));
            }

            if (status != null && !status.isBlank()) {
                // Status filter is case-insensitive for request convenience.
                predicates.add(cb.equal(root.get("status").as(String.class), status.trim().toUpperCase()));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        // Fetch entities first, then map to API DTOs.
        Page<Project> pageResult = projectRepository.findAll(spec, pageable);
        return pageResult.map(this::toResponse);
    }

    // Returns a single owned project by id.
    @Transactional(readOnly = true)
    public ProjectResponse getProjectById(User owner, Long projectId) {
        Project project = getOwnedProject(owner, projectId);
        return toResponse(project);
    }

    // Maps project entities to API-facing DTOs.
    private ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getProjectName(),
                project.getStatus(),
                project.getStartDate(),
                project.getEndDate()
        );
    }

    // Loads a project and enforces owner-only access.
    private Project getOwnedProject(User owner, Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found."));

        if (!project.getOwner().getId().equals(owner.getId())) {
            throw new IllegalArgumentException("Access denied: project does not belong to current user.");
        }

        return project;
    }


    // Partially updates an owned project while preserving date consistency.
    public ProjectResponse updateProject(User owner, Long projectId, UpdateProjectRequest request) {
        Project project = getOwnedProject(owner, projectId);

        // Compute effective dates first so validation works for partial payloads.
        LocalDate newStartDate = request.getStartDate() != null ? request.getStartDate() : project.getStartDate();
        LocalDate newEndDate = request.getEndDate() != null ? request.getEndDate() : project.getEndDate();

        if (newEndDate != null && newEndDate.isBefore(newStartDate)) {
            throw new IllegalArgumentException("End date must be on or after start date.");
        }

        // Apply only fields present in the request.
        if (request.getProjectName() != null) {
            project.setProjectName(request.getProjectName());
        }
        if (request.getStatus() != null) {
            project.setStatus(request.getStatus());
        }
        if (request.getStartDate() != null) {
            project.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            project.setEndDate(request.getEndDate());
        }

        Project saved = projectRepository.save(project);
        return toResponse(saved);
    }

    // Deletes an owned project; JPA mappings handle related records.
    public void deleteProject(User owner, Long projectId) {
        Project project = getOwnedProject(owner, projectId);
        projectRepository.delete(project);
    }
}
