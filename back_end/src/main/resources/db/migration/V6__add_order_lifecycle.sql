ALTER TABLE orders
    ADD COLUMN cancelled_time TIMESTAMP(6) NULL AFTER paid_time,
    ADD COLUMN closed_time TIMESTAMP(6) NULL AFTER cancelled_time,
    ADD INDEX idx_orders_status_create_time_order_id (status, create_time, order_id),
    ADD CONSTRAINT chk_orders_status
        CHECK (CAST(status AS BINARY) IN ('PENDING', 'PAID', 'CANCELLED', 'CLOSED'));
