package com.gadgetmart.catalog.controller;

import com.gadgetmart.catalog.dto.NamedRef;
import com.gadgetmart.catalog.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/brands")
@RequiredArgsConstructor
public class BrandController {

    private final ProductService productService;

    @GetMapping
    public List<NamedRef> list() {
        return productService.brands();
    }
}
