package com.reservation.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.info.GitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration
public class SwaggerConfig {

    private final Optional<GitProperties> gitProperties;

    public SwaggerConfig(Optional<GitProperties> gitProperties) {
        this.gitProperties = gitProperties;
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Reservation Payment Platform API")
                        .version(resolveVersion())
                        .description("선착순 예약 결제 플랫폼 API 문서"));
    }

    private String resolveVersion() {
        return gitProperties
                .map(git -> {
                    String version = git.get("build.version");
                    String commit = git.get("commit.id.abbrev");
                    return "%s (%s)".formatted(version, commit);
                })
                .orElse("dev");
    }
}