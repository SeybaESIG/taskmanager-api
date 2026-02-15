package ch.backend.taskmanagerapi.file;

import ch.backend.taskmanagerapi.task.Task;
import jakarta.persistence.*;
import lombok.*;

// Represents a file associated with a task. 
@Entity
@Table(name = "tb_files")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class File {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "file_id")
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Column(name = "filename", nullable = false)
    private String filename;

    @Column(name = "file_url", nullable = false)
    private String fileUrl;

    @Column(name = "contentType", nullable = false)
    private String contentType;
}
