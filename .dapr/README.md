# Order Processor Service - Dapr Configuration

## Dapr Components

This service uses the following Dapr building blocks:

### 1. Pub/Sub (`pubsub.yaml`)

- **Component Name**: `order-processor-pubsub`
- **Type**: Redis Pub/Sub
- **Topics**:
  - order.created
  - payment.processed, payment.failed
  - inventory.reserved, inventory.failed
  - shipping.prepared, shipping.failed

### 2. State Store (`statestore.yaml`)

- **Component Name**: `order-processor-statestore`
- **Type**: Redis State Store
- **Usage**: Actor state persistence (future use)

### 3. Secret Store (`secrets.yaml`)

- **Component Name**: `local-secret-store`
- **Type**: Local File Secret Store
- **File**: `.dapr/secrets.json`
- **Secrets**:
  - Database credentials
  - JWT secret
  - External service URLs

## Prerequisites

1. **Dapr CLI** installed

   ```bash
   # Install Dapr CLI
   wget -q https://raw.githubusercontent.com/dapr/cli/master/install/install.sh -O - | /bin/bash

   # Initialize Dapr
   dapr init
   ```

2. **Redis** running (for pub/sub and state store)

   ```bash
   docker run -d -p 6379:6379 redis:alpine
   ```

3. **PostgreSQL** running (for application data)
   ```bash
   docker run -d -p 5432:5432 -e POSTGRES_PASSWORD=postgres postgres:15
   ```

## Running the Service

### With Dapr (Recommended)

**Linux/Mac:**

```bash
./run-with-dapr.sh
```

**Windows:**

```cmd
run-with-dapr.bat
```

### Without Dapr (Development only)

```bash
mvn spring-boot:run
```

Note: Without Dapr, pub/sub and secrets won't work.

## Dapr Ports

- **Application Port**: 8080
- **Dapr HTTP Port**: 3500
- **Dapr gRPC Port**: 50001

## Service Invocation

Call this service via Dapr:

```bash
# Via Dapr sidecar
curl http://localhost:3500/v1.0/invoke/order-processor-service/method/api/v1/admin/sagas

# Direct to app (not recommended in production)
curl http://localhost:8080/api/v1/admin/sagas
```

## Pub/Sub Testing

Publish event to test:

```bash
dapr publish --publish-app-id order-processor-service \
  --pubsub order-processor-pubsub \
  --topic order.created \
  --data '{"orderId":"123","customerId":"456"}'
```

## Configuration

### Update Secrets

Edit `.dapr/secrets.json` to change:

- Database connection
- JWT secret
- Service URLs

### Update Redis Connection

Edit `.dapr/components/pubsub.yaml` and `.dapr/components/statestore.yaml`:

```yaml
metadata:
  - name: redisHost
    value: your-redis-host:6379
  - name: redisPassword
    value: your-redis-password
```

## Production Deployment

For production (Kubernetes):

1. Replace local components with production versions
2. Use Azure Service Bus / AWS SNS for pub/sub
3. Use Azure Key Vault / AWS Secrets Manager for secrets
4. Use Azure Redis / AWS ElastiCache for state store

Example Azure components available in `kubernetes/` directory.

## Troubleshooting

### Check Dapr Status

```bash
dapr list
```

### View Dapr Logs

```bash
dapr logs --app-id order-processor-service
```

### Check Components

```bash
curl http://localhost:3500/v1.0/metadata
```

## Architecture

```
┌─────────────────────────────────────────────┐
│  Order Processor Service (Port 8080)       │
│                                             │
│  ┌─────────────────────────────────────┐   │
│  │   Spring Boot Application           │   │
│  │   - Event Consumers                 │   │
│  │   - Event Publishers                │   │
│  │   - Saga Orchestrator               │   │
│  └─────────────────────────────────────┘   │
└─────────────────────────────────────────────┘
              ↕ (HTTP/gRPC)
┌─────────────────────────────────────────────┐
│        Dapr Sidecar (Ports 3500/50001)      │
│                                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  │
│  │ Pub/Sub  │  │ Secrets  │  │  State   │  │
│  └──────────┘  └──────────┘  └──────────┘  │
└─────────────────────────────────────────────┘
              ↕
┌─────────────────────────────────────────────┐
│         Infrastructure                      │
│  - Redis (Pub/Sub + State)                 │
│  - PostgreSQL (App Data)                   │
│  - Zipkin (Tracing)                        │
└─────────────────────────────────────────────┘
```
