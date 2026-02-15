package ch.backend.taskmanagerapi.task;

import ch.backend.taskmanagerapi.project.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

//Repository responsible for performing CRUD operations on Task entities.
public interface TaskRepository extends JpaRepository<Task, Long>, JpaSpecificationExecutor<Task> {

    // Returns all tasks linked to a project.
    List<Task> findByProject(Project project);

    // Returns paginated tasks linked to a project.
    Page<Task> findByProject(Project project, Pageable pageable);
}
