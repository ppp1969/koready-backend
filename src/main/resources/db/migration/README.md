# Database migrations

Flyway migration files live in this directory and use the format
`V<version>__<description>.sql`.

The initial domain migration is intentionally not defined yet. The current DB model under
`docs/koready-backend-design` is a draft and must be approved before it becomes an immutable
migration history.
