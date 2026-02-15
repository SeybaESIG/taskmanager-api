package ch.backend.taskmanagerapi.project;

import ch.backend.taskmanagerapi.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

//Repository responsible for performing CRUD operations on Project entities.
public interface ProjectRepository extends JpaRepository<Project, Long>, JpaSpecificationExecutor<Project> {

    List<Project> findByOwner(User owner);

    // Returns paginated list of projects for the given owner.
    Page<Project> findByOwner(User owner, Pageable pageable);
}
