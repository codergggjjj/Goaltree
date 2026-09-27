package com.goalflow.goal;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/goals/{goalId}/links")
public class GoalLinkController {
    private final GoalLinkService links;

    public GoalLinkController(GoalLinkService links) {
        this.links = links;
    }

    @GetMapping
    @Operation(summary = "获取目标关联链接")
    public List<GoalLinkApiModels.LinkView> list(@PathVariable String goalId) {
        return links.list(goalId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "添加目标关联链接")
    public GoalLinkApiModels.LinkView create(@PathVariable String goalId,
            @Valid @RequestBody GoalLinkApiModels.LinkInput input) {
        return links.create(goalId, input);
    }

    @PatchMapping("/{linkId}")
    @Operation(summary = "编辑目标关联链接")
    public GoalLinkApiModels.LinkView update(@PathVariable String goalId, @PathVariable String linkId,
            @Valid @RequestBody GoalLinkApiModels.LinkInput input) {
        return links.update(goalId, linkId, input);
    }

    @DeleteMapping("/{linkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "删除目标关联链接")
    public void delete(@PathVariable String goalId, @PathVariable String linkId) {
        links.delete(goalId, linkId);
    }
}
