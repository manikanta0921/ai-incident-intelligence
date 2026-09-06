package com.example.incident.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI is served at /swagger-ui.html (redirects from /swagger-ui/index.html).
 * The "Authorize" button accepts a JWT and sends it as "Authorization: Bearer <token>"
 * on every subsequent request made from Swagger - so secured endpoints can be tested
 * directly in the browser.
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI incidentOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI Incident Intelligence API")
                        .description("""
                                Incident management backend. In later phases AI will classify incidents, \
                                suggest severity, summarize them and recommend resolutions.\
                                
                                1. POST /api/auth/register  - create an account\
                                2. POST /api/auth/login     - get accessToken + refreshToken\
                                3. Click "Authorize" below  - paste the accessToken\
                                4. Call the incident endpoints\
                                """)
                        .version("v0.1.0")
                        .contact(new Contact().name("API Support").email("support@example.com")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME,
                        new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the accessToken from /api/auth/login (without the 'Bearer ' prefix)")));
    }
}
