CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL,
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until DATETIME(6) NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE financial_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    amount DECIMAL(19,2) NOT NULL,
    type VARCHAR(10) NOT NULL,
    category VARCHAR(100) NOT NULL,
    record_date DATE NOT NULL,
    description VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted BOOLEAN NOT NULL,
    deleted_at DATETIME(6) NULL,
    user_id BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_records_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_records_live_date ON financial_records (deleted, record_date);
CREATE INDEX idx_records_category ON financial_records (category);

-- Hash-chained, append-only (by convention; see README for the threat model).
CREATE TABLE audit_log (
    seq BIGINT PRIMARY KEY,
    occurred_at BIGINT NOT NULL,
    actor VARCHAR(255) NOT NULL,
    action VARCHAR(40) NOT NULL,
    entity_type VARCHAR(40) NOT NULL,
    entity_id BIGINT NOT NULL,
    entity_digest VARCHAR(64) NULL,
    details VARCHAR(1000) NULL,
    prev_hash VARCHAR(64) NOT NULL,
    hash VARCHAR(64) NOT NULL
);

-- Single row; locked FOR UPDATE so concurrent writers cannot fork the chain.
CREATE TABLE audit_head (
    id INT PRIMARY KEY,
    last_seq BIGINT NOT NULL,
    last_hash VARCHAR(64) NOT NULL
);

INSERT INTO audit_head (id, last_seq, last_hash)
VALUES (1, 0, '0000000000000000000000000000000000000000000000000000000000000000');
