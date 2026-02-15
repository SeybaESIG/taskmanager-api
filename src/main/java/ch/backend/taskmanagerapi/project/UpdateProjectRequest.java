package ch.backend.taskmanagerapi.project;

import ch.backend.taskmanagerapi.config.ProjectStatus;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * DTO representing the payload used to partially update a project.
 * All fields are optional to support PATCH semantics.
 */
@Getter
@Setter
public class UpdateProjectRequest {

    @Size(min = 3, max = 255)
    private String projectName;

    private ProjectStatus status;

    @FutureOrPresent
    private LocalDate startDate;

    private LocalDate endDate;
}
