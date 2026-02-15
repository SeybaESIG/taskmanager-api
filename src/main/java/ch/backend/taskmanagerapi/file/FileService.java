package ch.backend.taskmanagerapi.file;

import ch.backend.taskmanagerapi.error.ResourceOwnershipException;
import ch.backend.taskmanagerapi.task.Task;
import ch.backend.taskmanagerapi.task.TaskRepository;
import ch.backend.taskmanagerapi.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Service responsible for handling file-related operations.
@Service
public class FileService {

    private static final long MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024L;

    private final FileRepository fileRepository;
    private final TaskRepository taskRepository;

    public FileService(FileRepository fileRepository, TaskRepository taskRepository) {
        this.fileRepository = fileRepository;
        this.taskRepository = taskRepository;
    }

    private Task getOwnedTask(User currentUser, Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found."));

        if (!task.getProject().getOwner().getId().equals(currentUser.getId())) {
            throw new ResourceOwnershipException("Task not owned by current user.");
        }

        return task;
    }

    // Uploads a file to the specified task.
    public FileResponse uploadFile(User currentUser, Long taskId, MultipartFile multipartFile) throws IOException {
        if (multipartFile.isEmpty()) {
            throw new IllegalArgumentException("File is empty.");
        }

        if (multipartFile.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File exceeds maximum size of 2MB.");
        }

        Task task = getOwnedTask(currentUser, taskId);

        // Mocked storage URL for demonstration
        String storedUrl = "/mock-storage/tasks/" + taskId + "/" + multipartFile.getOriginalFilename();

        File file = File.builder()
                .task(task)
                .filename(multipartFile.getOriginalFilename())
                .fileUrl(storedUrl)
                .contentType(multipartFile.getContentType())
                .build();

        File saved = fileRepository.save(file);
        return toResponse(saved);
    }

    // Retrieves a paginated list of files for the specified task.
    public Page<FileResponse> getFiles(
            User currentUser,
            Long taskId,
            int page,
            int size,
            String sortBy,
            String direction,
            String filename
    ) {
        if (page < 0 || size <= 0) {
            throw new IllegalArgumentException("Invalid pagination parameters.");
        }

        Set<String> sortableFields = Set.of("filename");
        if (!sortableFields.contains(sortBy)) {
            throw new IllegalArgumentException("Invalid sort field. Allowed: filename.");
        }

        // Validates the sort direction.
        Sort.Direction sortDirection;
        try {
            sortDirection = Sort.Direction.fromString(direction);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid sort direction. Use ASC or DESC.");
        }

        Sort sort = Sort.by(sortDirection, sortBy).and(Sort.by(Sort.Direction.DESC, "id"));
        Pageable pageable = PageRequest.of(page, size, sort);

        Task task = getOwnedTask(currentUser, taskId);

        // Sorting the files by the specified field. 
        Specification<File> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("task"), task));

            if (filename != null && !filename.isBlank()) {
                predicates.add(
                        cb.like(
                                cb.lower(root.get("filename")),
                                "%" + filename.trim().toLowerCase() + "%"
                        )
                );
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        return fileRepository.findAll(spec, pageable).map(this::toResponse);
    }

    // Retrieves a file by its ID for the specified task.
    public FileResponse getFile(User currentUser, Long taskId, Long fileId) {
        Task task = getOwnedTask(currentUser, taskId);

        File file = fileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found."));

        if (!file.getTask().getId().equals(task.getId())) {
            throw new IllegalArgumentException("File does not belong to the specified task.");
        }

        return toResponse(file);
    }

    // Deletes a file by its ID for the specified task.
    public void deleteFile(User currentUser, Long taskId, Long fileId) {
        Task task = getOwnedTask(currentUser, taskId);

        File file = fileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found."));

        if (!file.getTask().getId().equals(task.getId())) {
            throw new IllegalArgumentException("File does not belong to the specified task.");
        }

        fileRepository.delete(file);
    }

    // Converts a File entity to a FileResponse.
    private FileResponse toResponse(File file) {
        return FileResponse.builder()
                .id(file.getId())
                .filename(file.getFilename())
                .fileUrl(file.getFileUrl())
                .contentType(file.getContentType())
                .build();
    }
}
