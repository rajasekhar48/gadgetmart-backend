package com.gadgetmart.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Identity propagation. After the JWT is verified, copies the identity into trusted headers
 * (X-User-Id, X-User-Roles) for downstream services. Any X-User-* headers sent by the client
 * are ALWAYS stripped first, so they can never be spoofed.
 * The original Authorization header is still forwarded.
 */
@Component
public class UserContextFilter implements GlobalFilter, Ordered {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_ROLES = "X-User-Roles";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .flatMap(ctx -> Mono.justOrEmpty(ctx.getAuthentication()))
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(JwtAuthenticationToken.class::cast)
                .map(auth -> withIdentity(exchange, auth.getToken()))
                .defaultIfEmpty(withoutIdentity(exchange))
                .flatMap(chain::filter);
    }

    private ServerWebExchange withIdentity(ServerWebExchange exchange, Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        var request = exchange.getRequest().mutate().headers(h -> {
            h.remove(USER_ID);
            h.remove(USER_ROLES);
            h.set(USER_ID, jwt.getSubject());
            h.set(USER_ROLES, roles == null ? "" : String.join(",", roles));
        }).build();
        return exchange.mutate().request(request).build();
    }

    private ServerWebExchange withoutIdentity(ServerWebExchange exchange) {
        var request = exchange.getRequest().mutate().headers(h -> {
            h.remove(USER_ID);
            h.remove(USER_ROLES);
        }).build();
        return exchange.mutate().request(request).build();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
