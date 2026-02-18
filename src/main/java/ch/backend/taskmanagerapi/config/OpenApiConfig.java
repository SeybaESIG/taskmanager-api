package ch.backend.taskmanagerapi.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// OpenAPI/Swagger metadata and JWT bearer authentication documentation.
@Configuration
public class OpenApiConfig {

    // Exposes API metadata and global bearer auth scheme in generated docs.
    @Bean
    public OpenAPI taskManagerOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("TaskManager API")
                        .description("Secure REST API for project and task management with JWT authentication.")
                        .version("v1")
                        .contact(new Contact().name("TaskManager API"))
                        .license(new License().name("Apache-2.0")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(
                                securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                        ));
    }
}
