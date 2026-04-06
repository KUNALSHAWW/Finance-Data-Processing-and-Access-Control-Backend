package com.kunal.finance.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class SwaggerConfig {

        @Bean
        public OpenAPI openAPI() {
                return new OpenAPI()
                                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
                                .components(new Components().addSecuritySchemes("BearerAuth", new SecurityScheme()
                                                .name("Authorization")
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")))
                                .info(new Info().title("Finance Data Processing and Access Control Backend API")
                                                .description("Backend system for financial data processing with role-based access control")
                                                .version("1.0"));
        }
}