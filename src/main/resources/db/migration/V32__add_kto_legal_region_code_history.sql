CREATE TABLE kto_legal_region_code_aliases (
    current_area_code VARCHAR(10) NOT NULL,
    current_signgu_code VARCHAR(10) NOT NULL,
    previous_area_code VARCHAR(10) NOT NULL,
    previous_signgu_code VARCHAR(10) NOT NULL,
    effective_from_ym CHAR(6) NOT NULL,
    source_reference VARCHAR(500) NOT NULL,
    PRIMARY KEY (
        current_area_code,
        current_signgu_code,
        effective_from_ym
    )
);

CREATE INDEX idx_kto_region_alias_previous
    ON kto_legal_region_code_aliases (
        previous_area_code,
        previous_signgu_code,
        effective_from_ym
    );

INSERT INTO kto_legal_region_code_aliases (
    current_area_code,
    current_signgu_code,
    previous_area_code,
    previous_signgu_code,
    effective_from_ym,
    source_reference
)
VALUES
    ('12', '12110', '46', '46110', '202607', 'KTO_NOTICE_20260629'),
    ('12', '12130', '46', '46130', '202607', 'KTO_NOTICE_20260629'),
    ('12', '12150', '46', '46150', '202607', 'KTO_NOTICE_20260629'),
    ('12', '12170', '46', '46170', '202607', 'KTO_NOTICE_20260629'),
    ('12', '12190', '46', '46230', '202607', 'KTO_NOTICE_20260629'),
    ('12', '12210', '29', '29110', '202607', 'KTO_NOTICE_20260629'),
    ('12', '12240', '29', '29140', '202607', 'KTO_NOTICE_20260629'),
    ('12', '12270', '29', '29155', '202607', 'KTO_NOTICE_20260629'),
    ('12', '12300', '29', '29170', '202607', 'KTO_NOTICE_20260629'),
    ('12', '12330', '29', '29200', '202607', 'KTO_NOTICE_20260629'),
    ('12', '12710', '46', '46710', '202607', 'KTO_NOTICE_20260629'),
    ('12', '12720', '46', '46720', '202607', 'KTO_NOTICE_20260629'),
    ('12', '12730', '46', '46730', '202607', 'KTO_NOTICE_20260629'),
    ('12', '12740', '46', '46770', '202607', 'KTO_NOTICE_20260629'),
    ('12', '12750', '46', '46780', '202607', 'KTO_NOTICE_20260629'),
    ('12', '12760', '46', '46790', '202607', 'KTO_NOTICE_20260629'),
    ('12', '12770', '46', '46800', '202607', 'KTO_NOTICE_20260629'),
    ('12', '12780', '46', '46810', '202607', 'KTO_NOTICE_20260629'),
    ('12', '12790', '46', '46820', '202607', 'KTO_NOTICE_20260629'),
    ('12', '12800', '46', '46830', '202607', 'KTO_NOTICE_20260629'),
    ('12', '12810', '46', '46840', '202607', 'KTO_NOTICE_20260629'),
    ('12', '12820', '46', '46860', '202607', 'KTO_NOTICE_20260629'),
    ('12', '12830', '46', '46870', '202607', 'KTO_NOTICE_20260629'),
    ('12', '12840', '46', '46880', '202607', 'KTO_NOTICE_20260629'),
    ('12', '12850', '46', '46890', '202607', 'KTO_NOTICE_20260629'),
    ('12', '12860', '46', '46900', '202607', 'KTO_NOTICE_20260629'),
    ('12', '12870', '46', '46910', '202607', 'KTO_NOTICE_20260629'),
    ('28', '28125', '28', '28140', '202607', 'KTO_NOTICE_20260629'),
    ('28', '28155', '28', '28110', '202607', 'KTO_NOTICE_20260629'),
    ('28', '28275', '28', '28260', '202607', 'KTO_NOTICE_20260629'),
    ('28', '28290', '28', '28260', '202607', 'KTO_NOTICE_20260629');
