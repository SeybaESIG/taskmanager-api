package ch.backend.taskmanagerapi.collaborate;

import ch.backend.taskmanagerapi.task.Task;
import ch.backend.taskmanagerapi.user.User;
import jakarta.persistence.*;
import lombok.*;

// Represents a collaboration between a user and a task, indicating whether the user is responsible for the task or not.
@Entity
@Table(name = "tb_collaborate")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Collaborate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "collaboration_id")
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "is_responsible", nullable = false)
    private boolean responsible;
}
