-- WC & QC Certificate module. Entirely new table, no foreign keys into any
-- billing table (invoices, shipments, customers) — the two modules are
-- independent by design. An uploaded invoice is read once for extraction and
-- never referenced again, so there is nothing to link here.

CREATE TABLE certificates (
    id                              BIGINT AUTO_INCREMENT PRIMARY KEY,
    type                            VARCHAR(8)   NOT NULL,
    variant                         VARCHAR(16)  NOT NULL DEFAULT 'STANDARD',
    status                          VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',

    source_invoice_file_name        VARCHAR(255),

    marks_and_nos                   VARCHAR(80)  NOT NULL,
    invoice_number                  VARCHAR(60),
    invoice_date                    DATE,
    declaration_line                VARCHAR(200),

    consignee_notify_block          TEXT,

    pre_carriage_by                 VARCHAR(80)  DEFAULT 'ROAD',
    place_of_receipt                VARCHAR(80),
    country_of_origin               VARCHAR(80)  DEFAULT 'INDIA',
    port_of_loading                 VARCHAR(80)  DEFAULT 'MANGALORE',
    port_of_discharge               VARCHAR(80),
    final_destination                VARCHAR(80),
    country_of_final_destination    VARCHAR(80),

    marks_column_text               TEXT,
    description_column_text         TEXT,
    quantity_column_text            TEXT,

    summary_statement_label         VARCHAR(40),
    summary_statement_lines         TEXT,

    coprocafe_bags_count            INT,
    coprocafe_total_kg              DECIMAL(12,2),
    coprocafe_moisture_percent      DECIMAL(5,2) DEFAULT 12,
    coprocafe_variety               VARCHAR(120),

    notes                           VARCHAR(500),

    generated_at                    TIMESTAMP NULL,
    generated_by                    VARCHAR(80),

    created_at                      TIMESTAMP NOT NULL,
    updated_at                      TIMESTAMP NOT NULL
);

CREATE INDEX idx_certificates_type ON certificates (type);
CREATE INDEX idx_certificates_marks ON certificates (marks_and_nos);
