# Catalog Service (buyer side)

Browse, search and filter second-hand gadgets, plus stock reservation for checkout.
Port **8082**, database `catalog_db` (Postgres, port 5433 locally).

## Public APIs (through the gateway, no login needed)
| Method | Path | Notes |
|---|---|---|
| GET | `/api/categories` | `[{slug, name}]` |
| GET | `/api/brands` | `[{slug, name}]` |
| GET | `/api/products` | search + filters + sort + paging (below) |
| GET | `/api/products/{id}` | full detail: specs (JSON), images, battery, warranty, stock |

### Search parameters (all optional)
`q` free text · `category` slug · `brand` slug (repeat) · `grade` A/B/C (repeat) ·
`minPricePaise` / `maxPricePaise` (paise: 2000000 = Rs 20,000) · `city` · `minBatteryHealth` ·
`minWarrantyMonths` · `inStock` (default true) · `sort` = newest | price_asc | price_desc · `page` (0-based) · `size` (1-50, default 12)

## Internal APIs (service-to-service, header `X-Internal-Key`; blocked at the gateway)
| Method | Path | Used by |
|---|---|---|
| POST | `/internal/products/batch` `{ids:[...]}` | Cart / Order: current price, active, stock |
| POST | `/internal/reservations` `{orderId, items:[{listingId, quantity}]}` | Order: hold stock (all-or-nothing, idempotent per orderId, 15 min TTL) |
| GET | `/internal/reservations/{orderId}` | Order |
| POST | `/internal/reservations/{orderId}/commit` | Order, after payment: held units become sold |
| POST | `/internal/reservations/{orderId}/release` | Order, on cancel / payment failure |

Reservation lifecycle: `HELD -> COMMITTED | RELEASED | EXPIRED`. A scheduled job expires unpaid holds.
Out of stock gives **409** with `unavailableItems: [{listingId, requested, available}]`.

## Try it
```bash
curl 'localhost:8080/api/products?q=iphone&grade=A&sort=price_asc'
curl 'localhost:8080/api/products?category=laptops&minPricePaise=3000000&maxPricePaise=6000000'
curl 'localhost:8080/api/products/00000000-0000-4000-8000-000000000001'

# reservation (directly against the service, never via the gateway)
ORDER=$(uuidgen)
curl -X POST localhost:8082/internal/reservations -H 'Content-Type: application/json' \
  -H 'X-Internal-Key: dev-internal-key-change-me' \
  -d "{\"orderId\":\"$ORDER\",\"items\":[{\"listingId\":\"00000000-0000-4000-8000-000000000001\",\"quantity\":2}]}"
curl -X POST localhost:8082/internal/reservations/$ORDER/commit -H 'X-Internal-Key: dev-internal-key-change-me'
```
Seed data (33 listings, one sold out: Pixel 7 Pro, id ...0009) lives in `db/seed` and is dev-only:
set `FLYWAY_LOCATIONS=classpath:db/migration` in real environments.
