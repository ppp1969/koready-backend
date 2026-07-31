UPDATE places place
LEFT JOIN kto_place_detail_sync_status detail_status
    ON detail_status.place_id = place.id
SET place.show_flag = FALSE
WHERE place.kto_content_id IS NOT NULL
  AND detail_status.place_id IS NULL;
