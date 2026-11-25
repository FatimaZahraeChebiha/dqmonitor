package com.dataquality.backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Adds the "Authorize" button to Swagger UI (paste an access token obtained
 * from POST /api/auth/login) and basic API metadata - springdoc otherwise only
 * generates a bare, untitled spec from the controllers themselves.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "DQMonitor API",
                version = "1.0",
                description = "Data quality monitoring platform for Enhanced Technologies: "
                        + "dataset upload, Spark + Great Expectations analysis, and quality reporting.",
                contact = @Contact(name = "Enhanced Technologies")
        ),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {
}
