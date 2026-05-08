package com.laroka.backend.shared.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

	@Bean
	@ConditionalOnProperty(name = "swagger.enabled", havingValue = "true", matchIfMissing = true)
	public OpenAPI larokaOpenAPI() {
		return new OpenAPI()
			.info(new Info()
				.title("Laroka API")
				.description("Order Management System API")
				.version("1.0.0"));
	}
}
