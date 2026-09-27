package com.goalflow.goal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "revisions")
public class RevisionEntity {

    @Id
    String id;

    @Column(name = "user_id", nullable = false, length = 36)
    String userId;

    @Column(name = "adjustment_id", length = 36)
    String adjustmentId;

    @Column(name = "entity_type", nullable = false, length = 64)
    String entityType;

    @Column(name = "entity_id", nullable = false, length = 36)
    String entityId;

    @Column(name = "revision_no", nullable = false)
    int revisionNo;

    @Column(name = "action_code", nullable = false, length = 24)
    String actionCode;

    @Column(columnDefinition = "TEXT")
    String reason;

    @Column(name = "effective_at", nullable = false)
    Instant effectiveAt;

    @Column(name = "before_json", columnDefinition = "JSON")
    String beforeJson;

    @Column(name = "after_json", nullable = false, columnDefinition = "JSON")
    String afterJson;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    protected RevisionEntity() {
    }

    static RevisionEntity create() {
        var revision = new RevisionEntity();
        revision.id = UUID.randomUUID().toString();
        revision.createdAt = Instant.now();
        return revision;
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
