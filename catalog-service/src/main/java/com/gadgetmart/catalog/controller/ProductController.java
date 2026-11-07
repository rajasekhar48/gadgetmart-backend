package com.gadgetmart.catalog.controller;

import com.gadgetmart.catalog.dto.PageResponse;
import com.gadgetmart.catalog.dto.ProductDetail;
import com.gadgetmart.catalog.dto.ProductSearchParams;
import com.gadgetmart.catalog.dto.ProductSummary;
import com.gadgetmart.catalog.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Public buyer-facing browsing. */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * GET /api/products?q=iphone&category=smartphones&brand=apple&grade=A&grade=B
     *     &minPricePaise=1000000&maxPricePaise=4000000&city=mumbai&minBatteryHealth=85
     *     &minWarrantyMonths=6&inStock=true&sort=price_asc&page=0&size=12
     */
    @GetMapping
    public PageResponse<ProductSummary> search(@Valid ProductSearchParams params) {
        return productService.search(params);
    }

    @GetMapping("/{id}")
    public ProductDetail get(@PathVariable UUID id) {
        return productService.get(id);
    }
}
