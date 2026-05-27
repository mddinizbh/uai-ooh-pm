package com.uai.buslines.adapter.out.boundary;

import com.uai.buslines.application.GeoJsonBoundaryParser;
import com.uai.buslines.domain.model.BoundaryParseException;
import com.uai.buslines.domain.model.Neighborhood;
import com.uai.buslines.domain.port.out.BoundaryGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * HTTP adapter that implements {@link BoundaryGateway}.
 *
 * <p>Downloads the PBH neighbourhood boundary GeoJSON file from the configured
 * URL and delegates parsing + CRS validation to {@link GeoJsonBoundaryParser}.
 *
 * <p>Package-private; exposed as {@link BoundaryGateway} through
 * {@link BoundaryAdapterConfiguration}.
 */
class HttpBoundaryGateway implements BoundaryGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpBoundaryGateway.class);

    /** Identifies the app to data-provider WAFs (ckan.pbh.gov.br rejects the default Java UA). */
    static final String USER_AGENT = "uai-buslines/1.0 (+https://linhas.uaiagencia.com.br)";

    private final BoundaryProperties props;
    private final GeoJsonBoundaryParser parser;
    private final RawBoundaryClient httpClient;

    /**
     * Production constructor — wires a real {@link java.net.http.HttpClient}.
     */
    HttpBoundaryGateway(BoundaryProperties props, GeoJsonBoundaryParser parser) {
        this.props  = props;
        this.parser = parser;
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(props.requestTimeoutSeconds()))
                .build();
        this.httpClient = url -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(props.requestTimeoutSeconds()))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
            HttpResponse<byte[]> resp =
                    client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200) {
                throw new BoundaryParseException(
                        "Boundary download failed: HTTP " + resp.statusCode()
                        + " from " + url);
            }
            return resp.body();
        };
    }

    /**
     * Test constructor — injects a mock {@link RawBoundaryClient} to avoid a live
     * HTTP server. Follows the same pattern as {@code HttpGtfsFeedGateway}.
     */
    HttpBoundaryGateway(BoundaryProperties props, GeoJsonBoundaryParser parser,
                         RawBoundaryClient httpClient) {
        this.props      = props;
        this.parser     = parser;
        this.httpClient = httpClient;
    }

    // ── BoundaryGateway ─────────────────────────────────────────────────────────

    @Override
    public List<Neighborhood> loadNeighborhoods() {
        log.info("Downloading neighbourhood boundaries from: {}", props.url());
        byte[] bytes;
        try {
            bytes = httpClient.get(props.url());
        } catch (BoundaryParseException e) {
            throw e;
        } catch (IOException e) {
            throw new BoundaryParseException(
                    "I/O error downloading boundary file: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BoundaryParseException("Boundary download interrupted", e);
        }
        List<Neighborhood> neighborhoods = parser.parse(bytes);
        log.info("Loaded {} neighbourhood boundaries", neighborhoods.size());
        return neighborhoods;
    }
}
