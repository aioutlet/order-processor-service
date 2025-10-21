-- V4: Add order items and addresses to saga for event-driven architecture
-- This allows the saga to store all necessary data from OrderCreatedEvent
-- without needing to make HTTP calls to other services

-- Add order items as JSON (contains product details, quantities, prices)
ALTER TABLE order_processing_saga 
ADD COLUMN order_items JSONB;

-- Add shipping address as JSON
ALTER TABLE order_processing_saga 
ADD COLUMN shipping_address JSONB;

-- Add billing address as JSON
ALTER TABLE order_processing_saga 
ADD COLUMN billing_address JSONB;

-- Add index for querying by product in order items
CREATE INDEX idx_order_processing_saga_order_items 
ON order_processing_saga USING GIN (order_items);

-- Add comments
COMMENT ON COLUMN order_processing_saga.order_items IS 'Order items from OrderCreatedEvent - prevents need for HTTP calls to Order Service';
COMMENT ON COLUMN order_processing_saga.shipping_address IS 'Shipping address from OrderCreatedEvent';
COMMENT ON COLUMN order_processing_saga.billing_address IS 'Billing address from OrderCreatedEvent';
