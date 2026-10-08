package com.technova.campusdesk.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI: http://localhost:8080/swagger-ui.html. Log in, then press "Authorize" and paste the token. */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI campusDeskOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CampusDesk API")
                        .version("1.0")
                        .description("IT incident management for TechNova Solutions. "
                                + "Call POST /api/auth/login, copy the token and press Authorize."))
                .components(new Components().addSecuritySchemes(BEARER_AUTH,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the JWT without the 'Bearer ' prefix.")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
