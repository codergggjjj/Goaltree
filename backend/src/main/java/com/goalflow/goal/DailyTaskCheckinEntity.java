package com.goalflow.goal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "daily_task_checkins")
public class DailyTaskCheckinEntity {

    @Id
    String id;

    @Column(name = "user_id", nullable = false, length = 36)
    String userId;

    @Column(name = "daily_task_id", nullable = false, length = 36)
    String dailyTaskId;

    @Column(name = "check_date", nullable = false)
    LocalDate checkDate;

    @Column(nullable = false)
    boolean completed;

    @Column(name = "completed_at")
    Instant completedAt;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    protected DailyTaskCheckinEntity() {
    }

    static DailyTaskCheckinEntity create(String userId, String dailyTaskId, LocalDate checkDate) {
        var now = Instant.now();
        var checkin = new DailyTaskCheckinEntity();
        checkin.id = UUID.randomUUID().toString();
        checkin.userId = userId;
        checkin.dailyTaskId = dailyTaskId;
        checkin.checkDate = checkDate;
        checkin.createdAt = now;
        checkin.updatedAt = now;
        return checkin;
    }

    @PrePersist
    void prePersist() {
        var now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    void setCompleted(boolean value) {
        completed = value;
        completedAt = value ? Instant.now() : null;
        updatedAt = Instant.now();
    }
}
