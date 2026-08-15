INSERT INTO stockpile (amount, frozen, product_id)
SELECT 0, 0, products.product_id
FROM products
LEFT JOIN stockpile ON stockpile.product_id = products.product_id
WHERE stockpile.id IS NULL;
