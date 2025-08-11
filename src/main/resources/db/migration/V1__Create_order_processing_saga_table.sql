-- Create order_processing_saga table
CREATE TABLE IF NOT EXISTS order_processing_saga (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL UNIQUE,
    customer_id VARCHAR(100) NOT NULL,
    order_number VARCHAR(100) NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(20) NOT NULL DEFAULT 'STARTED',
    current_step VARCHAR(20) NOT NULL DEFAULT 'PAYMENT_PROCESSING',
    payment_id VARCHAR(100),
    inventory_reservation_id VARCHAR(100),
    shipping_id VARCHAR(100),
    error_message VARCHAR(500),
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_order_processing_saga_order_id ON order_processing_saga(order_id);
CREATE INDEX IF NOT EXISTS idx_order_processing_saga_status ON order_processing_saga(status);
CREATE INDEX IF NOT EXISTS idx_order_processing_saga_customer_id ON order_processing_saga(customer_id);
CREATE INDEX IF NOT EXISTS idx_order_processing_saga_created_at ON order_processing_saga(created_at);
CREATE INDEX IF NOT EXISTS idx_order_processing_saga_updated_at ON order_processing_saga(updated_at);

-- Create a trigger to automatically update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_order_processing_saga_updated_at
    BEFORE UPDATE ON order_processing_saga
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
