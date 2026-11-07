
package com.gadgetmart.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class RequestIdFilter implements GlobalFilter, Ordered {

    public static final String HEADER = "X-Request-Id";

    private static final Pattern SAFE =
            Pattern.compile("[A-Za-z0-9-]{1,64}");

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            GatewayFilterChain chain) {

        String incoming = exchange.getRequest()
                .getHeaders()
                .getFirst(HEADER);

        String id = (incoming != null
                && SAFE.matcher(incoming).matches())
                ? incoming
                : UUID.randomUUID().toString();

        // Copy existing headers into a new, writable HttpHeaders object
        HttpHeaders writableHeaders = new HttpHeaders();
        writableHeaders.putAll(exchange.getRequest().getHeaders());

        // Replace the request ID
        writableHeaders.set(HEADER, id);

        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .headers(headers -> {
                    // Do not modify headers here
                })
                .build();

        request = new ServerHttpRequestDecorator(request) {
            @Override
            public HttpHeaders getHeaders() {
                return HttpHeaders.readOnlyHttpHeaders(writableHeaders);
            }
        };

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(request)
                .build();

        mutatedExchange.getResponse()
                .getHeaders()
                .set(HEADER, id);

        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
