# Admin-Driven Order Processing Workflow

## Overview

The order-processor-service is now refactored to be a **passive saga orchestrator** that waits for explicit admin actions. No automatic processing occurs. All state transitions require admin intervention through the Admin UI.

## Architecture

- **Pattern**: Saga Orchestration (Admin-Driven)
- **NOT**: Choreography (Automatic Processing)
- **Principle**: Human-in-the-loop workflow - every saga step requires admin confirmation

## Saga States

```java
public enum SagaStatus {
    CREATED,                      // Order created, saga initiated
    PENDING_PAYMENT_CONFIRMATION, // Waiting for admin to confirm payment
    PAYMENT_CONFIRMED,            // Admin confirmed payment received
    PENDING_SHIPPING_PREPARATION, // Waiting for admin to prepare shipment
    SHIPPING_PREPARED,            // Admin prepared shipment
    COMPLETED,                    // Fully processed
    CANCELLED,                    // Admin cancelled order
    COMPENSATING,                 // Rolling back
    COMPENSATED                   // Rollback completed
}
```

## Processing Steps

```java
public enum ProcessingStep {
    AWAITING_PAYMENT,    // Admin needs to confirm payment
    AWAITING_SHIPMENT,   // Admin needs to prepare shipment
    COMPLETED            // All steps done
}
```

## Event Flow

### 1. Order Creation
```
User submits order → Order-Service creates order → Publishes order.created event
  ↓
Order-Processor receives order.created
  ↓
Creates saga with:
  - Status: PENDING_PAYMENT_CONFIRMATION
  - Step: AWAITING_PAYMENT
  - NO automatic processing triggered
```

### 2. Payment Confirmation (Admin Action Required)
```
Admin reviews order in Admin UI
  ↓
Admin confirms payment received
  ↓
Admin UI calls admin-service endpoint: POST /api/orders/{orderId}/confirm-payment
  ↓
Admin-Service publishes payment.processed event
  ↓
Order-Processor receives payment.processed
  ↓
Updates saga:
  - Status: PENDING_SHIPPING_PREPARATION
  - Step: AWAITING_SHIPMENT
  - NO automatic inventory reservation
```

### 3. Shipment Preparation (Admin Action Required)
```
Admin reviews payment-confirmed orders in Admin UI
  ↓
Admin prepares shipment (packages items, prints label)
  ↓
Admin UI calls admin-service endpoint: POST /api/orders/{orderId}/prepare-shipment
  ↓
Admin-Service publishes shipping.prepared event
  ↓
Order-Processor receives shipping.prepared
  ↓
Updates saga:
  - Status: COMPLETED
  - Step: COMPLETED
```

### 4. Order Cancellation (Admin Action Available)
```
Admin can cancel order at any step
  ↓
Admin UI calls admin-service endpoint: POST /api/orders/{orderId}/cancel
  ↓
Admin-Service publishes order.cancelled event
  ↓
Order-Processor receives order.cancelled
  ↓
Starts compensation:
  - Status: COMPENSATING → COMPENSATED
  - Refunds payment if already collected
  - Releases inventory if already reserved
```

## Admin UI Requirements

The Admin UI must provide the following functionality:

### 1. Order Dashboard
- **View Pending Payment Orders**: List all orders with `status = PENDING_PAYMENT_CONFIRMATION`
- **View Pending Shipment Orders**: List all orders with `status = PENDING_SHIPPING_PREPARATION`
- **View Completed Orders**: List all orders with `status = COMPLETED`
- **View Cancelled Orders**: List all orders with `status = CANCELLED`

### 2. Order Actions
For each order, provide action buttons:

#### Pending Payment Orders
- **Confirm Payment** button → Calls `POST /api/admin/orders/{orderId}/confirm-payment`
- **Cancel Order** button → Calls `POST /api/admin/orders/{orderId}/cancel`

#### Payment Confirmed Orders
- **Prepare Shipment** button → Calls `POST /api/admin/orders/{orderId}/prepare-shipment`
- **Cancel Order** button → Calls `POST /api/admin/orders/{orderId}/cancel`

### 3. Order Details
- Display current saga status
- Display processing step
- Show order items, customer info, payment amount
- Show audit trail of admin actions

## Admin-Service API Endpoints (To Be Implemented)

```typescript
// POST /api/admin/orders/{orderId}/confirm-payment
{
  "adminId": "string",
  "paymentMethod": "string",
  "paymentReference": "string",
  "notes": "string"
}

// POST /api/admin/orders/{orderId}/prepare-shipment
{
  "adminId": "string",
  "trackingNumber": "string",
  "carrier": "string",
  "shippingAddress": {...},
  "notes": "string"
}

// POST /api/admin/orders/{orderId}/cancel
{
  "adminId": "string",
  "reason": "string",
  "notes": "string"
}
```

## Event Publishing (Admin-Service Responsibility)

When admin takes action, admin-service must publish events:

### Payment Confirmation Event
```json
{
  "eventType": "payment.processed",
  "orderId": "ORD-20251120-XXXXX",
  "sagaId": "uuid",
  "adminId": "admin-user-id",
  "paymentDetails": {
    "method": "credit_card",
    "reference": "PAY-12345",
    "amount": 99.99,
    "currency": "USD"
  },
  "timestamp": "2025-11-20T10:30:00Z"
}
```

### Shipment Preparation Event
```json
{
  "eventType": "shipping.prepared",
  "orderId": "ORD-20251120-XXXXX",
  "sagaId": "uuid",
  "adminId": "admin-user-id",
  "shippingDetails": {
    "trackingNumber": "1Z999AA10123456784",
    "carrier": "UPS",
    "estimatedDelivery": "2025-11-25"
  },
  "timestamp": "2025-11-20T14:45:00Z"
}
```

### Order Cancellation Event
```json
{
  "eventType": "order.cancelled",
  "orderId": "ORD-20251120-XXXXX",
  "sagaId": "uuid",
  "adminId": "admin-user-id",
  "cancellationReason": "Customer requested refund",
  "timestamp": "2025-11-20T09:15:00Z"
}
```

## Database Queries (Order-Processor)

The Admin UI will need to query order-processor-service for saga states:

```sql
-- Get orders awaiting payment confirmation
SELECT * FROM order_processing_saga 
WHERE status = 'PENDING_PAYMENT_CONFIRMATION' 
ORDER BY created_at DESC;

-- Get orders awaiting shipment
SELECT * FROM order_processing_saga 
WHERE status = 'PENDING_SHIPPING_PREPARATION' 
ORDER BY updated_at DESC;

-- Get completed orders
SELECT * FROM order_processing_saga 
WHERE status = 'COMPLETED' 
ORDER BY completed_at DESC;
```

## Testing Workflow

### Manual Testing via Postman/cURL

1. **Create Order** (via order-service):
   ```bash
   POST http://localhost:8083/api/orders
   ```

2. **Verify Saga Created**:
   ```bash
   GET http://localhost:8086/api/saga/{sagaId}
   # Should show status = PENDING_PAYMENT_CONFIRMATION
   ```

3. **Simulate Admin Payment Confirmation**:
   ```bash
   # Manually publish event via Dapr
   POST http://localhost:3500/v1.0/publish/event-bus/payment.processed
   {
     "orderId": "ORD-20251120-XXXXX",
     "sagaId": "uuid",
     "adminId": "test-admin",
     "paymentDetails": {...}
   }
   ```

4. **Verify Saga Updated**:
   ```bash
   GET http://localhost:8086/api/saga/{sagaId}
   # Should show status = PENDING_SHIPPING_PREPARATION
   ```

5. **Simulate Admin Shipment Preparation**:
   ```bash
   POST http://localhost:3500/v1.0/publish/event-bus/shipping.prepared
   {
     "orderId": "ORD-20251120-XXXXX",
     "sagaId": "uuid",
     "adminId": "test-admin",
     "shippingDetails": {...}
   }
   ```

6. **Verify Saga Completed**:
   ```bash
   GET http://localhost:8086/api/saga/{sagaId}
   # Should show status = COMPLETED
   ```

## Key Changes Made

### Removed (No Longer Automatic)
- ❌ Automatic payment processing
- ❌ Automatic inventory reservation
- ❌ Automatic shipping preparation
- ❌ Automatic retry logic
- ❌ Event publishing from order-processor (except compensation)

### Added (Admin-Driven)
- ✅ Saga starts in PENDING state awaiting admin action
- ✅ Event handlers only update state (no triggering next steps)
- ✅ Explicit state transition methods: `markPaymentConfirmed()`, `markShippingPrepared()`
- ✅ Logging: "Saga awaiting admin action: {action required}"

## Next Steps for Team

1. **Admin-Service Team**: Implement admin action endpoints that publish events
2. **Admin UI Team**: Build order dashboard with action buttons
3. **Order-Processor Team**: Fix any remaining compilation issues, add REST endpoints to query saga state
4. **DevOps Team**: Update monitoring to track saga states and admin response times
5. **Testing Team**: Create E2E tests for admin-driven workflow

## Benefits of This Approach

- ✅ **Control**: Admin reviews every order before processing
- ✅ **Fraud Prevention**: Payment confirmation step catches suspicious orders
- ✅ **Inventory Management**: Shipment preparation only when ready to ship
- ✅ **Auditability**: Every state transition has admin ID and timestamp
- ✅ **Flexibility**: Admin can cancel or modify orders at any step
- ✅ **Simplicity**: Order-processor is just a state machine, no business logic

## Architecture Diagram

```
┌─────────────┐
│   User      │ Submits Order
└──────┬──────┘
       │
       v
┌─────────────────┐
│ Order-Service   │ Creates Order → Publishes order.created
└─────────┬───────┘
          │
          v
┌─────────────────────────────────────────────┐
│     Order-Processor-Service (Orchestrator)  │
│                                             │
│  Receives order.created                     │
│    ↓                                        │
│  Creates Saga: PENDING_PAYMENT_CONFIRMATION │
│    ↓                                        │
│  WAITS FOR ADMIN ACTION                     │
└─────────────────────────────────────────────┘
          ↑
          │ Admin confirms payment
          │
┌─────────────────┐
│   Admin UI      │ Admin clicks "Confirm Payment"
└─────────┬───────┘
          │
          v
┌─────────────────┐
│ Admin-Service   │ Publishes payment.processed
└─────────┬───────┘
          │
          v
┌─────────────────────────────────────────────┐
│     Order-Processor-Service                 │
│                                             │
│  Receives payment.processed                 │
│    ↓                                        │
│  Updates Saga: PENDING_SHIPPING_PREPARATION │
│    ↓                                        │
│  WAITS FOR ADMIN ACTION                     │
└─────────────────────────────────────────────┘
          ↑
          │ Admin prepares shipment
          │
┌─────────────────┐
│   Admin UI      │ Admin clicks "Prepare Shipment"
└─────────┬───────┘
          │
          v
┌─────────────────┐
│ Admin-Service   │ Publishes shipping.prepared
└─────────┬───────┘
          │
          v
┌─────────────────────────────────────────────┐
│     Order-Processor-Service                 │
│                                             │
│  Receives shipping.prepared                 │
│    ↓                                        │
│  Updates Saga: COMPLETED                    │
└─────────────────────────────────────────────┘
```
