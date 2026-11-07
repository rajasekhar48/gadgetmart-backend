package com.gadgetmart.catalog.dto;

import com.gadgetmart.catalog.domain.ConditionGrade;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Query parameters of GET /api/products. Every field is optional.
 * Money is in minor units (paise): minPricePaise=2000000 means Rs 20,000.
 */
public record ProductSearchParams(
        @Size(max = 100) String q,                       // free text over title / model / brand
        String category,                                 // category slug, e.g. smartphones
        List<String> brand,                              // brand slugs, repeatable: brand=apple&brand=samsung
        List<ConditionGrade> grade,                      // A, B, C (repeatable)
        @Min(0) Long minPricePaise,
        @Min(0) Long maxPricePaise,
        @Size(max = 80) String city,
        @Min(0) @Max(100) Integer minBatteryHealth,
        @Min(0) Integer minWarrantyMonths,
        Boolean inStock,                                 // default true: hide sold-out items
        @Min(0) Integer page,                            // 0-based
        @Min(1) @Max(50) Integer size,                   // default 12
        String sort                                      // newest (default) | price_asc | price_desc
) {
    public int pageOrDefault() {
        return page == null ? 0 : page;
    }

    public int sizeOrDefault() {
        return size == null ? 12 : size;
    }

    public boolean inStockOrDefault() {
        return inStock == null || inStock;
    }
}
