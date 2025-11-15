#!/usr/bin/env pwsh
# Order Processor Service - PowerShell Run Script with Dapr
# Port: 1007, Dapr HTTP: 3507, Dapr gRPC: 50007

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "Starting order-processor-service with Dapr..." -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# Kill any existing processes on ports
Write-Host "Cleaning up existing processes..." -ForegroundColor Yellow

# Kill process on port 1007 (app port)
$process = Get-NetTCPConnection -LocalPort 1007 -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique
if ($process) {
    Write-Host "Killing process on port 1007 (PID: $process)" -ForegroundColor Yellow
    Stop-Process -Id $process -Force -ErrorAction SilentlyContinue
}

# Kill process on port 3507 (Dapr HTTP port)
$process = Get-NetTCPConnection -LocalPort 3507 -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique
if ($process) {
    Write-Host "Killing process on port 3507 (PID: $process)" -ForegroundColor Yellow
    Stop-Process -Id $process -Force -ErrorAction SilentlyContinue
}

# Kill process on port 50007 (Dapr gRPC port)
$process = Get-NetTCPConnection -LocalPort 50007 -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique
if ($process) {
    Write-Host "Killing process on port 50007 (PID: $process)" -ForegroundColor Yellow
    Stop-Process -Id $process -Force -ErrorAction SilentlyContinue
}

Start-Sleep -Seconds 2

Write-Host ""
Write-Host "Starting with Dapr sidecar..." -ForegroundColor Green
Write-Host "App ID: order-processor-service" -ForegroundColor Cyan
Write-Host "App Port: 1007" -ForegroundColor Cyan
Write-Host "Dapr HTTP Port: 3507" -ForegroundColor Cyan
Write-Host "Dapr gRPC Port: 50007" -ForegroundColor Cyan
Write-Host ""

dapr run `
  --app-id order-processor-service `
  --app-port 1007 `
  --dapr-http-port 3507 `
  --dapr-grpc-port 50007 `
  --log-level error `
  --resources-path ./.dapr/components `
  --config ./.dapr/config.yaml `
  --enable-app-health-check `
  --app-health-check-path /actuator/health `
  --app-health-probe-interval 5 `
  --app-health-probe-timeout 10 `
  --app-health-threshold 2 `
  -- mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=1007"

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "Service stopped." -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
