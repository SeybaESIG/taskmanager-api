package ch.backend.taskmanagerapi.file;

import ch.backend.taskmanagerapi.task.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

// Repository responsible for performing CRUD operations on File entities.
public interface FileRepository extends JpaRepository<File, Long>, JpaSpecificationExecutor<File> {

    Page<File> findByTask(Task task, Pageable pageable);
}
