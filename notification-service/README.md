# Notification Service

Turns domain events into messages: **email, SMS and an in-app inbox**. Port **8085**, database `notifications_db`
(Postgres, port 5436 locally), consumes Kafka. Email and SMS are mocks that print to the log (real providers plug in
through `ChannelSender`).

## How a notification is produced
```
Order / User service                    Kafka                    Notification service
 business tx ──┬─ order row                                  EventConsumer
               └─ outbox_events row ──► OutboxRelay ──► order-events / user-events ──► NotificationPlanner
                  (same transaction)    (polls, at-least-once)                           │ claims event id (processed_events)
                                                                                         │ renders templates (DB)
                                                                                         ▼
                                                              notifications  (EMAIL/SMS = PENDING, IN_APP = ready)
                                                                                         │
                                                                    DispatchJob ──► DeliveryService ──► MockEmailSender / MockSmsSender
                                                                    (retries with backoff, delivery_log per attempt)
```
Why this shape:
- **Transactional outbox:** an order can never be saved without its event, nor an event sent for an order that rolled back.
- **At-least-once + idempotent consumer:** Kafka may deliver twice; `processed_events` (plus a unique
  `(source_event_id, channel)`) makes the second delivery a no-op, so nobody gets two emails.
- **Consuming never blocks on sending:** the consumer only writes rows; a separate dispatcher sends. A slow or down
  email provider cannot stall Kafka.
- **Retries:** 5 attempts, waiting 30s, 2m, 10m, 30m, then `FAILED` (every attempt is in `delivery_log`). A crash mid-send
  is covered by a 2 minute lease.
- **Poison messages:** retried 3x, then parked on `<topic>.DLT`.

## Events handled
| Event (topic) | Email | SMS | In-app |
|---|---|---|---|
| `UserRegistered` (user-events) | welcome | | welcome |
| `OrderPaid` (order-events) | confirmation | confirmation | confirmation |
| `OrderCancelled` (order-events) | cancellation | | cancellation |

The buyer's email comes inside the event (from the JWT at checkout / the registration), so no lookup call is needed.
A missing email or phone just skips that one channel. Templates are rows in `templates` (placeholders like
`{{orderNumber}}`), editable without a deployment.

## Buyer API (through the gateway, JWT required): in-app inbox
| Method | Path | Notes |
|---|---|---|
| GET | `/api/notifications?page=0&size=20` | newest first; `refType=ORDER` + `refId` for deep links |
| GET | `/api/notifications/unread-count` | `{unread}` for a bell badge |
| POST | `/api/notifications/{id}/read` | 204, idempotent |
| POST | `/api/notifications/read-all` | 204 |

## Try it
```bash
docker compose up -d postgres order-postgres notification-postgres kafka    # plus catalog/cart/redis for orders
mvn -pl user-service spring-boot:run
mvn -pl notification-service spring-boot:run      # watch this console
mvn -pl gateway-service spring-boot:run

# 1) register: the welcome EMAIL appears in the notification-service log within ~2s
curl -s -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"asha@example.com","password":"Passw0rd!23","fullName":"Asha K"}'

# 2) inbox
curl -s localhost:8080/api/notifications -H "Authorization: Bearer $TOKEN"
curl -s localhost:8080/api/notifications/unread-count -H "Authorization: Bearer $TOKEN"
```
Place an order (order README) to get the confirmation email + SMS + in-app message.
Set `app.notification.mock.failure-rate: 0.5` to watch retries and `delivery_log` fill up.

Useful: `docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic order-events --from-beginning`

## Known limits
- Email / SMS are mocks. A real provider needs an implementation of `ChannelSender` (e.g. SMTP or SendGrid, Twilio or MSG91).
- Events published before the notification service ever ran are still consumed (`auto-offset-reset: earliest`), so first
  start may send "late" messages for old orders.
- No unsubscribe / per-user channel preferences, no push notifications, plain-text email only.
- Seller-side events (listing approved, low stock) are out of scope for the buyer-side build.
