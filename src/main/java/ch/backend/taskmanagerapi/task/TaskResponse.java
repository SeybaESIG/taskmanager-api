package ch.backend.taskmanagerapi.task;

import ch.backend.taskmanagerapi.config.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

// Represents a response containing task information.
@Getter
@AllArgsConstructor
public class TaskResponse {

    private Long id;
    private String taskName;
    private TaskStatus status;
    private LocalDate dueDate;
    private String description;
    private Long projectId;
}
