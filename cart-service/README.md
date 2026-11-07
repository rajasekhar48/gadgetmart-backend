# Cart Service (buyer side)

The logged-in buyer's shopping cart. Port **8083**, database `cart_db` (Postgres, port 5434 locally) + Redis cache.

## Design in one paragraph
The cart stores only **listing id + quantity** (plus the price the buyer last saw). Titles, live prices and stock
are fetched from Catalog every time the cart is viewed, so a stale price can never reach checkout. Adding to the
cart does **not** reserve stock: reservation happens at checkout in Order. Postgres is the source of truth; Redis
caches the cart lines (10 min TTL, evicted after each committed write). If Redis is down the cart keeps working
from Postgres. If Catalog is down, calls fail fast with 503 (timeouts, retry, circuit breaker).

## Buyer APIs (through the gateway, JWT required)
| Method | Path | Notes |
|---|---|---|
| GET | `/api/cart` | cart with live prices, stock, per-line `issues`, `subtotalPaise`, `checkoutReady` |
| GET | `/api/cart/count` | `{itemCount, lineCount}` for a badge; no Catalog call |
| POST | `/api/cart/items` `{listingId, quantity}` | adds to existing quantity |
| PUT | `/api/cart/items/{listingId}` `{quantity}` | sets quantity |
| DELETE | `/api/cart/items/{listingId}` | removes a line |
| DELETE | `/api/cart` | empties the cart (204) |

Rules: 1-10 units per item, at most 20 different items, only ACTIVE in-stock listings, never more than available.
Errors are Problem Details with a `code`: `OUT_OF_STOCK`, `INSUFFICIENT_STOCK`, `MAX_QUANTITY`, `CART_FULL`,
`NO_LONGER_AVAILABLE` (409/404) and `CATALOG_UNAVAILABLE` (503).

Line `issues`: `NO_LONGER_AVAILABLE`, `OUT_OF_STOCK`, `INSUFFICIENT_STOCK` (these block checkout) and
`PRICE_CHANGED` (informational; checkout uses the current price).

## Internal APIs (Order Service; header `X-Internal-Key`; blocked at the gateway)
| Method | Path | Notes |
|---|---|---|
| GET | `/internal/carts/{userId}` | freshly validated cart for checkout |
| DELETE | `/internal/carts/{userId}` | clear after payment (idempotent) |

## Try it
```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"asha@example.com","password":"Passw0rd!23"}' | jq -r .accessToken)
IPHONE=00000000-0000-4000-8000-000000000001

curl -s -X POST localhost:8080/api/cart/items -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d "{\"listingId\":\"$IPHONE\",\"quantity\":2}"
curl -s localhost:8080/api/cart -H "Authorization: Bearer $TOKEN"
curl -s localhost:8080/api/cart/count -H "Authorization: Bearer $TOKEN"
```

## Known trade-offs
- A reader can, in a rare race, re-cache lines read just before a concurrent write; the 10 min TTL bounds it.
- Cart-to-Catalog uses a shared internal key (as Catalog's `/internal`); move to mTLS / service tokens later.
- Abandoned carts (untouched 30 days) are deleted by a nightly job.
