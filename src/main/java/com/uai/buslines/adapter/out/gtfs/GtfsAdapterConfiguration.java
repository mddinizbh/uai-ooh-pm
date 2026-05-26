package com.uai.buslines.adapter.out.gtfs;

import com.uai.buslines.domain.port.out.GtfsFeedGateway;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for the GTFS adapter beans.
 *
 * <p>Declares {@link GtfsFeedGateway} as a bean backed by {@link HttpGtfsFeedGateway}.
 * Configuration properties are bound via {@link GtfsFeedProperties}.
 */
@Configuration
@EnableConfigurationProperties(GtfsFeedProperties.class)
class GtfsAdapterConfiguration {

    @Bean
    GtfsFeedGateway gtfsFeedGateway(GtfsFeedProperties props) {
        return new HttpGtfsFeedGateway(props);
    }
}
