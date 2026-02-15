package ch.backend.taskmanagerapi.error;

// Exception thrown when a user tries to access a resource they do not own. 
public class ResourceOwnershipException extends RuntimeException {
    public ResourceOwnershipException(String message) {
        super(message);
    }
}
