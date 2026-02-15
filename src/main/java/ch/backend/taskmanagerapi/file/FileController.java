package ch.backend.taskmanagerapi.file;

import ch.backend.taskmanagerapi.user.User;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

// REST controller exposing file-related endpoints scoped to a task and the authenticated user. 
@RestController
@RequestMapping("/tasks/{taskId}")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    // Uploads a file to the specified task.
    @PostMapping("/files")
    public ResponseEntity<FileResponse> uploadFile(
            Authentication authentication,
            @PathVariable Long taskId,
            @RequestParam("file") MultipartFile file
    ) throws Exception {
        User currentUser = (User) authentication.getPrincipal();
        FileResponse response = fileService.uploadFile(currentUser, taskId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Retrieves a paginated list of files for the specified task.
    @GetMapping("/files/page")
    public ResponseEntity<Page<FileResponse>> getFiles(
            Authentication authentication,
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "filename") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction,
            @RequestParam(required = false) String filename
    ) {
        User currentUser = (User) authentication.getPrincipal();
        Page<FileResponse> pageResponse = fileService.getFiles(
                currentUser,
                taskId,
                page,
                size,
                sortBy,
                direction,
                filename
        );
        return ResponseEntity.ok(pageResponse);
    }

    // Retrieves a file by its ID for the specified task.
    @GetMapping("/files/{fileId}")
    public ResponseEntity<FileResponse> getFile(
            Authentication authentication,
            @PathVariable Long taskId,
            @PathVariable Long fileId
    ) {
        User currentUser = (User) authentication.getPrincipal();
        FileResponse response = fileService.getFile(currentUser, taskId, fileId);
        return ResponseEntity.ok(response);
    }

    // Deletes a file by its ID for the specified task.
    @DeleteMapping("/files/{fileId}")
    public ResponseEntity<Void> deleteFile(
            Authentication authentication,
            @PathVariable Long taskId,
            @PathVariable Long fileId
    ) {
        User currentUser = (User) authentication.getPrincipal();
        fileService.deleteFile(currentUser, taskId, fileId);
        return ResponseEntity.noContent().build();
    }
}
