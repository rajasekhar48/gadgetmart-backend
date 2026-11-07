# Order Service (buyer side)

"Order now": turns the buyer's cart into a paid order. Port **8084**, database `orders_db` (Postgres, port 5435 locally).
Payment is a **mock** for now, behind a `PaymentProvider` interface (real gateways plug in later).

## Buyer APIs (through the gateway, JWT required)
| Method | Path | Notes |
|---|---|---|
| POST | `/api/orders/checkout` | header `Idempotency-Key` (8-64 chars) + body `{shippingAddress, simulatePayment?}` |
| GET | `/api/orders?page=0&size=10` | my orders, newest first |
| GET | `/api/orders/{id}` | detail: items, address, payment, status timeline |

`201` = new order placed, `200` = same Idempotency-Key replayed (returns the original order, never a second one).
Errors are Problem Details with a `code`:

| Status | code | Meaning |
|---|---|---|
| 409 | `EMPTY_CART` | nothing to order |
| 409 | `CART_NOT_READY` | some lines are out of stock / unavailable (`items` lists them) |
| 409 | `STOCK_UNAVAILABLE` | someone bought it between cart view and checkout (`unavailableItems`) |
| 402 | `PAYMENT_FAILED` | payment declined; stock released, nothing charged |
| 409 | `RESERVATION_EXPIRED` | hold lost after payment; payment refunded |
| 409 | `CHECKOUT_IN_PROGRESS` | same key still being processed |
| 503 | `SERVICE_UNAVAILABLE` | Cart/Catalog down; nothing charged |
| 503 | `ORDER_CONFIRMING` | paid, but stock confirmation is pending; the recovery job finishes it |

`simulatePayment: "FAIL"` makes the mock decline (DEV ONLY, `app.payment.mock.allow-simulation`).

## The checkout flow
```
POST /checkout
 1. Idempotency check (same key -> return original order)
 2. Cart  GET  /internal/carts/{user}            live prices + stock, must be checkoutReady
 3. Order created PENDING_PAYMENT                price / title / address SNAPSHOTS
 4. Catalog POST /internal/reservations          hold stock, all-or-nothing   (409 -> REJECTED)
 5. PaymentProvider.charge                       mock: succeeds                (fail -> release stock, CANCELLED)
 6. Catalog POST .../commit                      held units become sold        (hold lost -> refund, CANCELLED)
 7. Order PAID + outbox event OrderPaid, then Cart DELETE /internal/carts/{user}
```
No database transaction is open while talking to other services. If a step fails, earlier steps are undone
(release stock / refund). `OrderRecoveryJob` (every minute) finishes orders stuck in `PENDING_PAYMENT`: it re-runs the
(idempotent) commit for paid ones, refunds if the hold is truly gone, and cancels unpaid ones after 20 minutes.

Status: `PENDING_PAYMENT -> PAID -> SHIPPED -> DELIVERED -> COMPLETED`, side exits `REJECTED`, `CANCELLED`.
Only up to `PAID` happens in this (buyer-side) scope; shipping, returns and cancellation of paid orders come later.
Every change is stored in `order_status_history`.

## Outbox
`OrderPaid` and `OrderCancelled` are written to `outbox_events` in the same transaction as the status change.
Nothing publishes them yet; the Kafka relay arrives with the Notification Service.

## Try it
```bash
TOKEN=...   # login as in the cart README; put something in the cart first
curl -s -X POST localhost:8080/api/orders/checkout \
  -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: $(uuidgen)" -H 'Content-Type: application/json' \
  -d '{"shippingAddress":{"fullName":"Asha K","phone":"+919876543210","line1":"12 MG Road","city":"Bengaluru","state":"Karnataka","pincode":"560001"}}'
curl -s localhost:8080/api/orders -H "Authorization: Bearer $TOKEN"
```
Use the same `Idempotency-Key` twice to see the replay (200, same order). Add `"simulatePayment":"FAIL"` to see the 402 path.

## Known limits
- The price is read from the cart at checkout start; a change in the milliseconds before the reservation is not re-checked.
- Recovery job: with several replicas two may pick the same order (fine for commit/release/cancel); add a SKIP LOCKED claim before running real refunds on many replicas.
- Address is sent with the request; User Service will own saved addresses later.
