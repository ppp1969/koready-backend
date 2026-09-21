ALTER TABLE users ALTER COLUMN signup_status SET DEFAULT 'NEED_LANGUAGE';

-- The legacy default KO does not prove that the user explicitly chose a language.
-- Restart only the initial terms step; preserve language and agreement history.
UPDATE users
SET signup_status = 'NEED_LANGUAGE'
WHERE signup_status = 'NEED_TERMS'
  AND deleted_at IS NULL
  AND account_status = 'ACTIVE';
