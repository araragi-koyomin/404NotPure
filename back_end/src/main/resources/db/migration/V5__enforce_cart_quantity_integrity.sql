ALTER TABLE carts
    ADD CONSTRAINT chk_carts_quantity_positive CHECK (quantity > 0),
    ADD CONSTRAINT uk_carts_user_product UNIQUE (user_id, product_id);
