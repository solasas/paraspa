package com.sashank.paraspa.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI at /swagger-ui.html. Use "Authorize" and paste the access token to try the protected endpoints. */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI paraspaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("paraspa API")
                        .version("v1")
                        .description("Skill exchange and social API. Log in at POST /api/auth/login, then send the access token as "
                                + "'Authorization: Bearer <token>'. Access tokens last 15 minutes; renew them with POST /api/auth/refresh."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
