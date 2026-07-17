# Database migrations

Flyway migration files live in this directory and use the format
`V<version>__<description>.sql`.

`V1__create_kto_ingestion_foundation.sql` is the approved initial migration for the KTO
ingestion foundation. Do not edit it after it has been applied outside an ephemeral test
database. Add a new versioned migration for every later schema change.
