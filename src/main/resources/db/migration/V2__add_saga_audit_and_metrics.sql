-- =============================================================================
-- Add audit columns and performance metrics to saga table
-- =============================================================================

-- Add audit trail columns
ALTER TABLE order_processing_saga
    ADD COLUMN created_by VARCHAR(255) DEFAULT 'system',
    ADD COLUMN updated_by VARCHAR(255) DEFAULT 'system',
    ADD COLUMN version INTEGER NOT NULL DEFAULT 0;

-- Add performance metrics columns
ALTER TABLE order_processing_saga
    ADD COLUMN payment_processing_started_at TIMESTAMP,
    ADD COLUMN payment_processing_completed_at TIMESTAMP,
    ADD COLUMN inventory_processing_started_at TIMESTAMP,
    ADD COLUMN inventory_processing_completed_at TIMESTAMP,
    ADD COLUMN shipping_processing_started_at TIMESTAMP,
    ADD COLUMN shipping_processing_completed_at TIMESTAMP;

-- Add correlation ID for distributed tracing
ALTER TABLE order_processing_saga
    ADD COLUMN correlation_id VARCHAR(255);

-- Create index on correlation ID for tracing queries
CREATE INDEX idx_order_processing_saga_correlation_id ON order_processing_saga(correlation_id);

-- Create function to automatically update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    NEW.version = OLD.version + 1;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create trigger to auto-update updated_at
CREATE TRIGGER trg_order_processing_saga_updated_at
    BEFORE UPDATE ON order_processing_saga
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Comments
COMMENT ON COLUMN order_processing_saga.version IS 'Optimistic locking version number';
COMMENT ON COLUMN order_processing_saga.correlation_id IS 'Distributed tracing correlation ID';
