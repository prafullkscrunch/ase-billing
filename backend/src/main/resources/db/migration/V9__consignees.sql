-- Consignees for the sample (S) bill's PSC statement.
--
-- Taken from HC SAMPLE PSC DETAILS for Jul-2026 (bill 434, 24 sets) and
-- Aug-2026 (bill 438, 17 sets). The operator picks from this list rather than
-- retyping a name, which is what stops "Group Sopex" and "NV Group Sopex"
-- drifting into three spellings across months.
--
-- Both of those appear in ASE's own sheets as separate entries, so both are kept.

CREATE TABLE consignees (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  name       VARCHAR(160) NOT NULL,
  country    VARCHAR(80),
  active     BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_consignees_name (name)
) ENGINE=InnoDB;

INSERT INTO consignees (name) VALUES
  ('Bernhard Rothfos'),
  ('Briz Coffee'),
  ('DRWakefield'),
  ('EFICO NV'),
  ('Equatorial Traders'),
  ('Frey Commodities'),
  ('Group Sopex'),
  ('Hamburg Coffee'),
  ('Ken Gabbay Coffee'),
  ('NKG Bero Italia'),
  ('NV Group Sopex'),
  ('Novadelta'),
  ('Nuova Cipam srl'),
  ('Sucafina UK Ltd.'),
  ('TORREFACÇÃO'),
  ('Touton SA');

-- The annexure row count is the set count on the bill, so the two can never
-- disagree: 17 rows means "17 SET PSC @RS 2500/-".
ALTER TABLE invoice_annexures
    ADD COLUMN drives_quantity BOOLEAN NOT NULL DEFAULT TRUE;
