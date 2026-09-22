CREATE TABLE kto_daily_request_usage (
    usage_date DATE NOT NULL,
    operation_key VARCHAR(100) NOT NULL,
    reserved_requests INT NOT NULL DEFAULT 0,
    blocked BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (usage_date, operation_key),
    CONSTRAINT chk_kto_reserved_requests CHECK (reserved_requests >= 0)
);

-- Calls made before this ledger existed cannot be reconstructed exactly from
-- deduplicated success logs. Pause known operations for the migration day.
INSERT INTO kto_daily_request_usage (usage_date, operation_key, blocked)
SELECT DATE(UTC_TIMESTAMP() + INTERVAL 9 HOUR), operation_key, TRUE
FROM (
    SELECT 'kor-areabasedsynclist2' operation_key UNION ALL
    SELECT 'eng-areabasedsynclist2' UNION ALL
    SELECT 'kor-searchfestival2' UNION ALL
    SELECT 'kor-detailcommon2' UNION ALL
    SELECT 'kor-detailintro2' UNION ALL
    SELECT 'kor-detailinfo2' UNION ALL
    SELECT 'kor-detailimage2' UNION ALL
    SELECT 'kor-searchkeyword2' UNION ALL
    SELECT 'photo-award-phokoawrdsynclist' UNION ALL
    SELECT 'photo-gallery-gallerylist1' UNION ALL
    SELECT 'related-tour-areabasedlist1'
) operations;
