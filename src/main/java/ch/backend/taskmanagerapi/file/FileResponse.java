package ch.backend.taskmanagerapi.file;

import lombok.Builder;
import lombok.Data;

// Represents a response containing file information.
@Data
@Builder
public class FileResponse {
    private Long id;
    private String filename;
    private String fileUrl;
    private String contentType;
}
