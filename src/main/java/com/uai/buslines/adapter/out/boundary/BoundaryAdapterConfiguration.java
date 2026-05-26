package com.uai.buslines.adapter.out.boundary;

import com.uai.buslines.application.GeoJsonBoundaryParser;
import com.uai.buslines.domain.port.out.BoundaryGateway;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the neighbourhood boundary adapter beans.
 *
 * <p>Declares {@link BoundaryGateway} as a bean backed by {@link HttpBoundaryGateway}.
 * Configuration properties are bound via {@link BoundaryProperties}.
 */
@Configuration
@EnableConfigurationProperties(BoundaryProperties.class)
class BoundaryAdapterConfiguration {

    @Bean
    BoundaryGateway boundaryGateway(BoundaryProperties props, GeoJsonBoundaryParser parser) {
        return new HttpBoundaryGateway(props, parser);
    }
}
