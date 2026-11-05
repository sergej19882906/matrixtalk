#!/bin/sh
set -eu
# Idempotent on fresh and existing clusters. SQL/auth failures are never swallowed.
export PGDATABASE="${POSTGRES_DB:-postgres}"
for database in mautrix_telegram mautrix_whatsapp mautrix_signal mautrix_meta mautrix_gmessages mautrix_discord; do
    exists=$(psql -v ON_ERROR_STOP=1 -At --username "$POSTGRES_USER" --dbname "$PGDATABASE" \
        -c "SELECT 1 FROM pg_database WHERE datname = '$database'")
    if [ "$exists" != "1" ]; then
        psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$PGDATABASE" -c "CREATE DATABASE $database"
    fi
done
printf '%s\n' 'Bridge databases ready.'
