CREATE INDEX idx_event_occurrence_monthly_listing
    ON place_event_occurrences (
        date_validation_status,
        start_date,
        end_date,
        visible_from,
        place_id,
        id
    );
