package com.goalflow.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String frontendUrl;

    public WebConfig(@Value("${app.frontend-url}") String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedFrontendOrigins())
                .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("ETag", "Location")
                .allowCredentials(true)
                .maxAge(3600);
    }

    private String[] allowedFrontendOrigins() {
        var uri = URI.create(frontendUrl);
        var alias = switch (uri.getHost()) {
            case "localhost" -> "127.0.0.1";
            case "127.0.0.1" -> "localhost";
            default -> null;
        };
        if (alias == null) {
            return new String[] {frontendUrl};
        }
        var aliasOrigin = uri.getScheme() + "://" + alias
                + (uri.getPort() < 0 ? "" : ":" + uri.getPort());
        return new String[] {frontendUrl, aliasOrigin};
    }
}
