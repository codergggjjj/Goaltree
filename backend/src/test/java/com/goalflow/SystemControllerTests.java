package com.goalflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.goalflow.system.SystemController;
import org.junit.jupiter.api.Test;

class SystemControllerTests {

    @Test
    void healthDescribesTheRunningApplication() {
        var response = new SystemController().health();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("UP");
        assertThat(response.getBody().application()).isEqualTo("Goaltree");
    }
}
