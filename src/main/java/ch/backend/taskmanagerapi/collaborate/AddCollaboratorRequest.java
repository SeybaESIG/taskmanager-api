package ch.backend.taskmanagerapi.collaborate;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO representing the payload required to add or update a collaborator on a task.
 */
@Getter
@Setter
public class AddCollaboratorRequest {

    @NotNull
    private Long userId;

    @NotNull
    private Boolean responsible;
}
