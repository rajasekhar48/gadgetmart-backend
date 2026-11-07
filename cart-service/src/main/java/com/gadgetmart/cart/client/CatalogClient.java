package com.gadgetmart.cart.client;

import com.gadgetmart.cart.dto.BatchRequest;
import com.gadgetmart.cart.dto.ProductSnapshot;
import com.gadgetmart.cart.exception.CatalogUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Calls Catalog's internal API with timeouts, a retry and a circuit breaker.
 * (Retry wraps the circuit breaker, so a retried call that finds the circuit open fails immediately.)
 */
@Slf4j
@Component
public class CatalogClient {

    private final RestClient rest;

    public CatalogClient(@Value("${app.catalog.base-url}") String baseUrl,
                         @Value("${app.internal.api-key}") String internalKey,
                         @Value("${app.catalog.connect-timeout-ms}") int connectTimeoutMs,
                         @Value("${app.catalog.read-timeout-ms}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.rest = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("X-Internal-Key", internalKey)
                .build();
    }

    /** Unknown listing ids are simply absent from the result. */
    @Retry(name = "catalog")
    @CircuitBreaker(name = "catalog")
    public List<ProductSnapshot> snapshots(Collection<UUID> ids) {
        try {
            List<ProductSnapshot> result = rest.post()
                    .uri("/internal/products/batch")
                    .body(new BatchRequest(List.copyOf(ids)))
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<ProductSnapshot>>() { });
            return result == null ? List.of() : result;
        } catch (ResourceAccessException e) {                  // timeout / connection refused
            throw new CatalogUnavailableException("Catalog unreachable", e);
        } catch (RestClientResponseException e) {              // any non-2xx
            // 401/403 here means a misconfigured internal key: treat as unavailable and make noise
            log.error("Catalog returned {} for /internal/products/batch", e.getStatusCode());
            throw new CatalogUnavailableException("Catalog returned " + e.getStatusCode().value(), e);
        }
    }
}
