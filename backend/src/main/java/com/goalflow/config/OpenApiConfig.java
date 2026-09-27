package com.goalflow.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Goalflow API",
                version = "v1",
                description = "个人长期目标规划与进度管理系统接口"
        )
)
public class OpenApiConfig {
}
