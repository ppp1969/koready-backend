CREATE TABLE evidence_bundles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    bundle_id VARCHAR(80) NOT NULL,
    name VARCHAR(200) NOT NULL,
    status VARCHAR(20) NOT NULL,
    from_at DATETIME(6) NOT NULL,
    to_at DATETIME(6) NOT NULL,
    providers_json JSON NOT NULL,
    operations_json JSON NOT NULL,
    include_raw_snapshots BOOLEAN NOT NULL,
    raw_sample_limit_per_operation INT NOT NULL,
    storage_key VARCHAR(500) NULL,
    file_name VARCHAR(200) NULL,
    sha256 CHAR(64) NULL,
    byte_size BIGINT NULL,
    call_count BIGINT NULL,
    raw_snapshot_count BIGINT NULL,
    created_by_subject VARCHAR(191) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    failure_reason VARCHAR(500) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_evidence_bundles_bundle_id UNIQUE (bundle_id),
    CONSTRAINT chk_evidence_bundles_status CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_evidence_bundles_period CHECK (from_at < to_at),
    CONSTRAINT chk_evidence_bundles_raw_limit CHECK (raw_sample_limit_per_operation >= 0 AND raw_sample_limit_per_operation <= 100),
    CONSTRAINT chk_evidence_bundles_sizes CHECK (byte_size IS NULL OR byte_size >= 0),
    CONSTRAINT chk_evidence_bundles_counts CHECK ((call_count IS NULL OR call_count >= 0) AND (raw_snapshot_count IS NULL OR raw_snapshot_count >= 0))
);

CREATE INDEX idx_evidence_bundles_status_created ON evidence_bundles (status, created_at, id);
CREATE INDEX idx_evidence_bundles_created ON evidence_bundles (created_at DESC, id DESC);

CREATE TABLE evidence_bundle_exclusions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    evidence_bundle_id BIGINT NOT NULL,
    provider VARCHAR(20) NOT NULL,
    reason VARCHAR(60) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_evidence_bundle_exclusion UNIQUE (evidence_bundle_id, provider, reason),
    CONSTRAINT fk_evidence_bundle_exclusion_bundle
        FOREIGN KEY (evidence_bundle_id) REFERENCES evidence_bundles (id),
    CONSTRAINT chk_evidence_bundle_exclusion_reason
        CHECK (reason IN ('PROVIDER_RETENTION_RESTRICTED', 'EXPIRED', 'NOT_REQUESTED', 'SAMPLE_LIMIT'))
);

CREATE TABLE evidence_bundle_manifest_files (
    id BIGINT NOT NULL AUTO_INCREMENT,
    evidence_bundle_id BIGINT NOT NULL,
    path VARCHAR(500) NOT NULL,
    sha256 CHAR(64) NOT NULL,
    byte_size BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_evidence_bundle_manifest_path UNIQUE (evidence_bundle_id, path),
    CONSTRAINT fk_evidence_bundle_manifest_bundle
        FOREIGN KEY (evidence_bundle_id) REFERENCES evidence_bundles (id),
    CONSTRAINT chk_evidence_bundle_manifest_size CHECK (byte_size >= 0)
);
