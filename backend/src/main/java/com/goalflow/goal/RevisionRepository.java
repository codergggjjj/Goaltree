package com.goalflow.goal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RevisionRepository extends JpaRepository<RevisionEntity, String> {

    List<RevisionEntity> findByUserIdAndEntityTypeAndEntityIdOrderByRevisionNoDesc(
            String userId, String entityType, String entityId);

    Optional<RevisionEntity> findTopByEntityTypeAndEntityIdOrderByRevisionNoDesc(
            String entityType, String entityId);
}
