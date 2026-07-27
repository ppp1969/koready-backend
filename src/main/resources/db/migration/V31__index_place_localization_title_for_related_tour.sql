CREATE INDEX idx_place_localizations_language_title
    ON place_localizations (language, title, place_id);
