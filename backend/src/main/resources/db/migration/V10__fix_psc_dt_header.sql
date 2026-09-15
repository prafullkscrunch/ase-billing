-- The user's exact "HC SAMPLE PSC DEATAILS JUL 26.xlsx" has a double space in
-- the second column header: cell C6 is 'PSC  DT', not 'PSC DT'. V1 seeded the
-- single-space version by mistake. Since the user asked for the sample
-- reproduced exactly, this corrects the default and any rows already written
-- with the wrong text (an S bill's annexure created before this migration).
--
-- V1 itself is left alone — Flyway checksums applied migrations, so history is
-- corrected forward, the same way V6 fixed the phytosanitary rate.

ALTER TABLE invoice_annexures
  MODIFY COLUMN col2_header VARCHAR(80) NOT NULL DEFAULT 'PSC  DT';

UPDATE invoice_annexures
  SET col2_header = 'PSC  DT'
  WHERE col2_header = 'PSC DT';
