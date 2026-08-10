CREATE TABLE account (
    id INT NOT NULL AUTO_INCREMENT,
    avatar VARCHAR(255) DEFAULT NULL,
    email VARCHAR(100) DEFAULT NULL,
    location VARCHAR(255) DEFAULT NULL,
    name VARCHAR(50) NOT NULL,
    password VARCHAR(100) NOT NULL,
    points INT DEFAULT NULL,
    role VARCHAR(255) NOT NULL,
    telephone VARCHAR(11) DEFAULT NULL,
    username VARCHAR(50) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE products (
    product_id INT NOT NULL AUTO_INCREMENT,
    category VARCHAR(255) DEFAULT NULL,
    cover VARCHAR(255) DEFAULT NULL,
    description VARCHAR(255) DEFAULT NULL,
    detail VARCHAR(255) DEFAULT NULL,
    price DECIMAL(19,2) DEFAULT NULL,
    rate DOUBLE DEFAULT NULL,
    title VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE orders (
    order_id INT NOT NULL AUTO_INCREMENT,
    create_time TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    payment_method VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_amount DECIMAL(10,2) NOT NULL,
    user_id INT NOT NULL,
    PRIMARY KEY (order_id),
    KEY idx_orders_user_id (user_id),
    CONSTRAINT fk_orders_account FOREIGN KEY (user_id) REFERENCES account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE carts (
    cart_item_id INT NOT NULL AUTO_INCREMENT,
    quantity INT NOT NULL DEFAULT 1,
    user_id INT NOT NULL,
    product_id INT NOT NULL,
    PRIMARY KEY (cart_item_id),
    KEY idx_carts_user_id (user_id),
    KEY idx_carts_product_id (product_id),
    CONSTRAINT fk_carts_account FOREIGN KEY (user_id) REFERENCES account (id),
    CONSTRAINT fk_carts_product FOREIGN KEY (product_id) REFERENCES products (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE order_item (
    id INT NOT NULL AUTO_INCREMENT,
    quantity INT DEFAULT NULL,
    order_id INT NOT NULL,
    product_id INT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_order_item_order_id (order_id),
    KEY idx_order_item_product_id (product_id),
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES orders (order_id),
    CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES products (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE advertisements (
    id INT NOT NULL AUTO_INCREMENT,
    content VARCHAR(500) NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    product_id INT NOT NULL,
    title VARCHAR(50) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_advertisements_product_id (product_id),
    CONSTRAINT fk_advertisements_product FOREIGN KEY (product_id) REFERENCES products (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE carts_orders_relation (
    id INT NOT NULL AUTO_INCREMENT,
    cartitem_id INT NOT NULL,
    order_id INT NOT NULL,
    PRIMARY KEY (id),
    KEY idx_cart_order_cart_item_id (cartitem_id),
    KEY idx_cart_order_order_id (order_id),
    CONSTRAINT fk_cart_order_cart FOREIGN KEY (cartitem_id) REFERENCES carts (cart_item_id),
    CONSTRAINT fk_cart_order_order FOREIGN KEY (order_id) REFERENCES orders (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE product_content_images (
    id INT NOT NULL AUTO_INCREMENT,
    image_url VARCHAR(255) DEFAULT NULL,
    product_id INT DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_content_images_product_id (product_id),
    CONSTRAINT fk_content_images_product FOREIGN KEY (product_id) REFERENCES products (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE product_specifications (
    id INT NOT NULL AUTO_INCREMENT,
    item VARCHAR(255) DEFAULT NULL,
    value VARCHAR(255) DEFAULT NULL,
    product_id INT DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_specifications_product_id (product_id),
    CONSTRAINT fk_specifications_product FOREIGN KEY (product_id) REFERENCES products (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE stockpile (
    id INT NOT NULL AUTO_INCREMENT,
    amount INT NOT NULL,
    frozen INT NOT NULL,
    product_id INT NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE payment_info (
    id INT NOT NULL AUTO_INCREMENT,
    order_id INT DEFAULT NULL,
    pay_time DATETIME(6) DEFAULT NULL,
    payment_status VARCHAR(255) DEFAULT NULL,
    tradeno VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_info_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE comments (
    id INT NOT NULL AUTO_INCREMENT,
    content VARCHAR(255) DEFAULT NULL,
    create_time DATETIME(6) DEFAULT NULL,
    product_id INT DEFAULT NULL,
    rate INT DEFAULT NULL,
    user_id INT DEFAULT NULL,
    user_name VARCHAR(255) DEFAULT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE chat_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    content VARCHAR(255) DEFAULT NULL,
    is_read BIT(1) DEFAULT NULL,
    receiver_id INT DEFAULT NULL,
    room_id VARCHAR(255) DEFAULT NULL,
    sender_id INT DEFAULT NULL,
    timestamp BIGINT DEFAULT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE chat_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    last_message VARCHAR(255) DEFAULT NULL,
    last_timestamp BIGINT DEFAULT NULL,
    peer_id INT DEFAULT NULL,
    user_id INT DEFAULT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
