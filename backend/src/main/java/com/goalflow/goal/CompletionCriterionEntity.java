package com.goalflow.goal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "completion_criteria")
public class CompletionCriterionEntity {

    @Id
    String id;

    @Column(name = "user_id", nullable = false, length = 36)
    String userId;

    @Column(name = "goal_id", nullable = false, length = 36)
    String goalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "criterion_type", nullable = false, length = 20)
    CriterionType type;

    @Column(nullable = false, length = 500)
    String description;

    @Column(name = "target_value", precision = 18, scale = 4)
    BigDecimal targetValue;

    @Column(length = 32)
    String unit;

    @Column(name = "required_flag", nullable = false)
    boolean required;

    @Column(nullable = false)
    boolean satisfied;

    @Column(name = "satisfied_at")
    Instant satisfiedAt;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    protected CompletionCriterionEntity() {
    }

    static CompletionCriterionEntity create(String userId, String goalId) {
        var now = Instant.now();
        var criterion = new CompletionCriterionEntity();
        criterion.id = UUID.randomUUID().toString();
        criterion.userId = userId;
        criterion.goalId = goalId;
        criterion.createdAt = now;
        criterion.updatedAt = now;
        return criterion;
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

    void setSatisfied(boolean value) {
        satisfied = value;
        satisfiedAt = value ? Instant.now() : null;
        updatedAt = Instant.now();
    }
}
