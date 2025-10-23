#!/bin/bash
set -e

# Create database if it doesn't exist
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<-EOSQL
    SELECT 'CREATE DATABASE order_processor_db'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'order_processor_db')\gexec
EOSQL

echo "Database order_processor_db is ready!"
