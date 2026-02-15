package ch.backend.taskmanagerapi.collaborate;

import ch.backend.taskmanagerapi.task.Task;
import ch.backend.taskmanagerapi.task.TaskRepository;
import ch.backend.taskmanagerapi.user.User;
import ch.backend.taskmanagerapi.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Handles collaborator workflows and responsibility rules on owned tasks.
@Service
@Transactional
public class CollaborateService {

    private final CollaborateRepository collaborateRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    public CollaborateService(CollaborateRepository collaborateRepository,
                              TaskRepository taskRepository,
                              UserRepository userRepository) {
        this.collaborateRepository = collaborateRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
    }

    // Adds or updates a collaborator, enforcing single-responsible semantics per task.
    public CollaboratorResponse addOrUpdateCollaborator(User currentUser, Long taskId, AddCollaboratorRequest request) {
        Task task = getOwnedTask(currentUser, taskId);

        User collaboratorUser = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Collaborator user not found."));

        Collaborate collaborate = collaborateRepository
                .findByTaskAndUser(task, collaboratorUser)
                .orElseGet(() -> Collaborate.builder()
                        .task(task)
                        .user(collaboratorUser)
                        .build()
                );

        boolean newResponsible = Boolean.TRUE.equals(request.getResponsible());

        if (newResponsible && collaborate.isResponsible()) {
            throw new IllegalArgumentException("User is already responsible for this task.");
        }

        if (newResponsible) {
            // If responsibility changes, clear it from the previous responsible collaborator.
            collaborateRepository.findByTaskAndResponsibleTrue(task).ifPresent(existing -> {
                if (!existing.getUser().getId().equals(collaboratorUser.getId())) {
                    existing.setResponsible(false);
                    collaborateRepository.save(existing);
                }
            });
        }

        collaborate.setResponsible(newResponsible);

        Collaborate saved = collaborateRepository.save(collaborate);

        return toResponse(saved);
    }

    // Returns paginated collaborators with validated sort options and optional search filters.
    @Transactional(readOnly = true)
    public Page<CollaboratorResponse> getCollaborators(
            User currentUser,
            Long taskId,
            int page,
            int size,
            String sortBy,
            String direction,
            String username,
            String email
    ) {
        if (page < 0 || size <= 0) {
            throw new IllegalArgumentException("Invalid pagination parameters.");
        }

        // Only allow username/email sorting to avoid confusion.
        Set<String> sortableFields = Set.of("username", "email");
        if (!sortableFields.contains(sortBy)) {
            throw new IllegalArgumentException("Invalid sort field. Allowed: username, email.");
        }

        Sort.Direction sortDirection;
        try {
            sortDirection = Sort.Direction.fromString(direction);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid sort direction. Use ASC or DESC.");
        }

        // Public API fields map to nested collaborator user fields in JPA.
        String sortProperty = sortBy.equals("username") ? "user.username" : "user.email";
        // Stable secondary ordering prevents row shuffling when values are equal.
        Sort sort = Sort.by(sortDirection, sortProperty).and(Sort.by(Sort.Direction.DESC, "id"));
        Pageable pageable = PageRequest.of(page, size, sort);

        Task task = getOwnedTask(currentUser, taskId);

        Specification<Collaborate> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            jakarta.persistence.criteria.Join<Object, Object> userJoin = root.join("user");

            // Validate that the task belongs to the current user.
            predicates.add(cb.equal(root.get("task"), task));

            if (username != null && !username.isBlank()) {
                predicates.add(
                        cb.like(
                                cb.lower(userJoin.get("username")),
                                "%" + username.trim().toLowerCase() + "%"
                        )
                );
            }

            if (email != null && !email.isBlank()) {
                predicates.add(
                        cb.like(
                                cb.lower(userJoin.get("email")),
                                "%" + email.trim().toLowerCase() + "%"
                        )
                );
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Page<Collaborate> pageResult = collaborateRepository.findAll(spec, pageable);
        return pageResult.map(this::toResponse);
    }

    // Returns the collaborator currently marked as responsible for the task.
    @Transactional(readOnly = true)
    public CollaboratorResponse getResponsible(User currentUser, Long taskId) {
        Task task = getOwnedTask(currentUser, taskId);

        Collaborate responsible = collaborateRepository.findByTaskAndResponsibleTrue(task)
                .orElseThrow(() -> new IllegalArgumentException("No responsible collaborator defined for this task."));

        return toResponse(responsible);
    }

    // Removes a collaborator; the current responsible cannot be removed without a replacement.
    public void removeCollaborator(User currentUser, Long taskId, Long userId) {
        Task task = getOwnedTask(currentUser, taskId);

        User collaboratorUser = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Collaborator user not found."));

        Collaborate collab = collaborateRepository.findByTaskAndUser(task, collaboratorUser)
                .orElseThrow(() -> new IllegalArgumentException("Collaborator not found for this task."));

        if (collab.isResponsible()) {
            // Enforce continuity: a task must keep a responsible collaborator.
            boolean hasAnotherResponsible = collaborateRepository.findByTaskAndResponsibleTrue(task)
                    .filter(existing -> !existing.getUser().getId().equals(collaboratorUser.getId()))
                    .isPresent();

            if (!hasAnotherResponsible) {
                throw new IllegalArgumentException("Cannot remove the responsible collaborator without assigning another responsible first.");
            }
        }

        task.getCollaborations().remove(collab);
        collaborateRepository.delete(collab);
    }

    // Loads a task and ensures it belongs to the authenticated owner.
    private Task getOwnedTask(User owner, Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found."));

        if (!task.getProject().getOwner().getId().equals(owner.getId())) {
            throw new IllegalArgumentException("Access denied: task does not belong to a project of current user.");
        }

        return task;
    }

    // Maps collaborator entities to API-facing DTOs.
    private CollaboratorResponse toResponse(Collaborate collaborate) {
        return new CollaboratorResponse(
                collaborate.getUser().getId(),
                collaborate.getUser().getUsername(),
                collaborate.getUser().getEmail(),
                collaborate.isResponsible()
        );
    }
}
