package com.gadgetmart.catalog.dto;

import com.gadgetmart.catalog.domain.ConditionGrade;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Full product page. */
public record ProductDetail(
        UUID id,
        String title,
        String description,
        NamedRef brand,
        NamedRef category,
        String modelName,
        ConditionGrade conditionGrade,
        String conditionLabel,
        long pricePaise,
        Long mrpPaise,
        int discountPercent,
        String currency,
        Integer batteryHealthPct,
        int warrantyMonths,
        Map<String, Object> attributes,
        List<String> images,
        String city,
        String sellerName,
        boolean inStock,
        int availableQty,
        Instant listedAt
) {
}
