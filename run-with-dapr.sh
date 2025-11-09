#!/bin/bash

# Run Order Processor Service with Dapr sidecar

APP_ID="order-processor-service"
APP_PORT=8080
DAPR_HTTP_PORT=3500
DAPR_GRPC_PORT=50001
COMPONENTS_PATH="./.dapr/components"
CONFIG_PATH="./.dapr/config.yaml"

echo "Starting Order Processor Service with Dapr..."

dapr run \
  --app-id $APP_ID \
  --app-port $APP_PORT \
  --dapr-http-port $DAPR_HTTP_PORT \
  --dapr-grpc-port $DAPR_GRPC_PORT \
  --components-path $COMPONENTS_PATH \
  --config $CONFIG_PATH \
  --log-level info \
  -- mvn spring-boot:run
