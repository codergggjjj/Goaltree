package com.goalflow.goal;

import com.goalflow.api.ApiException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoalLinkService {
    private final GoalRepository goals;
    private final GoalLinkRepository links;

    public GoalLinkService(GoalRepository goals, GoalLinkRepository links) {
        this.goals = goals;
        this.links = links;
    }

    @Transactional(readOnly = true)
    public List<GoalLinkApiModels.LinkView> list(String goalId) {
        requireGoal(goalId);
        return links.findByGoalIdOrderByCreatedAtAsc(goalId).stream().map(this::view).toList();
    }

    @Transactional
    public GoalLinkApiModels.LinkView create(String goalId, GoalLinkApiModels.LinkInput input) {
        requireGoal(goalId);
        var link = GoalLinkEntity.create(goalId);
        assign(link, input);
        return view(links.save(link));
    }

    @Transactional
    public GoalLinkApiModels.LinkView update(String goalId, String linkId, GoalLinkApiModels.LinkInput input) {
        requireGoal(goalId);
        var link = requireLink(goalId, linkId);
        assign(link, input);
        link.touch();
        return view(links.save(link));
    }

    @Transactional
    public void delete(String goalId, String linkId) {
        requireGoal(goalId);
        links.delete(requireLink(goalId, linkId));
    }

    private void requireGoal(String goalId) {
        goals.findByIdAndUserIdAndDeletedAtIsNull(goalId, GoalService.DEFAULT_USER_ID)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GOAL_NOT_FOUND", "目标不存在。"));
    }

    private GoalLinkEntity requireLink(String goalId, String linkId) {
        return links.findByIdAndGoalId(linkId, goalId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LINK_NOT_FOUND", "链接不存在。"));
    }

    private void assign(GoalLinkEntity link, GoalLinkApiModels.LinkInput input) {
        if (input.name() == null || input.name().isBlank() || input.name().trim().length() > 120) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_LINK_NAME", "请输入链接名称。");
        }
        link.name = input.name().trim();
        link.url = validUrl(input.url());
    }

    private String validUrl(String value) {
        if (value == null) throw invalidUrl();
        var url = value.trim();
        if (url.length() > 2048) throw invalidUrl();
        try {
            var uri = URI.create(url);
            var scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if ((scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null
                    && !uri.getHost().isBlank()) return url;
        } catch (IllegalArgumentException ignored) {
            // Return the same field-level message for malformed URLs.
        }
        throw invalidUrl();
    }

    private ApiException invalidUrl() {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_LINK_URL", "请输入有效的 http 或 https 链接。");
    }

    private GoalLinkApiModels.LinkView view(GoalLinkEntity link) {
        return new GoalLinkApiModels.LinkView(link.id, link.name, link.url);
    }
}
