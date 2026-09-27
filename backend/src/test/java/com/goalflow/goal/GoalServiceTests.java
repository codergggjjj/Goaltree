package com.goalflow.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goalflow.api.ApiException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GoalServiceTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GoalService goalService;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private CompletionCriterionRepository criterionRepository;

    @Autowired
    private MilestoneRepository milestoneRepository;

    @Autowired
    private DailyTaskRepository dailyTaskRepository;

    @Autowired
    private DailyTaskCheckinRepository dailyTaskCheckinRepository;

    @Autowired
    private GoalLinkRepository goalLinkRepository;

    @MockitoSpyBean
    private RevisionRepository revisionRepository;

    @BeforeEach
    void cleanDatabase() {
        revisionRepository.deleteAll();
        goalLinkRepository.deleteAll();
        dailyTaskCheckinRepository.deleteAll();
        dailyTaskRepository.deleteAll();
        milestoneRepository.deleteAll();
        criterionRepository.deleteAll();
        goalRepository.deleteAll();
    }

    @Test
    void createsAndStartsWeightedGoalWithInitialHistory() {
        var created = goalService.createRootGoal(weightedGoal("发布一个完整作品", null, true));

        assertThat(created.status()).isEqualTo(GoalStatus.ACTIVE);
        assertThat(created.version()).isEqualTo(1);
        assertThat(created.completionCriteria()).hasSize(1);
        assertThat(created.milestones()).extracting(GoalApiModels.MilestoneView::weight)
                .containsExactly(new BigDecimal("100"));
        assertThat(goalService.getGoalRevisions(created.id()))
                .extracting(GoalApiModels.RevisionView::action)
                .contains("CREATE");
    }

    @Test
    void rejectsInvalidGoalDatesAndInvalidWeightedMilestones() {
        var invalidDates = new GoalApiModels.CreateGoalRequest(
                "日期错误",
                null,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 1),
                GoalPriority.MEDIUM,
                ProgressMode.MANUAL,
                null,
                null,
                null,
                null,
                null,
                List.of(criterion()),
                List.of(),
                false);

        assertApiCode(() -> goalService.createRootGoal(invalidDates), "INVALID_GOAL_DATES");

        var badWeights = new GoalApiModels.CreateGoalRequest(
                "权重错误",
                null,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 12, 1),
                GoalPriority.HIGH,
                ProgressMode.MILESTONE_WEIGHTED,
                null,
                null,
                null,
                null,
                null,
                List.of(criterion()),
                List.of(milestone("只占一半", "50")),
                true);
        assertApiCode(() -> goalService.createRootGoal(badWeights), "INVALID_MILESTONE_WEIGHT");
    }

    @Test
    void blocksFifthLevelAndCircularParentRelationship() {
        var root = goalService.createRootGoal(manualDraft("根目标", null));
        var levelTwo = goalService.createGoal(manualDraft("二级目标", root.id()));
        var levelThree = goalService.createGoal(manualDraft("三级目标", levelTwo.id()));
        var levelFour = goalService.createGoal(manualDraft("四级目标", levelThree.id()));

        assertApiCode(
                () -> goalService.createGoal(manualDraft("五级目标", levelFour.id())),
                "GOAL_DEPTH_EXCEEDED");

        var circularUpdate = new GoalApiModels.UpdateGoalRequest(
                root.version(),
                null,
                null,
                null,
                null,
                null,
                null,
                levelTwo.id(),
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        assertApiCode(() -> goalService.updateGoal(root.id(), circularUpdate), "GOAL_CYCLE");
    }

    @Test
    void returnsConflictForStaleGoalVersion() {
        var created = goalService.createRootGoal(manualDraft("并发目标", null));
        var staleUpdate = new GoalApiModels.UpdateGoalRequest(
                created.version() - 1,
                "不应覆盖的新名称",
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        assertThatThrownBy(() -> goalService.updateGoal(created.id(), staleUpdate))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> {
                    var apiError = (ApiException) error;
                    assertThat(apiError.getCode()).isEqualTo("VERSION_CONFLICT");
                    assertThat(apiError.getCurrentVersion()).isEqualTo(created.version());
                });
    }

    @Test
    void completesOnlyAfterRequiredCriterionIsSatisfied() {
        var created = goalService.createRootGoal(new GoalApiModels.CreateGoalRequest(
                "完成门槛",
                null,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 10, 1),
                GoalPriority.HIGH,
                ProgressMode.MANUAL,
                null,
                null,
                null,
                null,
                null,
                List.of(criterion()),
                List.of(),
                true));

        assertApiCode(
                () -> goalService.completeGoal(
                        created.id(),
                        new GoalApiModels.GoalActionRequest(created.version(), null, null)),
                "REQUIRED_CRITERIA_UNSATISFIED");

        var satisfied = goalService.setCriterionSatisfaction(
                created.id(),
                created.completionCriteria().get(0).id(),
                new GoalApiModels.CriterionSatisfactionRequest(
                        created.version(), true, "验收结果已确认"));
        var completed = goalService.completeGoal(
                created.id(),
                new GoalApiModels.GoalActionRequest(
                        satisfied.version(), "所有完成标准均已满足", null));

        assertThat(completed.status()).isEqualTo(GoalStatus.COMPLETED);
        assertThat(completed.currentProgress()).isEqualByComparingTo("100");
    }

    @Test
    void createsMilestoneAndIncludesItInGoalHistory() {
        var created = goalService.createRootGoal(manualDraft("里程碑目标", null));
        var updated = goalService.createMilestone(
                created.id(),
                new GoalApiModels.CreateMilestoneRequest(
                        created.version(),
                        "完成原型",
                        "可以演示完整交互",
                        LocalDate.of(2026, 9, 1),
                        new BigDecimal("40"),
                        1,
                        null));

        assertThat(updated.milestones()).extracting(GoalApiModels.MilestoneView::title)
                .containsExactly("完成原型");
        assertThat(updated.version()).isGreaterThan(created.version());
        assertThat(goalService.getGoalRevisions(created.id()))
                .extracting(GoalApiModels.RevisionView::entityType)
                .contains("MILESTONE");
    }

    @Test
    void supportsLightweightActiveGoalWithoutMilestonesOrRequiredCriteria() {
        var created = goalService.createRootGoal(new GoalApiModels.CreateGoalRequest(
                "记录一个简单目标",
                "只保留一段备注",
                LocalDate.of(2026, 7, 27),
                LocalDate.of(2046, 7, 27),
                GoalPriority.MEDIUM,
                ProgressMode.MANUAL,
                null,
                null,
                null,
                null,
                null,
                List.of(new GoalApiModels.CriterionInput(
                        null,
                        CriterionType.BOOLEAN,
                        "目标已完成",
                        null,
                        null,
                        false)),
                List.of(),
                true));

        var updated = goalService.updateGoal(
                created.id(),
                new GoalApiModels.UpdateGoalRequest(
                        created.version(),
                        "修改后的简单目标",
                        "更新备注",
                        created.startDate(),
                        created.targetDate(),
                        created.priority(),
                        created.progressMode(),
                        null,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "更新目标记录",
                        null));

        assertThat(updated.status()).isEqualTo(GoalStatus.ACTIVE);
        assertThat(updated.title()).isEqualTo("修改后的简单目标");
        assertThat(updated.milestones()).isEmpty();
    }

    @Test
    void recordsDailyTaskCompletionOnlyForToday() {
        var goal = goalService.createRootGoal(manualDraft("建立阅读习惯", null));
        var withTask = goalService.createDailyTask(
                goal.id(),
                new GoalApiModels.CreateDailyTaskRequest(goal.version(), "阅读 20 分钟", 1));
        var task = withTask.dailyTasks().get(0);

        var completed = goalService.checkinDailyTask(
                task.id(),
                new GoalApiModels.DailyTaskCheckinRequest(task.version(), true));
        assertThat(completed.dailyTasks().get(0).completedToday()).isTrue();

        var completedTask = completed.dailyTasks().get(0);
        var undone = goalService.checkinDailyTask(
                completedTask.id(),
                new GoalApiModels.DailyTaskCheckinRequest(
                        completedTask.version(), false));
        assertThat(undone.dailyTasks().get(0).completedToday()).isFalse();
        assertThat(dailyTaskCheckinRepository.count()).isEqualTo(1);
    }

    @Test
    void treeCompletionReopenAndLeafDeletionKeepParentInSync() {
        var root = goalService.createRootGoal(manualDraft("成为全栈工程师", null));
        root = goalService.startGoal(root.id(), new GoalApiModels.GoalActionRequest(root.version(), null, null));
        var child = goalService.createGoal(manualDraft("学习后端", root.id()));
        var leaf = goalService.createGoal(manualDraft("Spring Boot", child.id()));

        var rootId = root.id();
        assertApiCode(() -> goalService.deleteGoal(rootId, goalService.getGoal(rootId).version()), "GOAL_HAS_CHILDREN");
        goalService.completeTreeGoal(leaf.id(), new GoalApiModels.GoalActionRequest(leaf.version(), null, null));
        assertThat(goalService.getGoal(leaf.id()).status()).isEqualTo(GoalStatus.COMPLETED);
        assertThat(goalService.getGoal(root.id()).status()).isEqualTo(GoalStatus.COMPLETED);
        assertThat(goalService.getGoal(root.id()).currentProgress()).isEqualByComparingTo("100");

        var completedLeaf = goalService.getGoal(leaf.id());
        goalService.resumeGoal(leaf.id(), new GoalApiModels.GoalActionRequest(completedLeaf.version(), null, null));
        assertThat(goalService.getGoal(root.id()).status()).isEqualTo(GoalStatus.ACTIVE);
        assertThat(goalService.getGoal(root.id()).currentProgress()).isEqualByComparingTo("0");

        var reopened = goalService.getGoal(leaf.id());
        goalService.deleteGoal(leaf.id(), reopened.version());
        assertThat(goalService.listGoals(true)).noneMatch(item -> item.id().equals(leaf.id()));
    }

    @Test
    void deletingSubtreeKeepsOtherRootsAndUpdatesAncestors() {
        var root = goalService.createRootGoal(manualDraft("主目标", null));
        var branch = goalService.createGoal(manualDraft("分组", root.id()));
        var leaf = goalService.createGoal(manualDraft("执行项", branch.id()));
        var sibling = goalService.createGoal(manualDraft("保留项", root.id()));
        var otherRoot = goalService.createRootGoal(manualDraft("另一个主目标", null));
        var otherChild = goalService.createGoal(manualDraft("另一个执行项", otherRoot.id()));

        goalService.deleteGoalTree(branch.id(), branch.version());

        assertThat(goalService.listGoals(true)).extracting(GoalApiModels.GoalListItem::id)
                .contains(root.id(), sibling.id(), otherRoot.id(), otherChild.id())
                .doesNotContain(branch.id(), leaf.id());
        assertThat(goalService.getGoal(root.id()).childGoals())
                .extracting(GoalApiModels.GoalListItem::id).containsExactly(sibling.id());
        assertThat(revisionRepository.findByUserIdAndEntityTypeAndEntityIdOrderByRevisionNoDesc(
                GoalService.DEFAULT_USER_ID, "GOAL", leaf.id()))
                .extracting(revision -> revision.reason).contains("删除目标树");

        goalService.deleteGoalTree(otherRoot.id(), goalService.getGoal(otherRoot.id()).version());
        assertThat(goalService.listGoals(true)).extracting(GoalApiModels.GoalListItem::id)
                .contains(root.id(), sibling.id()).doesNotContain(otherRoot.id(), otherChild.id());
    }

    @Test
    void staleTreeDeleteLeavesTheEntireSubtreeUntouched() {
        var root = goalService.createRootGoal(manualDraft("主目标", null));
        var child = goalService.createGoal(manualDraft("子目标", root.id()));
        assertApiCode(() -> goalService.deleteGoalTree(root.id(), root.version() - 1), "VERSION_CONFLICT");
        assertThat(goalService.listGoals(true)).extracting(GoalApiModels.GoalListItem::id)
                .contains(root.id(), child.id());
    }

    @Test
    void treeDeleteRollsBackWhenAnAuditWriteFails() {
        var root = goalService.createRootGoal(manualDraft("主目标", null));
        var child = goalService.createGoal(manualDraft("子目标", root.id()));
        var version = goalService.getGoal(root.id()).version();
        doThrow(new IllegalStateException("audit write failed"))
                .when(revisionRepository).save(any(RevisionEntity.class));

        assertThatThrownBy(() -> goalService.deleteGoalTree(root.id(), version))
                .isInstanceOf(IllegalStateException.class);
        assertThat(goalService.listGoals(true)).extracting(GoalApiModels.GoalListItem::id)
                .contains(root.id(), child.id());
    }

    @Test
    void trashRestoresOnlyTheDeletedBatchAndLeavesOtherTreesAlone() {
        var root = goalService.createRootGoal(manualDraft("主目标", null));
        var branch = goalService.createGoal(manualDraft("分组", root.id()));
        var oldLeaf = goalService.createGoal(manualDraft("先前删除", branch.id()));
        var activeLeaf = goalService.createGoal(manualDraft("一起删除", branch.id()));
        var other = goalService.createRootGoal(manualDraft("其他主目标", null));
        goalService.deleteGoal(oldLeaf.id(), oldLeaf.version());
        goalService.deleteGoalTree(branch.id(), goalService.getGoal(branch.id()).version());

        assertThat(goalService.listTrash()).extracting(GoalApiModels.TrashGoalItem::id)
                .contains(branch.id(), oldLeaf.id());
        var deleted = goalService.listTrash().stream().filter(item -> item.id().equals(branch.id())).findFirst().orElseThrow();
        assertThat(deleted.descendantCount()).isEqualTo(1);
        goalService.restoreGoal(branch.id(), deleted.version());

        assertThat(goalService.listGoals(true)).extracting(GoalApiModels.GoalListItem::id)
                .contains(root.id(), branch.id(), activeLeaf.id(), other.id())
                .doesNotContain(oldLeaf.id());
        assertThat(goalService.listTrash()).extracting(GoalApiModels.TrashGoalItem::id)
                .contains(oldLeaf.id()).doesNotContain(branch.id());
    }

    @Test
    void restoreRequiresCurrentVersionAndAnActiveParent() {
        var root = goalService.createRootGoal(manualDraft("主目标", null));
        var child = goalService.createGoal(manualDraft("子目标", root.id()));
        goalService.deleteGoal(child.id(), child.version());
        var deletedChild = goalService.listTrash().stream().filter(item -> item.id().equals(child.id())).findFirst().orElseThrow();
        assertApiCode(() -> goalService.restoreGoal(child.id(), deletedChild.version() - 1), "VERSION_CONFLICT");
        goalService.deleteGoalTree(root.id(), goalService.getGoal(root.id()).version());
        assertApiCode(() -> goalService.restoreGoal(child.id(), deletedChild.version()), "RESTORE_PARENT_REQUIRED");
        assertThat(goalService.listTrash()).extracting(GoalApiModels.TrashGoalItem::id).contains(root.id(), child.id());
    }

    @Test
    void trashEndpointsListAndRestoreDeletedGoal() throws Exception {
        var root = goalService.createRootGoal(manualDraft("可恢复主目标", null));
        goalService.deleteGoalTree(root.id(), root.version());
        var deleted = goalService.listTrash().get(0);
        mockMvc.perform(get("/api/goals/trash"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("可恢复主目标"));
        mockMvc.perform(post("/api/goals/{goalId}/restore", root.id())
                        .param("version", String.valueOf(deleted.version())))
                .andExpect(status().isNoContent());
        assertThat(goalService.getGoal(root.id()).title()).isEqualTo("可恢复主目标");
    }

    @Test
    void restoreRollsBackWhenAuditWriteFails() {
        var root = goalService.createRootGoal(manualDraft("主目标", null));
        var child = goalService.createGoal(manualDraft("子目标", root.id()));
        goalService.deleteGoalTree(root.id(), goalService.getGoal(root.id()).version());
        var version = goalService.listTrash().get(0).version();
        doThrow(new IllegalStateException("audit write failed"))
                .when(revisionRepository).save(any(RevisionEntity.class));
        assertThatThrownBy(() -> goalService.restoreGoal(root.id(), version))
                .isInstanceOf(IllegalStateException.class);
        assertThat(goalService.listGoals(true)).extracting(GoalApiModels.GoalListItem::id)
                .doesNotContain(root.id(), child.id());
    }

    @Test
    void completingParentCascadesAndMovingLeafWithinOneTree() {
        var first = goalService.createRootGoal(manualDraft("第一主目标", null));
        first = goalService.startGoal(first.id(), new GoalApiModels.GoalActionRequest(first.version(), null, null));
        var second = goalService.createGoal(manualDraft("第二分组", first.id()));
        var completedLeaf = goalService.createGoal(manualDraft("已完成任务", first.id()));
        var openLeaf = goalService.createGoal(manualDraft("待完成任务", first.id()));
        goalService.completeTreeGoal(completedLeaf.id(), new GoalApiModels.GoalActionRequest(completedLeaf.version(), null, null));
        assertThat(goalService.getGoal(first.id()).currentProgress()).isEqualByComparingTo("33");

        var latestLeaf = goalService.getGoal(completedLeaf.id());
        goalService.updateGoal(completedLeaf.id(), new GoalApiModels.UpdateGoalRequest(
                latestLeaf.version(), null, null, null, null, null, null, second.id(), false,
                null, null, null, null, null, "调整目标层级", null));
        assertThat(goalService.getGoal(first.id()).currentProgress()).isEqualByComparingTo("50");
        assertThat(goalService.getGoal(second.id()).status()).isEqualTo(GoalStatus.COMPLETED);

        var latestFirst = goalService.getGoal(first.id());
        goalService.completeTreeGoal(first.id(), new GoalApiModels.GoalActionRequest(latestFirst.version(), null, null));
        assertThat(goalService.getGoal(openLeaf.id()).status()).isEqualTo(GoalStatus.COMPLETED);
        assertThat(goalService.getGoal(first.id()).status()).isEqualTo(GoalStatus.COMPLETED);
    }

    @Test
    void rootAndChildCreationRequireTheirDedicatedParentRules() {
        var root = goalService.createRootGoal(manualDraft("主目标", null));
        assertApiCode(() -> goalService.createGoal(manualDraft("无父子目标", null)), "PARENT_REQUIRED");
        assertApiCode(() -> goalService.createGoal(manualDraft("空父节点", " ")), "PARENT_REQUIRED");
        assertApiCode(() -> goalService.createRootGoal(manualDraft("错误主目标", root.id())), "ROOT_PARENT_NOT_ALLOWED");
        assertApiCode(() -> goalService.createGoal(manualDraft("无效父节点", "missing")), "NOT_FOUND");

        var child = goalService.createGoal(manualDraft("子目标", root.id()));
        assertThat(child.parentId()).isEqualTo(root.id());
        assertThat(goalService.getGoal(root.id()).childGoals())
                .extracting(GoalApiModels.GoalListItem::id).contains(child.id());
    }

    @Test
    void httpCreationRoutesEnforceRootAndChildBoundaries() throws Exception {
        mockMvc.perform(post("/api/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(manualDraft("无父子目标", null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PARENT_REQUIRED"));

        mockMvc.perform(post("/api/goals/roots")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(manualDraft("主目标", null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").doesNotExist());
    }

    @Test
    void loopbackFrontendCanSubmitAndDeleteGoals() throws Exception {
        var origin = "http://127.0.0.1:5173";
        mockMvc.perform(post("/api/goals")
                        .header("Origin", origin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(manualDraft("无父子目标", null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PARENT_REQUIRED"));

        mockMvc.perform(delete("/api/goals/missing/tree")
                        .header("Origin", origin)
                        .param("version", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void editingPreservesRootAndChildTreeBoundaries() {
        var first = goalService.createRootGoal(manualDraft("第一主目标", null));
        var second = goalService.createRootGoal(manualDraft("第二主目标", null));
        var branch = goalService.createGoal(manualDraft("分组", first.id()));
        var child = goalService.createGoal(manualDraft("子目标", branch.id()));
        var rootVersion = goalService.getGoal(first.id()).version();
        assertApiCode(() -> goalService.updateGoal(first.id(), new GoalApiModels.UpdateGoalRequest(
                rootVersion, null, null, null, null, null, null, second.id(), false,
                null, null, null, null, null, "调整层级", null)), "ROOT_REPARENT_FORBIDDEN");

        var childVersion = goalService.getGoal(child.id()).version();
        assertApiCode(() -> goalService.updateGoal(child.id(), new GoalApiModels.UpdateGoalRequest(
                childVersion, null, null, null, null, null, null, null, true,
                null, null, null, null, null, "调整层级", null)), "CHILD_PARENT_REQUIRED");
        assertApiCode(() -> goalService.updateGoal(child.id(), new GoalApiModels.UpdateGoalRequest(
                childVersion, null, null, null, null, null, null, second.id(), false,
                null, null, null, null, null, "调整层级", null)), "CROSS_TREE_MOVE_FORBIDDEN");

        var moved = goalService.updateGoal(child.id(), new GoalApiModels.UpdateGoalRequest(
                childVersion, null, null, null, null, null, null, first.id(), false,
                null, null, null, null, null, "调整层级", null));
        assertThat(moved.parentId()).isEqualTo(first.id());
        assertThat(goalService.getGoal(second.id()).childGoals()).isEmpty();
    }

    @Test
    void movesOnlySiblingsAndKeepsTheirOrderAfterReadingGoalsAgain() {
        var firstRoot = goalService.createRootGoal(manualDraft("第一主目标", null));
        var secondRoot = goalService.createRootGoal(manualDraft("第二主目标", null));
        var firstChild = goalService.createGoal(manualDraft("第一个子目标", firstRoot.id()));
        var secondChild = goalService.createGoal(manualDraft("第二个子目标", firstRoot.id()));
        var otherChild = goalService.createGoal(manualDraft("另一棵树的子目标", secondRoot.id()));

        goalService.moveGoal(secondChild.id(), new GoalApiModels.MoveGoalRequest(
                secondChild.version(), GoalMoveDirection.UP));
        assertThat(goalService.getGoal(firstRoot.id()).childGoals())
                .extracting(GoalApiModels.GoalListItem::id)
                .containsExactly(secondChild.id(), firstChild.id());
        assertThat(goalService.getGoal(secondRoot.id()).childGoals())
                .extracting(GoalApiModels.GoalListItem::id).containsExactly(otherChild.id());

        goalService.moveGoal(secondRoot.id(), new GoalApiModels.MoveGoalRequest(
                goalService.getGoal(secondRoot.id()).version(), GoalMoveDirection.UP));
        assertThat(goalService.listGoals(true).stream().filter(item -> item.parentId() == null)
                .sorted(java.util.Comparator.comparingInt(GoalApiModels.GoalListItem::sortOrder))
                .map(GoalApiModels.GoalListItem::id))
                .containsExactly(secondRoot.id(), firstRoot.id());
        assertApiCode(() -> goalService.moveGoal(firstChild.id(), new GoalApiModels.MoveGoalRequest(
                goalService.getGoal(firstChild.id()).version(), GoalMoveDirection.DOWN)), "MOVE_NOT_AVAILABLE");
        assertApiCode(() -> goalService.moveGoal(firstChild.id(), new GoalApiModels.MoveGoalRequest(
                -1, GoalMoveDirection.UP)), "VERSION_CONFLICT");
    }

    @Test
    void createsGoalWithoutUserFacingDatesAndKeepsInternalLegacyDates() {
        var request = new GoalApiModels.CreateGoalRequest(
                "不设置截止日期的新目标", null, null, null,
                GoalPriority.MEDIUM, ProgressMode.MANUAL, null,
                null, null, null, null, List.of(criterion()), List.of(), false);
        var created = goalService.createRootGoal(request);
        assertThat(created.startDate()).isNotNull();
        assertThat(created.targetDate()).isAfter(created.startDate());
    }

    private GoalApiModels.CreateGoalRequest weightedGoal(
            String title, String parentId, boolean startImmediately) {
        return new GoalApiModels.CreateGoalRequest(
                title,
                "用于测试的里程碑加权目标",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 12, 1),
                GoalPriority.HIGH,
                ProgressMode.MILESTONE_WEIGHTED,
                parentId,
                null,
                null,
                null,
                null,
                List.of(criterion()),
                List.of(milestone("最终成果发布", "100")),
                startImmediately);
    }

    private GoalApiModels.CreateGoalRequest manualDraft(String title, String parentId) {
        return new GoalApiModels.CreateGoalRequest(
                title,
                null,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 12, 1),
                GoalPriority.MEDIUM,
                ProgressMode.MANUAL,
                parentId,
                null,
                null,
                null,
                null,
                List.of(criterion()),
                List.of(),
                false);
    }

    private GoalApiModels.CriterionInput criterion() {
        return new GoalApiModels.CriterionInput(
                null,
                CriterionType.ACCEPTANCE,
                "成果通过最终验收",
                null,
                null,
                true);
    }

    private GoalApiModels.MilestoneInput milestone(String title, String weight) {
        return new GoalApiModels.MilestoneInput(
                null,
                title,
                "提交可以检查的阶段成果",
                LocalDate.of(2026, 10, 1),
                new BigDecimal(weight),
                1);
    }

    private void assertApiCode(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApiException.class)
                .satisfies(error ->
                        assertThat(((ApiException) error).getCode()).isEqualTo(code));
    }
}
