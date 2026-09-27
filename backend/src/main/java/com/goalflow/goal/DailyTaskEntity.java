package com.goalflow.goal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "daily_tasks")
public class DailyTaskEntity {

    @Id
    String id;

    @Column(name = "user_id", nullable = false, length = 36)
    String userId;

    @Column(name = "goal_id", nullable = false, length = 36)
    String goalId;

    @Column(nullable = false, length = 120)
    String title;

    @Column(nullable = false)
    boolean active;

    @Column(name = "sort_order", nullable = false)
    int sortOrder;

    @Version
    @Column(nullable = false)
    Integer version;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    protected DailyTaskEntity() {
    }

    static DailyTaskEntity create(String userId, String goalId) {
        var now = Instant.now();
        var task = new DailyTaskEntity();
        task.id = UUID.randomUUID().toString();
        task.userId = userId;
        task.goalId = goalId;
        task.active = true;
        task.createdAt = now;
        task.updatedAt = now;
        return task;
    }

    @PrePersist
    void prePersist() {
        var now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (version == null) {
            version = 1;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    void touch() {
        updatedAt = Instant.now();
    }
}
