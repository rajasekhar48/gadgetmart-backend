package com.gadgetmart.user.controller;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Publishes the PUBLIC key so the gateway (and later services) can verify tokens without sharing secrets. */
@RestController
public class JwksController {

    private final RSAKey publicJwk;

    public JwksController(RSAKey rsaKey) {
        this.publicJwk = rsaKey.toPublicJWK();
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> keys() {
        return new JWKSet(publicJwk).toJSONObject();
    }
}
