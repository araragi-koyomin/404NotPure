ALTER TABLE stockpile
    ADD CONSTRAINT uk_stockpile_product_id UNIQUE (product_id),
    ADD CONSTRAINT fk_stockpile_product
        FOREIGN KEY (product_id)
        REFERENCES products (product_id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT;
