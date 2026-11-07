package com.gadgetmart.catalog.controller;

import com.gadgetmart.catalog.dto.BatchRequest;
import com.gadgetmart.catalog.dto.ProductSnapshot;
import com.gadgetmart.catalog.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Service-to-service (Cart, Order). Not routed by the gateway; needs X-Internal-Key. */
@RestController
@RequestMapping("/internal/products")
@RequiredArgsConstructor
public class InternalProductController {

    private final ProductService productService;

    @PostMapping("/batch")
    public List<ProductSnapshot> batch(@Valid @RequestBody BatchRequest request) {
        return productService.snapshots(request.ids());
    }
}
