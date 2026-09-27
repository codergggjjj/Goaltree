package com.goalflow.goal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompletionCriterionRepository
        extends JpaRepository<CompletionCriterionEntity, String> {

    List<CompletionCriterionEntity> findByGoalIdOrderByCreatedAtAsc(String goalId);

    Optional<CompletionCriterionEntity> findByIdAndGoalId(String id, String goalId);

    long countByGoalId(String goalId);
}
