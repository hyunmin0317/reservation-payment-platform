-- DDL
CREATE TABLE IF NOT EXISTS product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    price INT NOT NULL,
    check_in_time TIME NOT NULL,
    check_out_time TIME NOT NULL,
    description VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS stock (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL UNIQUE,
    total_quantity INT NOT NULL,
    remaining_quantity INT NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_stock_product FOREIGN KEY (product_id) REFERENCES product (id)
);

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    point_balance INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_number VARCHAR(30) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    total_amount INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(50) NOT NULL UNIQUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_orders_product FOREIGN KEY (product_id) REFERENCES product (id),
    INDEX idx_orders_user_id (user_id),
    INDEX idx_orders_idempotency_key (idempotency_key)
);

CREATE TABLE IF NOT EXISTS payment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    payment_method VARCHAR(20) NOT NULL,
    amount INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    transaction_id VARCHAR(100),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES orders (id),
    INDEX idx_payment_order_id (order_id)
);

-- 초기 데이터
INSERT INTO product (name, price, check_in_time, check_out_time, description)
VALUES ('제주 오션뷰 디럭스', 150000, '15:00', '11:00', '제주 바다가 한눈에 보이는 디럭스 객실'),
       ('서울 시티뷰 스위트', 200000, '15:00', '11:00', '서울 도심 야경을 즐길 수 있는 스위트룸'),
       ('부산 해운대 프리미엄', 180000, '16:00', '11:00', '해운대 해변과 가까운 프리미엄 객실'),
       ('강릉 경포 풀빌라', 250000, '15:00', '11:00', '경포호수 전망의 프라이빗 풀빌라'),
       ('여수 마린뷰 패밀리', 170000, '15:00', '12:00', '여수 밤바다를 감상할 수 있는 패밀리룸');

INSERT INTO stock (product_id, total_quantity, remaining_quantity, version)
VALUES (1, 10, 10, 0),
       (2, 10, 10, 0),
       (3, 10, 10, 0),
       (4, 10, 10, 0),
       (5, 10, 10, 0);

INSERT INTO users (name, email, point_balance)
VALUES ('김철수', 'kim@example.com', 100000),
       ('이영희', 'lee@example.com', 50000),
       ('박민수', 'park@example.com', 200000),
       ('정수진', 'jung@example.com', 0),
       ('최동욱', 'choi@example.com', 300000);
