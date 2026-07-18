CREATE INDEX idx_event_occurrence_place_window
    ON place_event_occurrences (
        place_id,
        date_validation_status,
        end_date,
        visible_from,
        id
    );
