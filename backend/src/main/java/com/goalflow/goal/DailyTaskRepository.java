package com.goalflow.goal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyTaskRepository extends JpaRepository<DailyTaskEntity, String> {

    List<DailyTaskEntity> findByGoalIdAndActiveTrueOrderBySortOrderAscCreatedAtAsc(String goalId);

    Optional<DailyTaskEntity> findByIdAndUserId(String id, String userId);
}
