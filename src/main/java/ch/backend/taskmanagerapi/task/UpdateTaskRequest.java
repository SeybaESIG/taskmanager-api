package ch.backend.taskmanagerapi.task;

import ch.backend.taskmanagerapi.config.TaskStatus;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * DTO representing the payload used to partially update a task.
 * All fields are optional to support PATCH semantics.
 */
@Getter
@Setter
public class UpdateTaskRequest {

    @Size(min = 3, max = 255)
    private String taskName;

    private TaskStatus status;

    @FutureOrPresent
    private LocalDate dueDate;

    @Size(max = 1000)
    private String description;
}
