package com.goalflow.goal;

import static com.goalflow.goal.GoalApiModels.CreateDailyTaskRequest;
import static com.goalflow.goal.GoalApiModels.CreateGoalRequest;
import static com.goalflow.goal.GoalApiModels.CreateMilestoneRequest;
import static com.goalflow.goal.GoalApiModels.CriterionSatisfactionRequest;
import static com.goalflow.goal.GoalApiModels.DailyTaskCheckinRequest;
import static com.goalflow.goal.GoalApiModels.GoalActionRequest;
import static com.goalflow.goal.GoalApiModels.GoalDetail;
import static com.goalflow.goal.GoalApiModels.GoalListItem;
import static com.goalflow.goal.GoalApiModels.TrashGoalItem;
import static com.goalflow.goal.GoalApiModels.MoveGoalRequest;
import static com.goalflow.goal.GoalApiModels.MilestoneActionRequest;
import static com.goalflow.goal.GoalApiModels.RevisionView;
import static com.goalflow.goal.GoalApiModels.UpdateGoalRequest;
import static com.goalflow.goal.GoalApiModels.UpdateDailyTaskRequest;
import static com.goalflow.goal.GoalApiModels.UpdateMilestoneRequest;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @GetMapping("/goals")
    @Operation(summary = "获取目标列表")
    public List<GoalListItem> listGoals(
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return goalService.listGoals(includeArchived);
    }

    @GetMapping("/goals/trash")
    @Operation(summary = "查看已删除的目标和目标树")
    public List<TrashGoalItem> listTrash() {
        return goalService.listTrash();
    }

    @PostMapping("/goals")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "创建子目标，必须指定父目标")
    public GoalDetail createGoal(@Valid @RequestBody CreateGoalRequest request) {
        return goalService.createGoal(request);
    }

    @PostMapping("/goals/roots")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新建主目标及其独立目标树")
    public GoalDetail createRootGoal(@Valid @RequestBody CreateGoalRequest request) {
        return goalService.createRootGoal(request);
    }

    @GetMapping("/goals/{goalId}")
    @Operation(summary = "获取目标详情")
    public GoalDetail getGoal(@PathVariable String goalId) {
        return goalService.getGoal(goalId);
    }

    @PatchMapping("/goals/{goalId}")
    @Operation(summary = "编辑目标与完成标准")
    public GoalDetail updateGoal(
            @PathVariable String goalId, @Valid @RequestBody UpdateGoalRequest request) {
        return goalService.updateGoal(goalId, request);
    }

    @PostMapping("/goals/{goalId}/move")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "在同级目标中上移或下移")
    public void moveGoal(@PathVariable String goalId, @Valid @RequestBody MoveGoalRequest request) {
        goalService.moveGoal(goalId, request);
    }

    @DeleteMapping("/goals/{goalId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "软删除没有子目标的目标")
    public void deleteGoal(@PathVariable String goalId, @RequestParam int version) {
        goalService.deleteGoal(goalId, version);
    }

    @DeleteMapping("/goals/{goalId}/tree")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "软删除目标及其全部子目标")
    public void deleteGoalTree(@PathVariable String goalId, @RequestParam int version) {
        goalService.deleteGoalTree(goalId, version);
    }

    @PostMapping("/goals/{goalId}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "从回收站恢复目标或一次删除的目标树")
    public void restoreGoal(@PathVariable String goalId, @RequestParam int version) {
        goalService.restoreGoal(goalId, version);
    }

    @PostMapping("/goals/{goalId}/start")
    @Operation(summary = "启动草稿目标")
    public GoalDetail startGoal(
            @PathVariable String goalId, @Valid @RequestBody GoalActionRequest request) {
        return goalService.startGoal(goalId, request);
    }

    @PostMapping("/goals/{goalId}/pause")
    @Operation(summary = "暂停目标")
    public GoalDetail pauseGoal(
            @PathVariable String goalId, @Valid @RequestBody GoalActionRequest request) {
        return goalService.pauseGoal(goalId, request);
    }

    @PostMapping("/goals/{goalId}/resume")
    @Operation(summary = "恢复、重新打开或取消归档目标")
    public GoalDetail resumeGoal(
            @PathVariable String goalId, @Valid @RequestBody GoalActionRequest request) {
        return goalService.resumeGoal(goalId, request);
    }

    @PostMapping("/goals/{goalId}/complete")
    @Operation(summary = "确认完成目标")
    public GoalDetail completeGoal(
            @PathVariable String goalId, @Valid @RequestBody GoalActionRequest request) {
        return goalService.completeGoal(goalId, request);
    }

    @PostMapping("/goals/{goalId}/tree-complete")
    @Operation(summary = "在目标树中完成目标及全部后代")
    public GoalDetail completeTreeGoal(
            @PathVariable String goalId, @Valid @RequestBody GoalActionRequest request) {
        return goalService.completeTreeGoal(goalId, request);
    }

    @PostMapping("/goals/{goalId}/archive")
    @Operation(summary = "归档目标")
    public GoalDetail archiveGoal(
            @PathVariable String goalId, @Valid @RequestBody GoalActionRequest request) {
        return goalService.archiveGoal(goalId, request);
    }

    @PostMapping("/goals/{goalId}/criteria/{criterionId}/satisfaction")
    @Operation(summary = "确认或撤销完成标准")
    public GoalDetail setCriterionSatisfaction(
            @PathVariable String goalId,
            @PathVariable String criterionId,
            @Valid @RequestBody CriterionSatisfactionRequest request) {
        return goalService.setCriterionSatisfaction(goalId, criterionId, request);
    }

    @PostMapping("/goals/{goalId}/daily-tasks")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "为目标添加日常任务")
    public GoalDetail createDailyTask(
            @PathVariable String goalId,
            @Valid @RequestBody CreateDailyTaskRequest request) {
        return goalService.createDailyTask(goalId, request);
    }

    @PatchMapping("/daily-tasks/{taskId}")
    @Operation(summary = "编辑日常任务")
    public GoalDetail updateDailyTask(
            @PathVariable String taskId,
            @Valid @RequestBody UpdateDailyTaskRequest request) {
        return goalService.updateDailyTask(taskId, request);
    }

    @PostMapping("/daily-tasks/{taskId}/today")
    @Operation(summary = "记录或撤销今日日常任务")
    public GoalDetail checkinDailyTask(
            @PathVariable String taskId,
            @Valid @RequestBody DailyTaskCheckinRequest request) {
        return goalService.checkinDailyTask(taskId, request);
    }

    @PostMapping("/goals/{goalId}/milestones")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "为目标创建里程碑")
    public GoalDetail createMilestone(
            @PathVariable String goalId,
            @Valid @RequestBody CreateMilestoneRequest request) {
        return goalService.createMilestone(goalId, request);
    }

    @PatchMapping("/milestones/{milestoneId}")
    @Operation(summary = "编辑里程碑")
    public GoalDetail updateMilestone(
            @PathVariable String milestoneId,
            @Valid @RequestBody UpdateMilestoneRequest request) {
        return goalService.updateMilestone(milestoneId, request);
    }

    @PostMapping("/milestones/{milestoneId}/achieve")
    @Operation(summary = "标记里程碑已达成")
    public GoalDetail achieveMilestone(
            @PathVariable String milestoneId,
            @Valid @RequestBody MilestoneActionRequest request) {
        return goalService.achieveMilestone(milestoneId, request);
    }

    @PostMapping("/milestones/{milestoneId}/cancel")
    @Operation(summary = "取消里程碑")
    public GoalDetail cancelMilestone(
            @PathVariable String milestoneId,
            @Valid @RequestBody MilestoneActionRequest request) {
        return goalService.cancelMilestone(milestoneId, request);
    }

    @GetMapping("/goals/{goalId}/revisions")
    @Operation(summary = "查看目标及其里程碑的不可变修改历史")
    public List<RevisionView> getRevisions(@PathVariable String goalId) {
        return goalService.getGoalRevisions(goalId);
    }
}
