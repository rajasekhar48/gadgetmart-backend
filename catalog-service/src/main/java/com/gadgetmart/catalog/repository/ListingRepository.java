package com.gadgetmart.catalog.repository;

import com.gadgetmart.catalog.domain.Listing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface ListingRepository extends JpaRepository<Listing, UUID>, JpaSpecificationExecutor<Listing> {

    /** Fetch brand + category in the same query so listing pages do not trigger N+1 selects. */
    @Override
    @EntityGraph(attributePaths = {"brand", "category"})
    Page<Listing> findAll(Specification<Listing> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"brand", "category"})
    Optional<Listing> findDetailById(UUID id);
}
