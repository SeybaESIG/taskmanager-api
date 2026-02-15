package ch.backend.taskmanagerapi.project;

import ch.backend.taskmanagerapi.config.ProjectStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

// Represents a response containing project information.
@Getter
@AllArgsConstructor
public class ProjectResponse {

    private Long id;
    private String projectName;
    private ProjectStatus status;
    private LocalDate startDate;
    private LocalDate endDate;
}
