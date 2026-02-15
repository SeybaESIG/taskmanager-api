package ch.backend.taskmanagerapi.collaborate;

import ch.backend.taskmanagerapi.user.User;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

// Manages task collaborators for authenticated task owners.
@RestController
@RequestMapping("/tasks/{taskId}")
public class CollaborateController {

    private final CollaborateService collaborateService;

    public CollaborateController(CollaborateService collaborateService) {
        this.collaborateService = collaborateService;
    }

    // Adds a collaborator or updates its responsibility state on the target task.
    @PostMapping("/collaborators")
    public ResponseEntity<CollaboratorResponse> addOrUpdateCollaborator(
            Authentication authentication,
            @PathVariable Long taskId,
            @Valid @RequestBody AddCollaboratorRequest request
    ) {
        User currentUser = (User) authentication.getPrincipal();
        CollaboratorResponse response = collaborateService.addOrUpdateCollaborator(currentUser, taskId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Returns paginated collaborators with optional username/email filtering and sorting.
    @GetMapping("/collaborators")
    public ResponseEntity<Page<CollaboratorResponse>> getCollaborators(
            Authentication authentication,
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "username") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email
    ) {
        User currentUser = (User) authentication.getPrincipal();
        Page<CollaboratorResponse> collaborators = collaborateService.getCollaborators(
                currentUser,
                taskId,
                page,
                size,
                sortBy,
                direction,
                username,
                email
        );

        return ResponseEntity.ok(collaborators);
    }

    // Fetches the user currently marked as responsible for the task.
    @GetMapping("/responsible")
    public ResponseEntity<CollaboratorResponse> getResponsible(
            Authentication authentication,
            @PathVariable Long taskId
    ) {
        User currentUser = (User) authentication.getPrincipal();
        CollaboratorResponse response = collaborateService.getResponsible(currentUser, taskId);
        return ResponseEntity.ok(response);
    }

    // Removes a collaborator while enforcing the responsible-collaborator rule.
    @DeleteMapping("/collaborators/{userId}")
    public ResponseEntity<Void> removeCollaborator(
            Authentication authentication,
            @PathVariable Long taskId,
            @PathVariable Long userId
    ) {
        User currentUser = (User) authentication.getPrincipal();
        collaborateService.removeCollaborator(currentUser, taskId, userId);
        return ResponseEntity.noContent().build();
    }
}
