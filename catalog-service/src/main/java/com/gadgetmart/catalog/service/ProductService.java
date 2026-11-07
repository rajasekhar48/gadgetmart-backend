package com.gadgetmart.catalog.service;

import com.gadgetmart.catalog.domain.Brand;
import com.gadgetmart.catalog.domain.Category;
import com.gadgetmart.catalog.domain.Listing;
import com.gadgetmart.catalog.domain.ListingStatus;
import com.gadgetmart.catalog.domain.Stock;
import com.gadgetmart.catalog.dto.NamedRef;
import com.gadgetmart.catalog.dto.PageResponse;
import com.gadgetmart.catalog.dto.ProductDetail;
import com.gadgetmart.catalog.dto.ProductSearchParams;
import com.gadgetmart.catalog.dto.ProductSnapshot;
import com.gadgetmart.catalog.dto.ProductSummary;
import com.gadgetmart.catalog.exception.ApiException;
import com.gadgetmart.catalog.repository.BrandRepository;
import com.gadgetmart.catalog.repository.CategoryRepository;
import com.gadgetmart.catalog.repository.ListingRepository;
import com.gadgetmart.catalog.repository.StockRepository;
import com.gadgetmart.catalog.spec.ListingSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ListingRepository listings;
    private final StockRepository stocks;
    private final CategoryRepository categories;
    private final BrandRepository brands;

    public PageResponse<ProductSummary> search(ProductSearchParams params) {
        if (params.minPricePaise() != null && params.maxPricePaise() != null
                && params.minPricePaise() > params.maxPricePaise()) {
            throw ApiException.badRequest("minPricePaise cannot be greater than maxPricePaise");
        }
        Pageable pageable = PageRequest.of(params.pageOrDefault(), params.sizeOrDefault(), sortOf(params.sort()));
        Page<Listing> page = listings.findAll(ListingSpecifications.from(params), pageable);

        // one extra query for the whole page (instead of one per row)
        Map<UUID, Integer> available = availability(page.getContent().stream().map(Listing::getId).toList());
        return PageResponse.from(page.map(l -> ProductMapper.toSummary(l, available.getOrDefault(l.getId(), 0))));
    }

    public ProductDetail get(UUID id) {
        Listing listing = listings.findDetailById(id)
                .filter(l -> l.getStatus() == ListingStatus.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
        int available = stocks.findById(id).map(Stock::getAvailableQty).orElse(0);
        return ProductMapper.toDetail(listing, available);
    }

    /** Used by Cart / Order through /internal. Unknown ids are simply absent from the result. */
    public List<ProductSnapshot> snapshots(Collection<UUID> ids) {
        Map<UUID, Integer> available = availability(ids);
        return listings.findAllById(ids).stream()
                .map(l -> ProductMapper.toSnapshot(l, available.getOrDefault(l.getId(), 0)))
                .toList();
    }

    public List<NamedRef> categories() {
        return categories.findAllByOrderByDisplayOrderAscNameAsc().stream()
                .map((Category c) -> new NamedRef(c.getSlug(), c.getName()))
                .toList();
    }

    public List<NamedRef> brands() {
        return brands.findAllByOrderByNameAsc().stream()
                .map((Brand b) -> new NamedRef(b.getSlug(), b.getName()))
                .toList();
    }

    private Map<UUID, Integer> availability(Collection<UUID> ids) {
        return stocks.findAllById(ids).stream()
                .collect(Collectors.toMap(Stock::getListingId, Stock::getAvailableQty, Integer::sum, java.util.HashMap::new));
    }

    /** Whitelist of sort keys; "id" is a tiebreaker so pagination is stable. */
    private Sort sortOf(String key) {
        String k = key == null || key.isBlank() ? "newest" : key.trim().toLowerCase();
        Sort tie = Sort.by("id");
        return switch (k) {
            case "newest" -> Sort.by(Sort.Direction.DESC, "createdAt").and(tie);
            case "price_asc" -> Sort.by(Sort.Direction.ASC, "priceMinor").and(tie);
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "priceMinor").and(tie);
            default -> throw ApiException.badRequest("sort must be one of: newest, price_asc, price_desc");
        };
    }
}
