CREATE TABLE order_processing_saga (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE,
    customer_id VARCHAR(100) NOT NULL,
    order_number VARCHAR(100) NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    current_step VARCHAR(20) NOT NULL,
    payment_id VARCHAR(100),
    inventory_reservation_id VARCHAR(100),
    shipping_id VARCHAR(100),
    error_message VARCHAR(500),
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP
);

CREATE INDEX idx_saga_order_id ON order_processing_saga(order_id);
CREATE INDEX idx_saga_status ON order_processing_saga(status);
CREATE INDEX idx_saga_created_at ON order_processing_saga(created_at);
