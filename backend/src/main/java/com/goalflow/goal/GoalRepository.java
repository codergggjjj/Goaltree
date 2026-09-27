package com.goalflow.goal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalRepository extends JpaRepository<GoalEntity, String> {

    Optional<GoalEntity> findByIdAndUserIdAndDeletedAtIsNull(String id, String userId);

    Optional<GoalEntity> findByIdAndUserIdAndDeletedAtIsNotNull(String id, String userId);

    List<GoalEntity> findByUserIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(String userId);

    List<GoalEntity> findByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(String userId);

    List<GoalEntity> findByParentIdAndUserIdAndDeletedAtIsNullOrderByCreatedAtAsc(
            String parentId, String userId);
}
