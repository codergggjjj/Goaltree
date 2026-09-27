package com.goalflow.goal;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyTaskCheckinRepository
        extends JpaRepository<DailyTaskCheckinEntity, String> {

    Optional<DailyTaskCheckinEntity> findByDailyTaskIdAndCheckDate(
            String dailyTaskId, LocalDate checkDate);
}
