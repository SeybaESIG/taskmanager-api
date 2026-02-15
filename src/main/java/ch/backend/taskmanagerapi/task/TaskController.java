package ch.backend.taskmanagerapi.task;

import ch.backend.taskmanagerapi.user.User;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * REST controller exposing task-related endpoints scoped to a project and the authenticated user.
 */
@RestController
@RequestMapping("/projects/{projectId}/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * Creates a new task under the specified project for the current user.
     *
     * @param authentication current authentication context containing the principal
     * @param projectId      identifier of the project
     * @param request        payload describing the task to create
     * @return DTO representing the created task
     */
    @PostMapping
    public ResponseEntity<TaskResponse> createTask(
            Authentication authentication,
            @PathVariable Long projectId,
            @Valid @RequestBody CreateTaskRequest request
    ) {
        User currentUser = (User) authentication.getPrincipal();
        TaskResponse response = taskService.createTask(currentUser, projectId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves a paginated list of tasks for the specified project owned by the current user.
     *
     * @param authentication current authentication context containing the principal
     * @param projectId      identifier of the project
     * @param page           zero-based page index
     * @param size           number of items per page
     * @return a page of task DTOs
     */
    @GetMapping("/page")
    public ResponseEntity<Page<TaskResponse>> getTasksPaged(
            Authentication authentication,
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "taskName") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction,
            @RequestParam(required = false) String taskName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDate,
            @RequestParam(required = false) String status
    ) {
        User currentUser = (User) authentication.getPrincipal();
        Page<TaskResponse> tasksPage = taskService.getTasksForProject(
                currentUser,
                projectId,
                page,
                size,
                sortBy,
                direction,
                taskName,
                dueDate,
                status
        );

        return ResponseEntity.ok(tasksPage);
    }

    /**
     * Retrieves a single task by id for the specified project and current user.
     *
     * @param authentication current authentication context containing the principal
     * @param projectId      identifier of the project
     * @param id             identifier of the task
     * @return DTO representing the task
     */
    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getTaskById(
            Authentication authentication,
            @PathVariable Long projectId,
            @PathVariable Long id
    ) {
        User currentUser = (User) authentication.getPrincipal();
        TaskResponse response = taskService.getTaskByIdForProject(currentUser, projectId, id);
        return ResponseEntity.ok(response);
    }

    /**
     * Partially updates a task by id, ensuring it belongs to a project
     * owned by the current user.
     *
     * @param authentication current authentication context containing the principal
     * @param id             identifier of the task to update
     * @param request        payload describing the fields to update
     * @return DTO representing the updated task
     */
    @PatchMapping("/{id}")
    public ResponseEntity<TaskResponse> updateTask(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdateTaskRequest request
    ) {
        User currentUser = (User) authentication.getPrincipal();
        TaskResponse response = taskService.updateTask(currentUser, id, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Deletes a task by id, ensuring it belongs to a project
     * owned by the current user.
     * Deletion cascades to collaborators and files via JPA mappings.
     *
     * @param authentication current authentication context containing the principal
     * @param id             identifier of the task to delete
     * @return HTTP 204 if the deletion succeeds
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(
            Authentication authentication,
            @PathVariable Long id
    ) {
        User currentUser = (User) authentication.getPrincipal();
        taskService.deleteTask(currentUser, id);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
