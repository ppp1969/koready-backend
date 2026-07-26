CREATE TABLE kto_place_detail_sync_status (
    place_id BIGINT NOT NULL,
    common_snapshot_id BIGINT NOT NULL,
    intro_snapshot_id BIGINT NOT NULL,
    info_snapshot_id BIGINT NOT NULL,
    image_snapshot_id BIGINT NOT NULL,
    image_count INT NOT NULL,
    completed_at DATETIME(6) NOT NULL,
    next_refresh_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (place_id),
    CONSTRAINT fk_kto_detail_sync_place
        FOREIGN KEY (place_id) REFERENCES places (id) ON DELETE CASCADE,
    CONSTRAINT fk_kto_detail_sync_common_snapshot
        FOREIGN KEY (common_snapshot_id) REFERENCES open_api_raw_snapshots (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_kto_detail_sync_intro_snapshot
        FOREIGN KEY (intro_snapshot_id) REFERENCES open_api_raw_snapshots (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_kto_detail_sync_info_snapshot
        FOREIGN KEY (info_snapshot_id) REFERENCES open_api_raw_snapshots (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_kto_detail_sync_image_snapshot
        FOREIGN KEY (image_snapshot_id) REFERENCES open_api_raw_snapshots (id)
        ON DELETE CASCADE,
    CONSTRAINT chk_kto_detail_sync_image_count
        CHECK (image_count >= 0),
    CONSTRAINT chk_kto_detail_sync_refresh
        CHECK (next_refresh_at > completed_at)
);

CREATE INDEX idx_kto_detail_sync_refresh
    ON kto_place_detail_sync_status (next_refresh_at, place_id);

INSERT INTO kto_place_detail_sync_status (
    place_id,
    common_snapshot_id,
    intro_snapshot_id,
    info_snapshot_id,
    image_snapshot_id,
    image_count,
    completed_at,
    next_refresh_at
)
SELECT
    place.id,
    MAX(CASE WHEN call_log.operation = 'detailCommon2' THEN snapshot.id END),
    MAX(CASE WHEN call_log.operation = 'detailIntro2' THEN snapshot.id END),
    MAX(CASE WHEN call_log.operation = 'detailInfo2' THEN snapshot.id END),
    MAX(CASE WHEN call_log.operation = 'detailImage2' THEN snapshot.id END),
    (
        SELECT COUNT(*)
        FROM place_images image
        WHERE image.place_id = place.id
          AND image.source_type = 'KTO_DETAIL'
    ),
    MAX(snapshot.captured_at),
    DATE_ADD(MAX(snapshot.captured_at), INTERVAL 30 DAY)
FROM places place
JOIN open_api_call_logs call_log
  ON call_log.provider = 'KTO'
 AND call_log.api_name = 'KOR'
 AND call_log.success = TRUE
 AND call_log.operation IN (
     'detailCommon2',
     'detailIntro2',
     'detailInfo2',
     'detailImage2'
 )
 AND JSON_UNQUOTE(JSON_EXTRACT(
     call_log.request_params_masked,
     '$.contentId'
 )) = place.kto_content_id
JOIN open_api_raw_snapshots snapshot
  ON snapshot.call_log_id = call_log.id
GROUP BY place.id
HAVING COUNT(DISTINCT call_log.operation) = 4;
