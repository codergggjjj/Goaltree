package com.goalflow.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.goalflow.api.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GoalLinkServiceTests {

    @Autowired GoalLinkService links;
    @Autowired GoalLinkRepository linkRepository;
    @Autowired GoalRepository goalRepository;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    @AfterEach
    void clean() {
        linkRepository.deleteAll();
        goalRepository.deleteAll();
    }

    @Test
    void createsEditsAndDeletesLinksOnlyForTheirGoal() {
        var first = goalRepository.save(goal("第一主目标"));
        var second = goalRepository.save(goal("第二主目标"));

        var added = links.create(first.id, new GoalLinkApiModels.LinkInput("学习资料", "https://example.com/learn"));
        links.create(first.id, new GoalLinkApiModels.LinkInput("项目仓库", "https://example.com/repo"));
        assertThat(links.list(first.id)).extracting(GoalLinkApiModels.LinkView::name)
                .containsExactlyInAnyOrder("学习资料", "项目仓库");
        assertThat(links.list(second.id)).isEmpty();
        assertThatThrownBy(() -> links.update(second.id, added.id(),
                new GoalLinkApiModels.LinkInput("越界", "https://example.com/other")))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo("LINK_NOT_FOUND"));

        var edited = links.update(first.id, added.id(), new GoalLinkApiModels.LinkInput("课程页面", "https://example.com/course"));
        assertThat(edited.name()).isEqualTo("课程页面");
        assertThat(links.list(first.id)).hasSize(2);
        links.delete(first.id, added.id());
        assertThat(links.list(first.id)).extracting(GoalLinkApiModels.LinkView::name).containsExactly("项目仓库");
    }

    @Test
    void rejectsUnsafeUrlsAndLinksOnDeletedGoals() {
        var goal = goalRepository.save(goal("主目标"));
        assertThatThrownBy(() -> links.create(goal.id, new GoalLinkApiModels.LinkInput("错误", "javascript:alert(1)")))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo("INVALID_LINK_URL"));
        assertThat(links.list(goal.id)).isEmpty();
        goal.deletedAt = java.time.Instant.now();
        goalRepository.save(goal);
        assertThatThrownBy(() -> links.list(goal.id)).isInstanceOf(ApiException.class);
    }

    @Test
    void exposesLinkCrudThroughGoalScopedApi() throws Exception {
        var goal = goalRepository.save(goal("主目标"));
        var base = "/api/goals/" + goal.id + "/links";
        var created = mockMvc.perform(post(base).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"项目仓库\",\"url\":\"https://example.com/repo\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("项目仓库"))
                .andReturn();
        var linkId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(get(base)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(linkId));
        mockMvc.perform(post(base).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"危险链接\",\"url\":\"javascript:alert(1)\"}"))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(patch(base + "/" + linkId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"课程页面\",\"url\":\"https://example.com/course\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("课程页面"));
        mockMvc.perform(delete(base + "/" + linkId)).andExpect(status().isNoContent());
        mockMvc.perform(get(base)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    private GoalEntity goal(String title) {
        var goal = GoalEntity.create(GoalService.DEFAULT_USER_ID);
        goal.title = title;
        goal.startDate = java.time.LocalDate.of(2026, 1, 1);
        goal.targetDate = java.time.LocalDate.of(2046, 1, 1);
        goal.priority = GoalPriority.MEDIUM;
        goal.status = GoalStatus.ACTIVE;
        goal.progressMode = ProgressMode.MANUAL;
        goal.currentProgress = java.math.BigDecimal.ZERO;
        return goal;
    }
}
