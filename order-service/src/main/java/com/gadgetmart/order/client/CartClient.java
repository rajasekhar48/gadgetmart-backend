package com.gadgetmart.order.client;

import com.gadgetmart.order.dto.CartSnapshot;
import com.gadgetmart.order.exception.DownstreamUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

/** Order -> Cart. Both calls are idempotent, so retrying is safe. */
@Component
public class CartClient {

    private final RestClient rest;

    public CartClient(@Value("${app.cart.base-url}") String baseUrl,
                      @Value("${app.internal.api-key}") String internalKey,
                      @Value("${app.http.connect-timeout-ms}") int connectMs,
                      @Value("${app.http.read-timeout-ms}") int readMs) {
        this.rest = RestClients.build(baseUrl, internalKey, connectMs, readMs);
    }

    /** The cart with live, validated prices and stock. */
    @Retry(name = "cart")
    @CircuitBreaker(name = "cart")
    public CartSnapshot getCart(UUID userId) {
        try {
            CartSnapshot cart = rest.get().uri("/internal/carts/{id}", userId).retrieve().body(CartSnapshot.class);
            if (cart == null) {
                throw new DownstreamUnavailableException("Cart returned an empty body");
            }
            return cart;
        } catch (ResourceAccessException e) {
            throw new DownstreamUnavailableException("Cart unreachable", e);
        } catch (RestClientResponseException e) {
            throw new DownstreamUnavailableException("Cart returned " + e.getStatusCode().value(), e);
        }
    }

    @Retry(name = "cart")
    @CircuitBreaker(name = "cart")
    public void clearCart(UUID userId) {
        try {
            rest.delete().uri("/internal/carts/{id}", userId).retrieve().toBodilessEntity();
        } catch (ResourceAccessException e) {
            throw new DownstreamUnavailableException("Cart unreachable", e);
        } catch (RestClientResponseException e) {
            throw new DownstreamUnavailableException("Cart returned " + e.getStatusCode().value(), e);
        }
    }
}
