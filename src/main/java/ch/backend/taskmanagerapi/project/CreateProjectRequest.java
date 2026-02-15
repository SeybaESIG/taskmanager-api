package ch.backend.taskmanagerapi.project;

import ch.backend.taskmanagerapi.config.ProjectStatus;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * DTO representing the payload required to create a new project.
 */
@Getter
@Setter
public class CreateProjectRequest {

    @NotBlank
    @Size(max = 100)
    private String projectName;

    @NotNull
    private ProjectStatus status;

    @NotNull
    @FutureOrPresent
    private LocalDate startDate;

    private LocalDate endDate;
}
