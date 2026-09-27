package com.goalflow.goal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MilestoneRepository extends JpaRepository<MilestoneEntity, String> {

    List<MilestoneEntity> findByGoalIdOrderBySortOrderAscCreatedAtAsc(String goalId);

    Optional<MilestoneEntity> findByIdAndUserId(String id, String userId);

    long countByGoalId(String goalId);
}
