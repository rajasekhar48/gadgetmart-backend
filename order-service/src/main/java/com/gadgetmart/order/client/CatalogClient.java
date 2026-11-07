package com.gadgetmart.order.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gadgetmart.order.dto.ReserveItem;
import com.gadgetmart.order.dto.ReserveRequest;
import com.gadgetmart.order.dto.UnavailableItem;
import com.gadgetmart.order.exception.DownstreamUnavailableException;
import com.gadgetmart.order.exception.ReservationLostException;
import com.gadgetmart.order.exception.StockUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.UUID;

/**
 * Order -> Catalog stock reservation. reserve / commit / release are all idempotent per orderId on the
 * Catalog side, which is what makes retries (and "did my timed-out call actually succeed?") safe.
 */
@Slf4j
@Component
public class CatalogClient {

    private final RestClient rest;
    private final ObjectMapper mapper;

    public CatalogClient(@Value("${app.catalog.base-url}") String baseUrl,
                         @Value("${app.internal.api-key}") String internalKey,
                         @Value("${app.http.connect-timeout-ms}") int connectMs,
                         @Value("${app.http.read-timeout-ms}") int readMs,
                         ObjectMapper mapper) {
        this.rest = RestClients.build(baseUrl, internalKey, connectMs, readMs);
        this.mapper = mapper;
    }

    /** Holds the stock for 15 minutes, all-or-nothing. Throws StockUnavailableException on a 409. */
    @Retry(name = "catalog")
    @CircuitBreaker(name = "catalog")
    public void reserve(UUID orderId, List<ReserveItem> items) {
        try {
            rest.post().uri("/internal/reservations")
                    .body(new ReserveRequest(orderId, items))
                    .retrieve().toBodilessEntity();
        } catch (ResourceAccessException e) {
            throw new DownstreamUnavailableException("Catalog unreachable", e);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 409) {
                throw new StockUnavailableException(parseUnavailable(e));
            }
            throw new DownstreamUnavailableException("Catalog returned " + e.getStatusCode().value(), e);
        }
    }

    /** Payment captured: held units become sold. 409/404 mean the hold is gone. */
    @Retry(name = "catalog")
    @CircuitBreaker(name = "catalog")
    public void commit(UUID orderId) {
        try {
            rest.post().uri("/internal/reservations/{id}/commit", orderId).retrieve().toBodilessEntity();
        } catch (ResourceAccessException e) {
            throw new DownstreamUnavailableException("Catalog unreachable", e);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 409 || status == 404) {
                throw new ReservationLostException();
            }
            throw new DownstreamUnavailableException("Catalog returned " + status, e);
        }
    }

    /** Gives the stock back. Already released / unknown / already sold are all fine (nothing to undo). */
    @Retry(name = "catalog")
    @CircuitBreaker(name = "catalog")
    public void release(UUID orderId) {
        try {
            rest.post().uri("/internal/reservations/{id}/release", orderId).retrieve().toBodilessEntity();
        } catch (ResourceAccessException e) {
            throw new DownstreamUnavailableException("Catalog unreachable", e);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 409 || status == 404) {
                log.info("Release of {} was a no-op (catalog answered {})", orderId, status);
                return;
            }
            throw new DownstreamUnavailableException("Catalog returned " + status, e);
        }
    }

    private List<UnavailableItem> parseUnavailable(RestClientResponseException e) {
        try {
            JsonNode node = mapper.readTree(e.getResponseBodyAsString()).path("unavailableItems");
            return mapper.convertValue(node, new TypeReference<List<UnavailableItem>>() { });
        } catch (Exception parseError) {
            log.warn("Could not parse Catalog's 409 body: {}", parseError.toString());
            return List.of();
        }
    }
}
