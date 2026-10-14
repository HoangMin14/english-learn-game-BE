package com.aws.alphabetuniverse.config;


import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class OpenApiConfig {


    @Bean
    public OpenAPI customOpenAPI() {

        return new OpenAPI()
                // Khai báo kiểu Authentication
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        "Bearer Authentication",
                                        new SecurityScheme()
                                                .name("Authorization")
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")
                                )
                );
    }
}
