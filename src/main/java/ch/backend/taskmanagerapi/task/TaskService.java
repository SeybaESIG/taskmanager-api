package ch.backend.taskmanagerapi.task;

import ch.backend.taskmanagerapi.project.Project;
import ch.backend.taskmanagerapi.project.ProjectRepository;
import ch.backend.taskmanagerapi.user.User;
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

//Service layer for managing tasks and enforcing task-related business rules.
@Service
@Transactional
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;

    public TaskService(TaskRepository taskRepository, ProjectRepository projectRepository) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
    }

    // Creates a task under an owned project.
    public TaskResponse createTask(User owner, Long projectId, CreateTaskRequest request) {
        Project project = getOwnedProject(owner, projectId);

        Task task = Task.builder()
                .project(project)
                .taskName(request.getTaskName())
                .status(request.getStatus())
                .dueDate(request.getDueDate())
                .description(request.getDescription())
                .build();

        Task saved = taskRepository.save(task);
        return toResponse(saved);
    }

    // Returns project tasks with validated pagination, sorting, and optional filters.
    @Transactional(readOnly = true)
    public Page<TaskResponse> getTasksForProject(
            User owner,
            Long projectId,
            int page,
            int size,
            String sortBy,
            String direction,
            String taskName,
            LocalDate dueDate,
            String status
    ) {
        if (page < 0 || size <= 0) {
            throw new IllegalArgumentException("Invalid pagination parameters.");
        }

        Set<String> sortableFields = Set.of("taskName", "dueDate");
        if (!sortableFields.contains(sortBy)) {
            throw new IllegalArgumentException("Invalid sort field. Allowed: taskName, dueDate.");
        }

        Sort.Direction sortDirection;
        try {
            sortDirection = Sort.Direction.fromString(direction);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid sort direction. Use ASC or DESC.");
        }

        // Stable secondary sort keeps page ordering deterministic.
        Sort sort = Sort.by(sortDirection, sortBy).and(Sort.by(Sort.Direction.DESC, "id"));
        Pageable pageable = PageRequest.of(page, size, sort);
        Project project = getOwnedProject(owner, projectId);

        // Build dynamic criteria from optional filter inputs.
        Specification<Task> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            // Security boundary: always scope results to the validated owned project.
            predicates.add(cb.equal(root.get("project"), project));

            if (taskName != null && !taskName.isBlank()) {
                predicates.add(
                        cb.like(
                                cb.lower(root.get("taskName")),
                                "%" + taskName.trim().toLowerCase() + "%"
                        )
                );
            }

            if (dueDate != null) {
                predicates.add(cb.equal(root.get("dueDate"), dueDate));
            }

            if (status != null && !status.isBlank()) {
                // Status filter is case-insensitive for request convenience.
                predicates.add(cb.equal(root.get("status").as(String.class), status.trim().toUpperCase()));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Page<Task> pageResult = taskRepository.findAll(spec, pageable);
        return pageResult.map(this::toResponse);
    }

    // Returns one task by id and verifies it belongs to the specified owned project.
    @Transactional(readOnly = true)
    public TaskResponse getTaskByIdForProject(User owner, Long projectId, Long taskId) {
        Project project = getOwnedProject(owner, projectId);
        Task task = getOwnedTask(owner, taskId);

        if (!task.getProject().getId().equals(project.getId())) {
            throw new IllegalArgumentException("Task does not belong to the specified project.");
        }

        return toResponse(task);
    }

    // Loads a task and enforces ownership through its parent project.
    private Task getOwnedTask(User owner, Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found."));

        Project project = task.getProject();
        if (!project.getOwner().getId().equals(owner.getId())) {
            throw new IllegalArgumentException(
                    "Access denied: task does not belong to a project of current user."
            );
        }

        return task;
    }

    // Partially updates an owned task.
    public TaskResponse updateTask(User owner, Long taskId, UpdateTaskRequest request) {
        Task task = getOwnedTask(owner, taskId);

        // Apply only fields present in the patch payload.
        if (request.getTaskName() != null) {
            task.setTaskName(request.getTaskName());
        }
        if (request.getStatus() != null) {
            task.setStatus(request.getStatus());
        }
        if (request.getDueDate() != null) {
            task.setDueDate(request.getDueDate());
        }
        if (request.getDescription() != null) {
            task.setDescription(request.getDescription());
        }

        Task saved = taskRepository.save(task);
        return toResponse(saved);
    }

    // Deletes an owned task; related entities are handled by JPA mappings.
    public void deleteTask(User owner, Long taskId) {
        Task task = getOwnedTask(owner, taskId);
        taskRepository.delete(task);
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

    // Maps task entities to API-facing DTOs.
    private TaskResponse toResponse(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTaskName(),
                task.getStatus(),
                task.getDueDate(),
                task.getDescription(),
                task.getProject().getId()
        );
    }
}
