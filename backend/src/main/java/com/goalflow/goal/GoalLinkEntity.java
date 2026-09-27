package com.goalflow.goal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "goal_links")
public class GoalLinkEntity {
    @Id
    String id;

    @Column(name = "goal_id", nullable = false, length = 36)
    String goalId;

    @Column(nullable = false, length = 120)
    String name;

    @Column(nullable = false, length = 2048)
    String url;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    protected GoalLinkEntity() {
    }

    static GoalLinkEntity create(String goalId) {
        var link = new GoalLinkEntity();
        link.id = UUID.randomUUID().toString();
        link.goalId = goalId;
        link.createdAt = Instant.now();
        link.updatedAt = link.createdAt;
        return link;
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = createdAt;
    }

    void touch() {
        updatedAt = Instant.now();
    }
}
