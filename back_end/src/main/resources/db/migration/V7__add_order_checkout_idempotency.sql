ALTER TABLE orders
    ADD COLUMN idempotency_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER closed_time,
    ADD COLUMN request_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER idempotency_key,
    ADD CONSTRAINT uk_orders_user_idempotency_key UNIQUE (user_id, idempotency_key),
    ADD CONSTRAINT chk_orders_idempotency_pair
        CHECK ((idempotency_key IS NULL AND request_fingerprint IS NULL)
            OR (idempotency_key IS NOT NULL AND request_fingerprint IS NOT NULL));
