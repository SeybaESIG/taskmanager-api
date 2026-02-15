package ch.backend.taskmanagerapi.task;

import ch.backend.taskmanagerapi.config.TaskStatus;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * DTO representing the payload required to create a new task within a project.
 */
@Getter
@Setter
public class CreateTaskRequest {

    @NotBlank
    @Size(max = 100)
    private String taskName;

    @NotNull
    private TaskStatus status;

    @NotNull
    @FutureOrPresent
    private LocalDate dueDate;

    @Size(max = 1000)
    private String description;
}
