#!/bin/sh
set -e

# Create bridge databases on first PostgreSQL initialization.
# For existing installations, use the init-bridges-db service instead.
psql -v ON_ERROR_STOP=0 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-'EOSQL'
    CREATE DATABASE mautrix_telegram;
    CREATE DATABASE mautrix_whatsapp;
    CREATE DATABASE mautrix_signal;
EOSQL
