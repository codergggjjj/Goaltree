package com.goalflow.goal;

import static com.goalflow.goal.GoalApiModels.CreateGoalRequest;
import static com.goalflow.goal.GoalApiModels.CreateDailyTaskRequest;
import static com.goalflow.goal.GoalApiModels.CreateMilestoneRequest;
import static com.goalflow.goal.GoalApiModels.CriterionInput;
import static com.goalflow.goal.GoalApiModels.CriterionSatisfactionRequest;
import static com.goalflow.goal.GoalApiModels.CriterionView;
import static com.goalflow.goal.GoalApiModels.DailyTaskCheckinRequest;
import static com.goalflow.goal.GoalApiModels.DailyTaskView;
import static com.goalflow.goal.GoalApiModels.GoalActionRequest;
import static com.goalflow.goal.GoalApiModels.GoalDetail;
import static com.goalflow.goal.GoalApiModels.GoalListItem;
import static com.goalflow.goal.GoalApiModels.TrashGoalItem;
import static com.goalflow.goal.GoalApiModels.MilestoneActionRequest;
import static com.goalflow.goal.GoalApiModels.MilestoneInput;
import static com.goalflow.goal.GoalApiModels.MilestoneView;
import static com.goalflow.goal.GoalApiModels.MoveGoalRequest;
import static com.goalflow.goal.GoalApiModels.RevisionView;
import static com.goalflow.goal.GoalApiModels.UpdateGoalRequest;
import static com.goalflow.goal.GoalApiModels.UpdateDailyTaskRequest;
import static com.goalflow.goal.GoalApiModels.UpdateMilestoneRequest;

import com.goalflow.api.ApiException;
import java.math.BigDecimal;
import java.text.Collator;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class GoalService {

    static final String DEFAULT_USER_ID = "demo-user";
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Comparator<GoalEntity> SIBLING_ORDER = Comparator
            .comparingInt((GoalEntity goal) -> goal.sortOrder)
            .thenComparing((left, right) -> Collator.getInstance(Locale.CHINA).compare(left.title, right.title))
            .thenComparing(goal -> goal.id);

    private final GoalRepository goalRepository;
    private final CompletionCriterionRepository criterionRepository;
    private final MilestoneRepository milestoneRepository;
    private final DailyTaskRepository dailyTaskRepository;
    private final DailyTaskCheckinRepository dailyTaskCheckinRepository;
    private final RevisionRepository revisionRepository;
    private final ObjectMapper objectMapper;

    public GoalService(
            GoalRepository goalRepository,
            CompletionCriterionRepository criterionRepository,
            MilestoneRepository milestoneRepository,
            DailyTaskRepository dailyTaskRepository,
            DailyTaskCheckinRepository dailyTaskCheckinRepository,
            RevisionRepository revisionRepository,
            ObjectMapper objectMapper) {
        this.goalRepository = goalRepository;
        this.criterionRepository = criterionRepository;
        this.milestoneRepository = milestoneRepository;
        this.dailyTaskRepository = dailyTaskRepository;
        this.dailyTaskCheckinRepository = dailyTaskCheckinRepository;
        this.revisionRepository = revisionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<GoalListItem> listGoals(boolean includeArchived) {
        return goalRepository
                .findByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(DEFAULT_USER_ID)
                .stream()
                .filter(goal -> includeArchived || goal.status != GoalStatus.ARCHIVED)
                .map(this::toListItem)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TrashGoalItem> listTrash() {
        var deleted = goalRepository.findByUserIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(DEFAULT_USER_ID);
        var byId = new HashMap<String, GoalEntity>();
        var children = new HashMap<String, List<GoalEntity>>();
        for (var goal : deleted) {
            byId.put(goal.id, goal);
            children.computeIfAbsent(goal.parentId, ignored -> new ArrayList<>()).add(goal);
        }
        return deleted.stream()
                .filter(goal -> !sameDeletion(goal, byId.get(goal.parentId)))
                .map(goal -> new TrashGoalItem(goal.id, goal.parentId, goal.title, goal.deletedAt,
                        deletedBatchSubtree(goal, children).size() - 1, goal.version,
                        goal.parentId == null || goalRepository
                                .findByIdAndUserIdAndDeletedAtIsNull(goal.parentId, DEFAULT_USER_ID).isPresent()))
                .toList();
    }

    @Transactional(readOnly = true)
    public GoalDetail getGoal(String goalId) {
        return toDetail(requireGoal(goalId));
    }

    @Transactional
    public GoalDetail createGoal(CreateGoalRequest request) {
        if (blankToNull(request.parentId()) == null) {
            throw rule("PARENT_REQUIRED", "添加子目标前，请先选择父目标。");
        }
        return createGoalRecord(request);
    }

    @Transactional
    public GoalDetail createRootGoal(CreateGoalRequest request) {
        if (request.parentId() != null) {
            throw rule("ROOT_PARENT_NOT_ALLOWED", "新建主目标不能指定父目标。");
        }
        return createGoalRecord(request);
    }

    private GoalDetail createGoalRecord(CreateGoalRequest request) {
        var startDate = request.startDate() == null ? LocalDate.now(APP_ZONE) : request.startDate();
        var targetDate = request.targetDate() == null ? startDate.plusYears(20) : request.targetDate();
        validateGoalDates(startDate, targetDate);
        validateHierarchy(request.parentId(), null);
        validateMetricDefinition(
                request.progressMode(),
                request.metricName(),
                request.metricStartValue(),
                request.metricTargetValue());
        validateCriterionInputs(request.completionCriteria());

        var goal = GoalEntity.create(DEFAULT_USER_ID);
        goal.parentId = blankToNull(request.parentId());
        goal.sortOrder = nextSortOrder(goal.parentId, null);
        goal.title = requiredText(request.title(), "目标名称不能为空。");
        goal.description = blankToNull(request.description());
        goal.startDate = startDate;
        goal.targetDate = targetDate;
        goal.priority = request.priority();
        goal.progressMode = request.progressMode();
        goal.metricName = blankToNull(request.metricName());
        goal.metricUnit = blankToNull(request.metricUnit());
        goal.metricStartValue = request.metricStartValue();
        goal.metricTargetValue = request.metricTargetValue();
        goal.metricCurrentValue = request.metricStartValue();
        goal.status = request.startImmediately() ? GoalStatus.ACTIVE : GoalStatus.DRAFT;

        var milestoneInputs = request.milestones() == null ? List.<MilestoneInput>of() : request.milestones();
        validateMilestoneInputs(goal, milestoneInputs);
        if (request.startImmediately()) {
            validateStartInputRules(
                    request.completionCriteria(), milestoneInputs, request.progressMode());
        }

        goalRepository.save(goal);
        saveNewCriteria(goal, request.completionCriteria());
        saveNewMilestones(goal, milestoneInputs);
        goalRepository.flush();

        var detail = toDetail(goal);
        recordRevision(
                "GOAL",
                goal.id,
                "CREATE",
                request.startImmediately() ? "创建并启动目标" : "创建目标草稿",
                Instant.now(),
                null,
                snapshot(detail));
        syncAncestors(goal.parentId);
        return detail;
    }

    @Transactional
    public GoalDetail updateGoal(String goalId, UpdateGoalRequest request) {
        var goal = requireGoal(goalId);
        rejectArchived(goal);
        assertVersion(goal.version, request.version());

        var before = snapshot(toDetail(goal));
        var previousParentId = goal.parentId;
        var originalRootId = rootIdOf(goal);
        var structuralChange = isStructuralGoalChange(goal, request);
        if (goal.status != GoalStatus.DRAFT && structuralChange) {
            requireReason(request.changeReason(), "已启动目标的结构性修改必须填写原因。");
        }

        if (request.title() != null) {
            goal.title = requiredText(request.title(), "目标名称不能为空。");
        }
        if (request.description() != null) {
            goal.description = blankToNull(request.description());
        }
        if (request.startDate() != null) {
            goal.startDate = request.startDate();
        }
        if (request.targetDate() != null) {
            goal.targetDate = request.targetDate();
        }
        if (request.priority() != null) {
            goal.priority = request.priority();
        }
        if (request.progressMode() != null) {
            goal.progressMode = request.progressMode();
        }
        if (request.clearParent()) {
            goal.parentId = null;
        } else if (request.parentId() != null) {
            goal.parentId = blankToNull(request.parentId());
        }
        if (request.metricName() != null) {
            goal.metricName = blankToNull(request.metricName());
        }
        if (request.metricUnit() != null) {
            goal.metricUnit = blankToNull(request.metricUnit());
        }
        if (request.metricStartValue() != null) {
            goal.metricStartValue = request.metricStartValue();
        }
        if (request.metricTargetValue() != null) {
            goal.metricTargetValue = request.metricTargetValue();
        }

        validateGoalDates(goal.startDate, goal.targetDate);
        validateHierarchy(goal.parentId, goal.id);
        if (previousParentId == null && goal.parentId != null) {
            throw rule("ROOT_REPARENT_FORBIDDEN", "主目标不能变成子目标。");
        }
        if (previousParentId != null && goal.parentId == null) {
            throw rule("CHILD_PARENT_REQUIRED", "子目标不能移出所属主目标树。");
        }
        if (goal.parentId != null && !Objects.equals(originalRootId, rootIdOf(requireGoal(goal.parentId)))) {
            throw rule("CROSS_TREE_MOVE_FORBIDDEN", "子目标只能在所属主目标树内调整层级。");
        }
        if (!Objects.equals(previousParentId, goal.parentId)) {
            goal.sortOrder = nextSortOrder(goal.parentId, goal.id);
        }
        validateMetricDefinition(
                goal.progressMode, goal.metricName, goal.metricStartValue, goal.metricTargetValue);

        if (request.completionCriteria() != null) {
            validateCriterionInputs(request.completionCriteria());
            syncCriteria(goal, request.completionCriteria());
        }
        if (goal.status == GoalStatus.ACTIVE
                && goal.progressMode == ProgressMode.MILESTONE_WEIGHTED) {
            validateActiveMilestoneWeights(goal);
        }

        goal.touch();
        goalRepository.saveAndFlush(goal);
        var detail = toDetail(goal);
        recordRevision(
                "GOAL",
                goal.id,
                "UPDATE",
                blankToNull(request.changeReason()),
                request.effectiveAt() == null ? Instant.now() : request.effectiveAt(),
                before,
                snapshot(detail));
        syncAncestors(previousParentId);
        if (!Objects.equals(previousParentId, goal.parentId)) {
            syncAncestors(goal.parentId);
        }
        return detail;
    }

    @Transactional
    public void moveGoal(String goalId, MoveGoalRequest request) {
        var goal = requireGoal(goalId);
        assertVersion(goal.version, request.version());
        var siblings = goalRepository.findByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(DEFAULT_USER_ID)
                .stream()
                .filter(item -> Objects.equals(item.parentId, goal.parentId))
                .sorted(SIBLING_ORDER)
                .toList();
        var currentIndex = -1;
        for (int i = 0; i < siblings.size(); i++) {
            if (siblings.get(i).id.equals(goalId)) currentIndex = i;
        }
        int targetIndex = currentIndex + (request.direction() == GoalMoveDirection.UP ? -1 : 1);
        if (currentIndex < 0 || targetIndex < 0 || targetIndex >= siblings.size()) {
            throw rule("MOVE_NOT_AVAILABLE", "这个目标已经在同级目标的边缘，无法继续移动。");
        }
        var ordered = new ArrayList<>(siblings);
        java.util.Collections.swap(ordered, currentIndex, targetIndex);
        for (int i = 0; i < ordered.size(); i++) {
            var sibling = ordered.get(i);
            if (sibling.sortOrder != i) {
                sibling.sortOrder = i;
                sibling.touch();
            }
        }
        goalRepository.saveAllAndFlush(ordered);
    }

    private int nextSortOrder(String parentId, String excludedId) {
        return goalRepository.findByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(DEFAULT_USER_ID)
                .stream()
                .filter(item -> Objects.equals(item.parentId, parentId) && !Objects.equals(item.id, excludedId))
                .mapToInt(item -> item.sortOrder)
                .max().orElse(-1) + 1;
    }

    @Transactional
    public GoalDetail startGoal(String goalId, GoalActionRequest request) {
        var goal = requireGoal(goalId);
        assertVersion(goal.version, request.version());
        if (goal.status != GoalStatus.DRAFT) {
            throw rule("INVALID_STATUS_TRANSITION", "只有草稿目标可以启动。");
        }
        validatePersistedStartRules(
                criterionRepository.findByGoalIdOrderByCreatedAtAsc(goal.id),
                milestoneRepository.findByGoalIdOrderBySortOrderAscCreatedAtAsc(goal.id),
                goal.progressMode);
        return changeGoalStatus(goal, GoalStatus.ACTIVE, request, "启动目标");
    }

    @Transactional
    public GoalDetail pauseGoal(String goalId, GoalActionRequest request) {
        var goal = requireGoal(goalId);
        assertVersion(goal.version, request.version());
        if (goal.status != GoalStatus.ACTIVE) {
            throw rule("INVALID_STATUS_TRANSITION", "只有进行中的目标可以暂停。");
        }
        goal.pausedAt = Instant.now();
        return changeGoalStatus(goal, GoalStatus.PAUSED, request, "暂停目标");
    }

    @Transactional
    public GoalDetail resumeGoal(String goalId, GoalActionRequest request) {
        var goal = requireGoal(goalId);
        assertVersion(goal.version, request.version());
        if (!Set.of(GoalStatus.PAUSED, GoalStatus.COMPLETED, GoalStatus.ARCHIVED)
                .contains(goal.status)) {
            throw rule("INVALID_STATUS_TRANSITION", "当前状态不能恢复为进行中。");
        }
        validatePersistedStartRules(
                criterionRepository.findByGoalIdOrderByCreatedAtAsc(goal.id),
                milestoneRepository.findByGoalIdOrderBySortOrderAscCreatedAtAsc(goal.id),
                goal.progressMode);
        goal.pausedAt = null;
        goal.completedAt = null;
        goal.archivedAt = null;
        goal.currentProgress = BigDecimal.ZERO;
        var detail = changeGoalStatus(goal, GoalStatus.ACTIVE, request, "恢复目标");
        syncAncestors(goal.parentId);
        return detail;
    }

    @Transactional
    public GoalDetail completeGoal(String goalId, GoalActionRequest request) {
        var goal = requireGoal(goalId);
        assertVersion(goal.version, request.version());
        if (goal.status != GoalStatus.ACTIVE) {
            throw rule("INVALID_STATUS_TRANSITION", "只有进行中的目标可以确认完成。");
        }
        var criteria = criterionRepository.findByGoalIdOrderByCreatedAtAsc(goal.id);
        if (criteria.stream().filter(criterion -> criterion.required).anyMatch(criterion -> !criterion.satisfied)) {
            throw rule("REQUIRED_CRITERIA_UNSATISFIED", "仍有必需完成标准未满足，暂时不能完成目标。");
        }
        return finishTree(goal, request);
    }

    @Transactional
    public GoalDetail completeTreeGoal(String goalId, GoalActionRequest request) {
        var goal = requireGoal(goalId);
        assertVersion(goal.version, request.version());
        if (goal.status == GoalStatus.ARCHIVED || goal.status == GoalStatus.COMPLETED) {
            throw rule("INVALID_STATUS_TRANSITION", "当前目标不能标记完成。");
        }
        return finishTree(goal, request);
    }

    private GoalDetail finishTree(GoalEntity goal, GoalActionRequest request) {
        goal.completedAt = Instant.now();
        goal.currentProgress = ONE_HUNDRED;
        completeDescendants(goal.id);
        var detail = changeGoalStatus(goal, GoalStatus.COMPLETED, request, "完成目标");
        syncAncestors(goal.parentId);
        return detail;
    }

    @Transactional
    public GoalDetail archiveGoal(String goalId, GoalActionRequest request) {
        var goal = requireGoal(goalId);
        assertVersion(goal.version, request.version());
        if (goal.status == GoalStatus.ARCHIVED) {
            throw rule("INVALID_STATUS_TRANSITION", "目标已经归档。");
        }
        goal.archivedAt = Instant.now();
        var detail = changeGoalStatus(goal, GoalStatus.ARCHIVED, request, "归档目标");
        syncAncestors(goal.parentId);
        return detail;
    }

    @Transactional
    public void deleteGoal(String goalId, int version) {
        var goal = requireGoal(goalId);
        assertVersion(goal.version, version);
        if (!goalRepository.findByParentIdAndUserIdAndDeletedAtIsNullOrderByCreatedAtAsc(goal.id, DEFAULT_USER_ID).isEmpty()) {
            throw rule("GOAL_HAS_CHILDREN", "请先移走或删除子目标，再删除这个目标。");
        }
        var before = snapshot(toDetail(goal));
        goal.deletedAt = Instant.now();
        goal.deletedBatchId = UUID.randomUUID().toString();
        goal.touch();
        goalRepository.saveAndFlush(goal);
        recordRevision("GOAL", goal.id, "UPDATE", "删除目标", Instant.now(), before, snapshot(toDetail(goal)));
        syncAncestors(goal.parentId);
    }

    @Transactional
    public void deleteGoalTree(String goalId, int version) {
        var root = requireGoal(goalId);
        assertVersion(root.version, version);
        var subtree = new ArrayList<GoalEntity>();
        collectGoalSubtree(root, subtree, new LinkedHashSet<>());
        var before = new HashMap<String, String>();
        for (var goal : subtree) {
            before.put(goal.id, snapshot(toDetail(goal)));
        }
        var deletedAt = Instant.now();
        var batchId = UUID.randomUUID().toString();
        for (var goal : subtree) {
            goal.deletedAt = deletedAt;
            goal.deletedBatchId = batchId;
            goal.touch();
            goalRepository.saveAndFlush(goal);
            recordRevision("GOAL", goal.id, "UPDATE", "删除目标树", deletedAt,
                    before.get(goal.id), snapshot(toDetail(goal)));
        }
        syncAncestors(root.parentId);
    }

    @Transactional
    public void restoreGoal(String goalId, int version) {
        var root = goalRepository.findByIdAndUserIdAndDeletedAtIsNotNull(goalId, DEFAULT_USER_ID)
                .orElseThrow(() -> notFound("回收站中没有这个目标。"));
        assertVersion(root.version, version);
        if (root.parentId != null && goalRepository
                .findByIdAndUserIdAndDeletedAtIsNull(root.parentId, DEFAULT_USER_ID).isEmpty()) {
            throw rule("RESTORE_PARENT_REQUIRED", "请先恢复该目标的父目标，再恢复此目标。");
        }
        var children = new HashMap<String, List<GoalEntity>>();
        for (var goal : goalRepository.findByUserIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(DEFAULT_USER_ID)) {
            children.computeIfAbsent(goal.parentId, ignored -> new ArrayList<>()).add(goal);
        }
        var subtree = deletedBatchSubtree(root, children);
        var restoredAt = Instant.now();
        for (var goal : subtree) {
            var before = snapshot(toDetail(goal));
            goal.deletedAt = null;
            goal.deletedBatchId = null;
            goal.touch();
            goalRepository.saveAndFlush(goal);
            recordRevision("GOAL", goal.id, "UPDATE", "从回收站恢复目标", restoredAt,
                    before, snapshot(toDetail(goal)));
        }
        syncAncestors(root.parentId);
    }

    private boolean sameDeletion(GoalEntity child, GoalEntity parent) {
        if (parent == null) return false;
        if (child.deletedBatchId != null || parent.deletedBatchId != null) {
            return Objects.equals(child.deletedBatchId, parent.deletedBatchId);
        }
        return Objects.equals(child.deletedAt, parent.deletedAt);
    }

    private List<GoalEntity> deletedBatchSubtree(GoalEntity root, Map<String, List<GoalEntity>> children) {
        var result = new ArrayList<GoalEntity>();
        collectDeletedBatch(root, children, result, new LinkedHashSet<>());
        return result;
    }

    private void collectDeletedBatch(GoalEntity goal, Map<String, List<GoalEntity>> children,
            List<GoalEntity> result, Set<String> path) {
        if (!path.add(goal.id)) throw rule("GOAL_CYCLE", "检测到循环目标关系。");
        result.add(goal);
        for (var child : children.getOrDefault(goal.id, List.of())) {
            if (sameDeletion(child, goal)) {
                collectDeletedBatch(child, children, result, new LinkedHashSet<>(path));
            }
        }
    }

    private void collectGoalSubtree(GoalEntity goal, List<GoalEntity> subtree, Set<String> path) {
        if (!path.add(goal.id)) {
            throw rule("GOAL_CYCLE", "检测到循环目标关系。");
        }
        for (var child : goalRepository.findByParentIdAndUserIdAndDeletedAtIsNullOrderByCreatedAtAsc(
                goal.id, DEFAULT_USER_ID)) {
            collectGoalSubtree(child, subtree, new LinkedHashSet<>(path));
        }
        subtree.add(goal);
    }

    @Transactional
    public GoalDetail setCriterionSatisfaction(
            String goalId, String criterionId, CriterionSatisfactionRequest request) {
        var goal = requireGoal(goalId);
        rejectArchived(goal);
        assertVersion(goal.version, request.version());
        var before = snapshot(toDetail(goal));
        var criterion = criterionRepository
                .findByIdAndGoalId(criterionId, goal.id)
                .orElseThrow(() -> notFound("完成标准不存在。"));
        criterion.setSatisfied(request.satisfied());
        criterionRepository.save(criterion);
        goal.touch();
        goalRepository.saveAndFlush(goal);
        var detail = toDetail(goal);
        recordRevision(
                "GOAL",
                goal.id,
                "UPDATE",
                blankToNull(request.reason()) == null
                        ? (request.satisfied() ? "确认完成标准" : "撤销完成标准")
                        : request.reason(),
                Instant.now(),
                before,
                snapshot(detail));
        return detail;
    }

    @Transactional
    public GoalDetail createMilestone(String goalId, CreateMilestoneRequest request) {
        var goal = requireGoal(goalId);
        rejectArchived(goal);
        assertVersion(goal.version, request.goalVersion());
        if (goal.status != GoalStatus.DRAFT) {
            requireReason(request.changeReason(), "已启动目标新增里程碑必须填写原因。");
        }

        var milestone = MilestoneEntity.create(DEFAULT_USER_ID, goal.id);
        applyMilestoneInput(
                milestone,
                request.title(),
                request.acceptance(),
                request.targetDate(),
                request.weight(),
                request.sortOrder());
        validateMilestoneDate(goal, milestone.targetDate);

        var proposed = new ArrayList<>(
                milestoneRepository.findByGoalIdOrderBySortOrderAscCreatedAtAsc(goal.id));
        proposed.add(milestone);
        if (goal.status == GoalStatus.ACTIVE && goal.progressMode == ProgressMode.MILESTONE_WEIGHTED) {
            validateMilestoneWeightSum(proposed);
        }

        milestoneRepository.save(milestone);
        goal.touch();
        goalRepository.saveAndFlush(goal);
        recordRevision(
                "MILESTONE",
                milestone.id,
                "CREATE",
                blankToNull(request.changeReason()) == null ? "新增里程碑" : request.changeReason(),
                Instant.now(),
                null,
                snapshot(toMilestoneView(milestone)));
        return toDetail(goal);
    }

    @Transactional
    public GoalDetail updateMilestone(String milestoneId, UpdateMilestoneRequest request) {
        var milestone = requireMilestone(milestoneId);
        var goal = requireGoal(milestone.goalId);
        rejectArchived(goal);
        assertVersion(milestone.version, request.version());
        var before = snapshot(toMilestoneView(milestone));
        var structural = (request.targetDate() != null
                        && !request.targetDate().equals(milestone.targetDate))
                || (request.weight() != null && request.weight().compareTo(milestone.weight) != 0);
        if (goal.status != GoalStatus.DRAFT && structural) {
            requireReason(request.changeReason(), "修改已启动目标的里程碑日期或权重必须填写原因。");
        }

        applyMilestoneInput(
                milestone,
                request.title() == null ? milestone.title : request.title(),
                request.acceptance() == null ? milestone.acceptance : request.acceptance(),
                request.targetDate() == null ? milestone.targetDate : request.targetDate(),
                request.weight() == null ? milestone.weight : request.weight(),
                request.sortOrder() == null ? milestone.sortOrder : request.sortOrder());
        validateMilestoneDate(goal, milestone.targetDate);
        if (goal.status == GoalStatus.ACTIVE && goal.progressMode == ProgressMode.MILESTONE_WEIGHTED) {
            validateActiveMilestoneWeights(goal);
        }
        milestone.touch();
        milestoneRepository.saveAndFlush(milestone);
        recordRevision(
                "MILESTONE",
                milestone.id,
                "UPDATE",
                blankToNull(request.changeReason()),
                request.effectiveAt() == null ? Instant.now() : request.effectiveAt(),
                before,
                snapshot(toMilestoneView(milestone)));
        return toDetail(goal);
    }

    @Transactional
    public GoalDetail achieveMilestone(String milestoneId, MilestoneActionRequest request) {
        var milestone = requireMilestone(milestoneId);
        var goal = requireGoal(milestone.goalId);
        rejectArchived(goal);
        assertVersion(milestone.version, request.version());
        if (milestone.status == MilestoneStatus.CANCELLED
                || milestone.status == MilestoneStatus.ACHIEVED) {
            throw rule("INVALID_MILESTONE_STATUS", "当前里程碑不能标记为已达成。");
        }
        var before = snapshot(toMilestoneView(milestone));
        milestone.status = MilestoneStatus.ACHIEVED;
        milestone.progress = ONE_HUNDRED;
        milestone.completedAt = Instant.now();
        milestone.touch();
        milestoneRepository.saveAndFlush(milestone);
        recordRevision(
                "MILESTONE",
                milestone.id,
                "STATUS_CHANGE",
                blankToNull(request.reason()) == null ? "达成里程碑" : request.reason(),
                request.effectiveAt() == null ? Instant.now() : request.effectiveAt(),
                before,
                snapshot(toMilestoneView(milestone)));
        return toDetail(goal);
    }

    @Transactional
    public GoalDetail cancelMilestone(String milestoneId, MilestoneActionRequest request) {
        var milestone = requireMilestone(milestoneId);
        var goal = requireGoal(milestone.goalId);
        rejectArchived(goal);
        assertVersion(milestone.version, request.version());
        requireReason(request.reason(), "取消里程碑必须填写原因。");
        if (milestone.status == MilestoneStatus.CANCELLED) {
            throw rule("INVALID_MILESTONE_STATUS", "里程碑已经取消。");
        }
        var before = snapshot(toMilestoneView(milestone));
        milestone.status = MilestoneStatus.CANCELLED;
        milestone.touch();
        if (goal.status == GoalStatus.ACTIVE && goal.progressMode == ProgressMode.MILESTONE_WEIGHTED) {
            validateActiveMilestoneWeights(goal);
        }
        milestoneRepository.saveAndFlush(milestone);
        recordRevision(
                "MILESTONE",
                milestone.id,
                "STATUS_CHANGE",
                request.reason(),
                request.effectiveAt() == null ? Instant.now() : request.effectiveAt(),
                before,
                snapshot(toMilestoneView(milestone)));
        return toDetail(goal);
    }

    @Transactional(readOnly = true)
    public List<RevisionView> getGoalRevisions(String goalId) {
        var goal = requireGoal(goalId);
        var revisions = new ArrayList<>(revisionRepository
                .findByUserIdAndEntityTypeAndEntityIdOrderByRevisionNoDesc(
                        DEFAULT_USER_ID, "GOAL", goal.id));
        milestoneRepository.findByGoalIdOrderBySortOrderAscCreatedAtAsc(goal.id).forEach(milestone ->
                revisions.addAll(revisionRepository
                        .findByUserIdAndEntityTypeAndEntityIdOrderByRevisionNoDesc(
                                DEFAULT_USER_ID, "MILESTONE", milestone.id)));
        return revisions.stream()
                .sorted(Comparator.comparing((RevisionEntity revision) -> revision.createdAt).reversed())
                .map(this::toRevisionView)
                .toList();
    }

    private void completeDescendants(String parentId) {
        for (var child : goalRepository.findByParentIdAndUserIdAndDeletedAtIsNullOrderByCreatedAtAsc(parentId, DEFAULT_USER_ID)) {
            if (child.status == GoalStatus.ARCHIVED) continue;
            completeDescendants(child.id);
            if (child.status != GoalStatus.COMPLETED) {
                child.currentProgress = ONE_HUNDRED;
                child.completedAt = Instant.now();
                changeGoalStatus(child, GoalStatus.COMPLETED, new GoalActionRequest(child.version, "随父目标完成", null), "随父目标完成");
            }
        }
    }

    private void syncAncestors(String parentId) {
        Set<String> seen = new LinkedHashSet<>();
        var cursorId = parentId;
        while (cursorId != null && seen.add(cursorId)) {
            var parent = requireGoal(cursorId);
            syncGroup(parent);
            cursorId = parent.parentId;
        }
    }

    private void syncGroup(GoalEntity parent) {
        var children = goalRepository.findByParentIdAndUserIdAndDeletedAtIsNullOrderByCreatedAtAsc(parent.id, DEFAULT_USER_ID)
                .stream().filter(child -> child.status != GoalStatus.ARCHIVED).toList();
        if (children.isEmpty()) {
            if (parent.status == GoalStatus.ACTIVE && parent.currentProgress.compareTo(BigDecimal.ZERO) != 0) {
                var before = snapshot(toDetail(parent));
                parent.currentProgress = BigDecimal.ZERO;
                parent.touch();
                goalRepository.saveAndFlush(parent);
                recordRevision("GOAL", parent.id, "STATUS_CHANGE", "按子目标进度同步", Instant.now(), before, snapshot(toDetail(parent)));
            }
            return;
        }
        var before = snapshot(toDetail(parent));
        int[] totals = new int[2];
        for (var child : children) countLeaves(child, totals, new LinkedHashSet<>());
        boolean allChildrenCompleted = children.stream().allMatch(child -> child.status == GoalStatus.COMPLETED);
        var progress = BigDecimal.valueOf(Math.min(allChildrenCompleted ? 100 : 99, Math.round(100.0 * totals[1] / totals[0])));
        var nextStatus = parent.status;
        if (parent.status != GoalStatus.ARCHIVED && allChildrenCompleted && totals[0] == totals[1]) {
            nextStatus = GoalStatus.COMPLETED;
        } else if (parent.status == GoalStatus.COMPLETED) {
            nextStatus = GoalStatus.ACTIVE;
        }
        if (nextStatus != GoalStatus.COMPLETED && progress.compareTo(ONE_HUNDRED) == 0) progress = BigDecimal.valueOf(99);
        if (progress.compareTo(parent.currentProgress) == 0 && nextStatus == parent.status) return;
        parent.currentProgress = progress;
        parent.status = nextStatus;
        parent.completedAt = nextStatus == GoalStatus.COMPLETED ? Instant.now() : null;
        parent.touch();
        goalRepository.saveAndFlush(parent);
        recordRevision("GOAL", parent.id, "STATUS_CHANGE", "按子目标进度同步", Instant.now(), before, snapshot(toDetail(parent)));
    }

    private void countLeaves(GoalEntity goal, int[] totals, Set<String> path) {
        if (!path.add(goal.id)) throw rule("GOAL_CYCLE", "检测到循环目标关系。");
        var children = goalRepository.findByParentIdAndUserIdAndDeletedAtIsNullOrderByCreatedAtAsc(goal.id, DEFAULT_USER_ID)
                .stream().filter(child -> child.status != GoalStatus.ARCHIVED).toList();
        if (children.isEmpty()) {
            totals[0]++;
            if (goal.status == GoalStatus.COMPLETED) totals[1]++;
        } else {
            for (var child : children) countLeaves(child, totals, new LinkedHashSet<>(path));
        }
    }

    private GoalDetail changeGoalStatus(
            GoalEntity goal, GoalStatus targetStatus, GoalActionRequest request, String defaultReason) {
        var before = snapshot(toDetail(goal));
        goal.status = targetStatus;
        goal.touch();
        goalRepository.saveAndFlush(goal);
        var detail = toDetail(goal);
        recordRevision(
                "GOAL",
                goal.id,
                "STATUS_CHANGE",
                blankToNull(request.reason()) == null ? defaultReason : request.reason(),
                request.effectiveAt() == null ? Instant.now() : request.effectiveAt(),
                before,
                snapshot(detail));
        return detail;
    }

    @Transactional
    public GoalDetail createDailyTask(String goalId, CreateDailyTaskRequest request) {
        var goal = requireGoal(goalId);
        rejectArchived(goal);
        assertVersion(goal.version, request.goalVersion());

        var existing =
                dailyTaskRepository.findByGoalIdAndActiveTrueOrderBySortOrderAscCreatedAtAsc(goal.id);
        var task = DailyTaskEntity.create(DEFAULT_USER_ID, goal.id);
        task.title = requiredText(request.title(), "日常任务名称不能为空。");
        task.sortOrder = request.sortOrder() == null ? existing.size() + 1 : request.sortOrder();
        dailyTaskRepository.save(task);
        goal.touch();
        goalRepository.saveAndFlush(goal);
        return toDetail(goal);
    }

    @Transactional
    public GoalDetail updateDailyTask(String taskId, UpdateDailyTaskRequest request) {
        var task = requireDailyTask(taskId);
        var goal = requireGoal(task.goalId);
        rejectArchived(goal);
        assertVersion(task.version, request.version());

        task.title = requiredText(request.title(), "日常任务名称不能为空。");
        if (request.sortOrder() != null) {
            task.sortOrder = request.sortOrder();
        }
        task.touch();
        dailyTaskRepository.saveAndFlush(task);
        goal.touch();
        goalRepository.saveAndFlush(goal);
        return toDetail(goal);
    }

    @Transactional
    public GoalDetail checkinDailyTask(String taskId, DailyTaskCheckinRequest request) {
        var task = requireDailyTask(taskId);
        var goal = requireGoal(task.goalId);
        rejectArchived(goal);
        assertVersion(task.version, request.version());

        var checkDate = LocalDate.now(APP_ZONE);
        var checkin = dailyTaskCheckinRepository
                .findByDailyTaskIdAndCheckDate(task.id, checkDate)
                .orElseGet(() ->
                        DailyTaskCheckinEntity.create(DEFAULT_USER_ID, task.id, checkDate));
        checkin.setCompleted(request.completed());
        dailyTaskCheckinRepository.save(checkin);
        task.touch();
        dailyTaskRepository.saveAndFlush(task);
        goal.touch();
        goalRepository.saveAndFlush(goal);
        return toDetail(goal);
    }

    private void saveNewCriteria(GoalEntity goal, List<CriterionInput> inputs) {
        criterionRepository.saveAll(inputs.stream()
                .map(input -> newCriterion(goal, input))
                .toList());
    }

    private CompletionCriterionEntity newCriterion(GoalEntity goal, CriterionInput input) {
        var criterion = CompletionCriterionEntity.create(DEFAULT_USER_ID, goal.id);
        applyCriterionInput(criterion, input);
        return criterion;
    }

    private void syncCriteria(GoalEntity goal, List<CriterionInput> inputs) {
        var existing = criterionRepository.findByGoalIdOrderByCreatedAtAsc(goal.id);
        Map<String, CompletionCriterionEntity> byId = new HashMap<>();
        existing.forEach(criterion -> byId.put(criterion.id, criterion));
        Set<String> retained = new LinkedHashSet<>();
        List<CompletionCriterionEntity> updated = new ArrayList<>();

        for (var input : inputs) {
            CompletionCriterionEntity criterion;
            if (input.id() == null || input.id().isBlank()) {
                criterion = CompletionCriterionEntity.create(DEFAULT_USER_ID, goal.id);
            } else {
                criterion = byId.get(input.id());
                if (criterion == null) {
                    throw rule("INVALID_CRITERION", "完成标准不属于当前目标。");
                }
                retained.add(criterion.id);
            }
            var changed = criterion.type != input.type()
                    || !Objects.equals(criterion.description, input.description().trim())
                    || !Objects.equals(criterion.targetValue, input.targetValue())
                    || !Objects.equals(criterion.unit, blankToNull(input.unit()));
            applyCriterionInput(criterion, input);
            if (changed && criterion.satisfied) {
                criterion.setSatisfied(false);
            }
            updated.add(criterion);
        }

        existing.stream()
                .filter(criterion -> !retained.contains(criterion.id))
                .forEach(criterionRepository::delete);
        criterionRepository.saveAll(updated);
    }

    private void applyCriterionInput(CompletionCriterionEntity criterion, CriterionInput input) {
        criterion.type = input.type();
        criterion.description = requiredText(input.description(), "完成标准说明不能为空。");
        criterion.targetValue = input.targetValue();
        criterion.unit = blankToNull(input.unit());
        criterion.required = input.required();
        criterion.updatedAt = Instant.now();
    }

    private void saveNewMilestones(GoalEntity goal, List<MilestoneInput> inputs) {
        var milestones = new ArrayList<MilestoneEntity>();
        for (int index = 0; index < inputs.size(); index++) {
            var input = inputs.get(index);
            var milestone = MilestoneEntity.create(DEFAULT_USER_ID, goal.id);
            applyMilestoneInput(
                    milestone,
                    input.title(),
                    input.acceptance(),
                    input.targetDate(),
                    input.weight(),
                    input.sortOrder() == null ? index + 1 : input.sortOrder());
            milestones.add(milestone);
        }
        milestoneRepository.saveAll(milestones);
        milestones.forEach(milestone -> recordRevision(
                "MILESTONE",
                milestone.id,
                "CREATE",
                "随目标创建里程碑",
                Instant.now(),
                null,
                snapshot(toMilestoneView(milestone))));
    }

    private void applyMilestoneInput(
            MilestoneEntity milestone,
            String title,
            String acceptance,
            LocalDate targetDate,
            BigDecimal weight,
            Integer sortOrder) {
        milestone.title = requiredText(title, "里程碑名称不能为空。");
        milestone.acceptance = requiredText(acceptance, "里程碑验收结果不能为空。");
        milestone.targetDate = targetDate;
        milestone.weight = weight;
        milestone.sortOrder = sortOrder == null ? 0 : sortOrder;
        milestone.touch();
    }

    private boolean isStructuralGoalChange(GoalEntity goal, UpdateGoalRequest request) {
        return (request.startDate() != null && !request.startDate().equals(goal.startDate))
                || (request.targetDate() != null && !request.targetDate().equals(goal.targetDate))
                || (request.progressMode() != null && request.progressMode() != goal.progressMode)
                || request.completionCriteria() != null
                || request.clearParent()
                || (request.parentId() != null
                        && !Objects.equals(blankToNull(request.parentId()), goal.parentId));
    }

    private void validateGoalDates(LocalDate startDate, LocalDate targetDate) {
        if (startDate == null || targetDate == null || !targetDate.isAfter(startDate)) {
            throw rule("INVALID_GOAL_DATES", "目标日期必须晚于开始日期。");
        }
    }

    private void validateCriterionInputs(List<CriterionInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            throw rule("CRITERION_REQUIRED", "目标至少需要一条完成标准。");
        }
        for (var input : inputs) {
            if (input.type() == CriterionType.METRIC && input.targetValue() == null) {
                throw rule("INVALID_CRITERION", "数值型完成标准必须填写目标值。");
            }
        }
    }

    private void validateMetricDefinition(
            ProgressMode mode,
            String metricName,
            BigDecimal startValue,
            BigDecimal targetValue) {
        if (mode != ProgressMode.SINGLE_METRIC) {
            return;
        }
        if (blankToNull(metricName) == null || startValue == null || targetValue == null) {
            throw rule("INVALID_METRIC", "单指标模式必须填写指标名称、起始值和目标值。");
        }
        if (targetValue.compareTo(startValue) == 0) {
            throw rule("INVALID_METRIC", "单指标目标值不能等于起始值。");
        }
    }

    private void validateMilestoneInputs(GoalEntity goal, List<MilestoneInput> inputs) {
        for (var input : inputs) {
            validateMilestoneDate(goal, input.targetDate());
        }
    }

    private void validateMilestoneDate(GoalEntity goal, LocalDate targetDate) {
        if (targetDate == null
                || targetDate.isBefore(goal.startDate)
                || targetDate.isAfter(goal.targetDate)) {
            throw rule("INVALID_MILESTONE_DATE", "里程碑日期必须位于目标周期内。");
        }
    }

    private void validateStartInputRules(
            List<CriterionInput> criteria, List<MilestoneInput> milestones, ProgressMode mode) {
        validateCriterionInputs(criteria);
        if (mode == ProgressMode.MILESTONE_WEIGHTED) {
            if (milestones.isEmpty()) {
                throw rule("MILESTONE_REQUIRED", "里程碑加权模式至少需要一个里程碑。");
            }
            var sum = milestones.stream()
                    .map(MilestoneInput::weight)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            validateWeightSum(sum);
        }
    }

    private void validatePersistedStartRules(
            List<CompletionCriterionEntity> criteria,
            List<MilestoneEntity> milestones,
            ProgressMode mode) {
        if (criteria.isEmpty()) {
            throw rule("CRITERION_REQUIRED", "目标至少需要一条完成标准。");
        }
        if (mode == ProgressMode.MILESTONE_WEIGHTED) {
            validateMilestoneWeightSum(milestones);
        }
    }

    private void validateActiveMilestoneWeights(GoalEntity goal) {
        validateMilestoneWeightSum(
                milestoneRepository.findByGoalIdOrderBySortOrderAscCreatedAtAsc(goal.id));
    }

    private void validateMilestoneWeightSum(List<MilestoneEntity> milestones) {
        var effective = milestones.stream()
                .filter(milestone -> milestone.status != MilestoneStatus.CANCELLED)
                .toList();
        if (effective.isEmpty()) {
            throw rule("MILESTONE_REQUIRED", "里程碑加权模式至少需要一个有效里程碑。");
        }
        var sum = effective.stream()
                .map(milestone -> milestone.weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        validateWeightSum(sum);
    }

    private void validateWeightSum(BigDecimal sum) {
        if (sum == null || sum.compareTo(ONE_HUNDRED) != 0) {
            throw rule("INVALID_MILESTONE_WEIGHT", "有效里程碑权重合计必须为 100%。");
        }
    }

    private void validateHierarchy(String parentId, String goalId) {
        var normalizedParentId = blankToNull(parentId);
        if (normalizedParentId == null) {
            if (goalId != null && maxSubtreeHeight(goalId, new LinkedHashSet<>()) > 4) {
                throw rule("GOAL_DEPTH_EXCEEDED", "目标层级最多为 4 层。");
            }
            return;
        }
        if (Objects.equals(normalizedParentId, goalId)) {
            throw rule("GOAL_CYCLE", "目标不能把自己设为父目标。");
        }

        var parent = requireGoal(normalizedParentId);
        if (parent.status == GoalStatus.ARCHIVED) {
            throw rule("ARCHIVED_PARENT", "不能在已归档目标下创建子目标。");
        }
        int parentDepth = 1;
        var cursor = parent;
        Set<String> seen = new LinkedHashSet<>();
        while (cursor != null) {
            if (!seen.add(cursor.id) || Objects.equals(cursor.id, goalId)) {
                throw rule("GOAL_CYCLE", "目标父子关系不能形成循环。");
            }
            if (cursor.parentId == null) {
                break;
            }
            cursor = requireGoal(cursor.parentId);
            parentDepth++;
        }

        int subtreeHeight = goalId == null ? 1 : maxSubtreeHeight(goalId, new LinkedHashSet<>());
        if (parentDepth + subtreeHeight > 4) {
            throw rule("GOAL_DEPTH_EXCEEDED", "目标层级最多为 4 层。");
        }
    }

    private String rootIdOf(GoalEntity goal) {
        var cursor = goal;
        var seen = new LinkedHashSet<String>();
        while (cursor.parentId != null) {
            if (!seen.add(cursor.id)) {
                throw rule("GOAL_CYCLE", "检测到循环目标关系。");
            }
            cursor = requireGoal(cursor.parentId);
        }
        return cursor.id;
    }

    private int maxSubtreeHeight(String goalId, Set<String> path) {
        if (!path.add(goalId)) {
            throw rule("GOAL_CYCLE", "检测到循环目标关系。");
        }
        var children = goalRepository
                .findByParentIdAndUserIdAndDeletedAtIsNullOrderByCreatedAtAsc(
                        goalId, DEFAULT_USER_ID);
        int childHeight = children.stream()
                .mapToInt(child -> maxSubtreeHeight(child.id, new LinkedHashSet<>(path)))
                .max()
                .orElse(0);
        return 1 + childHeight;
    }

    private GoalEntity requireGoal(String goalId) {
        return goalRepository
                .findByIdAndUserIdAndDeletedAtIsNull(goalId, DEFAULT_USER_ID)
                .orElseThrow(() -> notFound("目标不存在或已删除。"));
    }

    private MilestoneEntity requireMilestone(String milestoneId) {
        return milestoneRepository
                .findByIdAndUserId(milestoneId, DEFAULT_USER_ID)
                .orElseThrow(() -> notFound("里程碑不存在。"));
    }

    private DailyTaskEntity requireDailyTask(String taskId) {
        return dailyTaskRepository
                .findByIdAndUserId(taskId, DEFAULT_USER_ID)
                .orElseThrow(() -> notFound("日常任务不存在。"));
    }

    private void rejectArchived(GoalEntity goal) {
        if (goal.status == GoalStatus.ARCHIVED) {
            throw rule("ARCHIVED_GOAL_READ_ONLY", "已归档目标为只读，请先恢复目标。");
        }
    }

    private void assertVersion(Integer currentVersion, Integer requestedVersion) {
        if (!Objects.equals(currentVersion, requestedVersion)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "VERSION_CONFLICT",
                    "内容已更新，请刷新后再提交。",
                    currentVersion);
        }
    }

    private GoalListItem toListItem(GoalEntity goal) {
        var criteria = criterionRepository.findByGoalIdOrderByCreatedAtAsc(goal.id);
        var milestones = milestoneRepository.findByGoalIdOrderBySortOrderAscCreatedAtAsc(goal.id);
        var nextMilestone = milestones.stream()
                .filter(milestone -> milestone.status != MilestoneStatus.ACHIEVED
                        && milestone.status != MilestoneStatus.CANCELLED)
                .min(Comparator.comparing(milestone -> milestone.targetDate))
                .orElse(null);
        return new GoalListItem(
                goal.id,
                goal.parentId,
                goal.title,
                goal.startDate,
                goal.targetDate,
                goal.priority,
                goal.status,
                goal.progressMode,
                goal.currentProgress,
                goal.sortOrder,
                goal.version,
                criteria.size(),
                criteria.stream().filter(criterion -> criterion.satisfied).count(),
                milestones.size(),
                nextMilestone == null ? null : nextMilestone.title,
                nextMilestone == null ? null : nextMilestone.targetDate,
                goal.updatedAt);
    }

    private GoalDetail toDetail(GoalEntity goal) {
        var parentTitle = goal.parentId == null
                ? null
                : goalRepository
                        .findByIdAndUserIdAndDeletedAtIsNull(goal.parentId, DEFAULT_USER_ID)
                        .map(parent -> parent.title)
                        .orElse(null);
        var criteria = criterionRepository.findByGoalIdOrderByCreatedAtAsc(goal.id).stream()
                .map(this::toCriterionView)
                .toList();
        var milestones = milestoneRepository
                .findByGoalIdOrderBySortOrderAscCreatedAtAsc(goal.id)
                .stream()
                .map(this::toMilestoneView)
                .toList();
        var dailyTasks = dailyTaskRepository
                .findByGoalIdAndActiveTrueOrderBySortOrderAscCreatedAtAsc(goal.id)
                .stream()
                .map(this::toDailyTaskView)
                .toList();
        var children = goalRepository
                .findByParentIdAndUserIdAndDeletedAtIsNullOrderByCreatedAtAsc(
                        goal.id, DEFAULT_USER_ID)
                .stream()
                .sorted(SIBLING_ORDER)
                .map(this::toListItem)
                .toList();
        return new GoalDetail(
                goal.id,
                goal.parentId,
                parentTitle,
                goal.title,
                goal.description,
                goal.startDate,
                goal.targetDate,
                goal.priority,
                goal.status,
                goal.progressMode,
                goal.currentProgress,
                goal.metricName,
                goal.metricUnit,
                goal.metricStartValue,
                goal.metricTargetValue,
                goal.metricCurrentValue,
                goal.version,
                goal.pausedAt,
                goal.completedAt,
                goal.archivedAt,
                goal.createdAt,
                goal.updatedAt,
                criteria,
                milestones,
                dailyTasks,
                children);
    }

    private CriterionView toCriterionView(CompletionCriterionEntity criterion) {
        return new CriterionView(
                criterion.id,
                criterion.type,
                criterion.description,
                criterion.targetValue,
                criterion.unit,
                criterion.required,
                criterion.satisfied,
                criterion.satisfiedAt);
    }

    private MilestoneView toMilestoneView(MilestoneEntity milestone) {
        return new MilestoneView(
                milestone.id,
                milestone.title,
                milestone.acceptance,
                milestone.targetDate,
                milestone.weight,
                milestone.progress,
                milestone.status,
                milestone.completedAt,
                milestone.sortOrder,
                milestone.version);
    }

    private DailyTaskView toDailyTaskView(DailyTaskEntity task) {
        var completedToday = dailyTaskCheckinRepository
                .findByDailyTaskIdAndCheckDate(task.id, LocalDate.now(APP_ZONE))
                .map(checkin -> checkin.completed)
                .orElse(false);
        return new DailyTaskView(
                task.id, task.title, completedToday, task.sortOrder, task.version);
    }

    private RevisionView toRevisionView(RevisionEntity revision) {
        return new RevisionView(
                revision.id,
                revision.entityType,
                revision.entityId,
                revision.revisionNo,
                revision.actionCode,
                revision.reason,
                revision.effectiveAt,
                parseJson(revision.beforeJson),
                parseJson(revision.afterJson),
                revision.createdAt);
    }

    private void recordRevision(
            String entityType,
            String entityId,
            String action,
            String reason,
            Instant effectiveAt,
            String before,
            String after) {
        var revision = RevisionEntity.create();
        revision.userId = DEFAULT_USER_ID;
        revision.entityType = entityType;
        revision.entityId = entityId;
        revision.revisionNo = revisionRepository
                        .findTopByEntityTypeAndEntityIdOrderByRevisionNoDesc(entityType, entityId)
                        .map(previous -> previous.revisionNo + 1)
                        .orElse(1);
        revision.actionCode = action;
        revision.reason = blankToNull(reason);
        revision.effectiveAt = effectiveAt == null ? Instant.now() : effectiveAt;
        revision.beforeJson = before;
        revision.afterJson = after;
        revisionRepository.save(revision);
    }

    private String snapshot(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法保存版本快照。", exception);
        }
    }

    private JsonNode parseJson(String value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法读取版本快照。", exception);
        }
    }

    private String requiredText(String value, String message) {
        var normalized = blankToNull(value);
        if (normalized == null) {
            throw rule("VALIDATION_FAILED", message);
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void requireReason(String reason, String message) {
        if (blankToNull(reason) == null) {
            throw rule("CHANGE_REASON_REQUIRED", message);
        }
    }

    private ApiException rule(String code, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    private ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
