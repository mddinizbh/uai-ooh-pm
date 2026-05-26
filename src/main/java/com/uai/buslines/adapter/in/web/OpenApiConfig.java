package com.uai.buslines.adapter.in.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Springdoc OpenAPI configuration.
 *
 * <p>Serves the API specification at {@code /api/docs} and Swagger UI at
 * {@code /api/swagger-ui.html} (configured in {@code application.yml}).
 */
@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI busLinesOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("BH Bus Lines API")
                        .description(
                                "Public read API for the BH Bus Lines — Neighborhood Bus Explorer. " +
                                "Returns neighbourhood and bus-line data from the active GTFS dataset. " +
                                "Data source: PBH/BHTRANS open data (CC-BY 4.0).")
                        .version("1.0.0")
                        .license(new License()
                                .name("CC-BY 4.0")
                                .url("https://creativecommons.org/licenses/by/4.0/")));
    }
}
