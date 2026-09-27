package com.goalflow.goal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import tools.jackson.databind.JsonNode;

public final class GoalApiModels {

    private GoalApiModels() {
    }

    public record CriterionInput(
            String id,
            @NotNull CriterionType type,
            @NotBlank @Size(max = 500) String description,
            @DecimalMin("0") BigDecimal targetValue,
            @Size(max = 32) String unit,
            boolean required) {
    }

    public record MilestoneInput(
            String id,
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 2000) String acceptance,
            @NotNull LocalDate targetDate,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal weight,
            Integer sortOrder) {
    }

    public record CreateGoalRequest(
            @NotBlank @Size(max = 80) String title,
            @Size(max = 5000) String description,
            LocalDate startDate,
            LocalDate targetDate,
            @NotNull GoalPriority priority,
            @NotNull ProgressMode progressMode,
            String parentId,
            @Size(max = 80) String metricName,
            @Size(max = 32) String metricUnit,
            BigDecimal metricStartValue,
            BigDecimal metricTargetValue,
            @NotEmpty List<@Valid CriterionInput> completionCriteria,
            List<@Valid MilestoneInput> milestones,
            boolean startImmediately) {
    }

    public record UpdateGoalRequest(
            @NotNull Integer version,
            @Size(max = 80) String title,
            @Size(max = 5000) String description,
            LocalDate startDate,
            LocalDate targetDate,
            GoalPriority priority,
            ProgressMode progressMode,
            String parentId,
            boolean clearParent,
            @Size(max = 80) String metricName,
            @Size(max = 32) String metricUnit,
            BigDecimal metricStartValue,
            BigDecimal metricTargetValue,
            List<@Valid CriterionInput> completionCriteria,
            @Size(max = 1000) String changeReason,
            Instant effectiveAt) {
    }

    public record GoalActionRequest(
            @NotNull Integer version,
            @Size(max = 1000) String reason,
            Instant effectiveAt) {
    }

    public record MoveGoalRequest(@NotNull Integer version, @NotNull GoalMoveDirection direction) {
    }

    public record CriterionSatisfactionRequest(
            @NotNull Integer version,
            boolean satisfied,
            @Size(max = 1000) String reason) {
    }

    public record CreateMilestoneRequest(
            @NotNull Integer goalVersion,
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 2000) String acceptance,
            @NotNull LocalDate targetDate,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal weight,
            Integer sortOrder,
            @Size(max = 1000) String changeReason) {
    }

    public record UpdateMilestoneRequest(
            @NotNull Integer version,
            @Size(max = 120) String title,
            @Size(max = 2000) String acceptance,
            LocalDate targetDate,
            @DecimalMin("0") @DecimalMax("100") BigDecimal weight,
            Integer sortOrder,
            @Size(max = 1000) String changeReason,
            Instant effectiveAt) {
    }

    public record MilestoneActionRequest(
            @NotNull Integer version,
            @Size(max = 1000) String reason,
            Instant effectiveAt) {
    }

    public record CreateDailyTaskRequest(
            @NotNull Integer goalVersion,
            @NotBlank @Size(max = 120) String title,
            Integer sortOrder) {
    }

    public record UpdateDailyTaskRequest(
            @NotNull Integer version,
            @NotBlank @Size(max = 120) String title,
            Integer sortOrder) {
    }

    public record DailyTaskCheckinRequest(
            @NotNull Integer version,
            boolean completed) {
    }

    public record CriterionView(
            String id,
            CriterionType type,
            String description,
            BigDecimal targetValue,
            String unit,
            boolean required,
            boolean satisfied,
            Instant satisfiedAt) {
    }

    public record MilestoneView(
            String id,
            String title,
            String acceptance,
            LocalDate targetDate,
            BigDecimal weight,
            BigDecimal progress,
            MilestoneStatus status,
            Instant completedAt,
            int sortOrder,
            int version) {
    }

    public record DailyTaskView(
            String id,
            String title,
            boolean completedToday,
            int sortOrder,
            int version) {
    }

    public record GoalListItem(
            String id,
            String parentId,
            String title,
            LocalDate startDate,
            LocalDate targetDate,
            GoalPriority priority,
            GoalStatus status,
            ProgressMode progressMode,
            BigDecimal currentProgress,
            int sortOrder,
            int version,
            long criterionCount,
            long satisfiedCriterionCount,
            long milestoneCount,
            String nextMilestoneTitle,
            LocalDate nextMilestoneDate,
            Instant updatedAt) {
    }

    public record TrashGoalItem(
            String id,
            String parentId,
            String title,
            Instant deletedAt,
            int descendantCount,
            int version,
            boolean canRestore) {
    }

    public record GoalDetail(
            String id,
            String parentId,
            String parentTitle,
            String title,
            String description,
            LocalDate startDate,
            LocalDate targetDate,
            GoalPriority priority,
            GoalStatus status,
            ProgressMode progressMode,
            BigDecimal currentProgress,
            String metricName,
            String metricUnit,
            BigDecimal metricStartValue,
            BigDecimal metricTargetValue,
            BigDecimal metricCurrentValue,
            int version,
            Instant pausedAt,
            Instant completedAt,
            Instant archivedAt,
            Instant createdAt,
            Instant updatedAt,
            List<CriterionView> completionCriteria,
            List<MilestoneView> milestones,
            List<DailyTaskView> dailyTasks,
            List<GoalListItem> childGoals) {
    }

    public record RevisionView(
            String id,
            String entityType,
            String entityId,
            int revisionNo,
            String action,
            String reason,
            Instant effectiveAt,
            JsonNode before,
            JsonNode after,
            Instant createdAt) {
    }
}
