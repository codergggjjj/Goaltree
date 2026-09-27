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
@Table(name = "goals")
public class GoalEntity {

    @Id
    String id;

    @Column(name = "user_id", nullable = false, length = 36)
    String userId;

    @Column(name = "parent_id", length = 36)
    String parentId;

    @Column(name = "sort_order", nullable = false)
    int sortOrder;

    @Column(nullable = false, length = 80)
    String title;

    @Column(columnDefinition = "TEXT")
    String description;

    @Column(name = "start_date", nullable = false)
    LocalDate startDate;

    @Column(name = "target_date", nullable = false)
    LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    GoalPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    GoalStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "progress_mode", nullable = false, length = 32)
    ProgressMode progressMode;

    @Column(name = "current_progress", nullable = false, precision = 6, scale = 2)
    BigDecimal currentProgress;

    @Column(name = "metric_name", length = 80)
    String metricName;

    @Column(name = "metric_unit", length = 32)
    String metricUnit;

    @Column(name = "metric_start_value", precision = 18, scale = 4)
    BigDecimal metricStartValue;

    @Column(name = "metric_target_value", precision = 18, scale = 4)
    BigDecimal metricTargetValue;

    @Column(name = "metric_current_value", precision = 18, scale = 4)
    BigDecimal metricCurrentValue;

    @Version
    @Column(nullable = false)
    Integer version;

    @Column(name = "paused_at")
    Instant pausedAt;

    @Column(name = "completed_at")
    Instant completedAt;

    @Column(name = "archived_at")
    Instant archivedAt;

    @Column(name = "deleted_at")
    Instant deletedAt;

    @Column(name = "deleted_batch_id", length = 36)
    String deletedBatchId;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    protected GoalEntity() {
    }

    static GoalEntity create(String userId) {
        var now = Instant.now();
        var goal = new GoalEntity();
        goal.id = UUID.randomUUID().toString();
        goal.userId = userId;
        goal.status = GoalStatus.DRAFT;
        goal.currentProgress = BigDecimal.ZERO;
        goal.createdAt = now;
        goal.updatedAt = now;
        return goal;
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
