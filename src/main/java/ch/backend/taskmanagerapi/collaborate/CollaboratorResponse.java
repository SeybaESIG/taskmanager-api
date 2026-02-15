package ch.backend.taskmanagerapi.collaborate;

import lombok.AllArgsConstructor;
import lombok.Getter;

// Represents a response containing collaborator information.
@Getter
@AllArgsConstructor
public class CollaboratorResponse {

    private Long userId;
    private String username;
    private String email;
    private boolean responsible;
}
