package com.goalflow.goal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "milestones")
public class MilestoneEntity {

    @Id
    String id;

    @Column(name = "user_id", nullable = false, length = 36)
    String userId;

    @Column(name = "goal_id", nullable = false, length = 36)
    String goalId;

    @Column(nullable = false, length = 120)
    String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    String acceptance;

    @Column(name = "target_date", nullable = false)
    LocalDate targetDate;

    @Column(nullable = false, precision = 6, scale = 2)
    BigDecimal weight;

    @Column(nullable = false, precision = 6, scale = 2)
    BigDecimal progress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    MilestoneStatus status;

    @Column(name = "completed_at")
    Instant completedAt;

    @Column(name = "sort_order", nullable = false)
    int sortOrder;

    @Version
    @Column(nullable = false)
    Integer version;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    protected MilestoneEntity() {
    }

    static MilestoneEntity create(String userId, String goalId) {
        var now = Instant.now();
        var milestone = new MilestoneEntity();
        milestone.id = UUID.randomUUID().toString();
        milestone.userId = userId;
        milestone.goalId = goalId;
        milestone.progress = BigDecimal.ZERO;
        milestone.status = MilestoneStatus.NOT_STARTED;
        milestone.createdAt = now;
        milestone.updatedAt = now;
        return milestone;
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
