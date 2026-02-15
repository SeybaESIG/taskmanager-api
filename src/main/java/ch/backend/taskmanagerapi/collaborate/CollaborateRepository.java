package ch.backend.taskmanagerapi.collaborate;

import ch.backend.taskmanagerapi.task.Task;
import ch.backend.taskmanagerapi.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

// Repository responsible for performing CRUD operations on Collaborate entities.
public interface CollaborateRepository extends JpaRepository<Collaborate, Long>, JpaSpecificationExecutor<Collaborate> {
    // Returns paginated collaborators for a specific task.
    Page<Collaborate> findByTask(Task task, Pageable pageable);

    // Finds one collaborator entry by task and user.
    Optional<Collaborate> findByTaskAndUser(Task task, User user);

    // Returns the collaborator marked as responsible for the task, if present.
    Optional<Collaborate> findByTaskAndResponsibleTrue(Task task);

    // Counts collaborators linked to the task.
    long countByTask(Task task);
}
