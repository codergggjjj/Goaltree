package com.goalflow.system;

import io.swagger.v3.oas.annotations.Operation;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    @GetMapping("/health")
    @Operation(summary = "获取前后端连通状态")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(new HealthResponse("UP", "Goaltree", "0.2.0", Instant.now()));
    }

    public record HealthResponse(String status, String application, String version, Instant checkedAt) {
    }
}
