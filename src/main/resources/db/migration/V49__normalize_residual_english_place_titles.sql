-- Remove values rewritten by the retiring EB instance during the V48 rollout.
UPDATE place_localizations
SET title = TRIM(REGEXP_REPLACE(
        title,
        '[[:space:]]*\\([^)]*[가-힣][^)]*\\)[[:space:]]*$',
        ''
    )),
    updated_at = CURRENT_TIMESTAMP(6)
WHERE language = 'EN'
  AND title REGEXP '[A-Za-z]'
  AND title REGEXP '[[:space:]]*\\([^)]*[가-힣][^)]*\\)[[:space:]]*$';
