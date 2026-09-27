package com.goalflow.goal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalLinkRepository extends JpaRepository<GoalLinkEntity, String> {
    List<GoalLinkEntity> findByGoalIdOrderByCreatedAtAsc(String goalId);
    Optional<GoalLinkEntity> findByIdAndGoalId(String id, String goalId);
}
