-- The buyer's email travels inside OrderPaid / OrderCancelled so Notification needs no lookup (event-carried state).
-- Nullable: orders created before this migration have none.
ALTER TABLE orders ADD COLUMN user_email VARCHAR(255);
