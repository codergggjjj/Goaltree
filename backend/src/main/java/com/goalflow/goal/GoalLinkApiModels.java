package com.goalflow.goal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class GoalLinkApiModels {
    private GoalLinkApiModels() {
    }

    public record LinkInput(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 2048) String url) {
    }

    public record LinkView(String id, String name, String url) {
    }
}
