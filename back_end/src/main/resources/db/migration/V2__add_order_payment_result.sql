ALTER TABLE orders
    ADD COLUMN paid_time TIMESTAMP(6) NULL AFTER create_time,
    ADD COLUMN alipay_trade_no VARCHAR(64) NULL AFTER payment_method,
    ADD CONSTRAINT uk_orders_alipay_trade_no UNIQUE (alipay_trade_no);
