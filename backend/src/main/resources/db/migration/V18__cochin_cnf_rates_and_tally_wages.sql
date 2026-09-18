-- Cochin-bound shipments (confirmed: max ~10/year) need different CNF rates
-- for six of the standard charges, plus one Cochin-only charge (Tally
-- Wages) that Mangalore never bills at all. Cross-checked against
-- ASE_HC-Quote_for_handling_Charges-COK___MLR.xlsx.
--
-- Mangalore rate check, done first as agreed ("first tell which ur
-- changing"): of the six affected charges, four already match the quote's
-- Mangalore column exactly (EDI 1000, Customs Clearance 1750, Port
-- Expenses 1250, CHA 2500) — no change. Two do not:
--
--   charge                     currently live   quote's Mangalore figure
--   VGM expenses               750              500
--   Empty Container survey fee 400 (per TEU)     500
--
-- Instruction: keep whichever is higher. VGM's live rate (750) is already
-- higher than the quote's figure (500) — left untouched. Empty Container
-- survey fee's quote figure (500) is higher than the live rate (400) — the
-- **only** Mangalore rate this migration actually changes.

-- ---- Empty Container survey fee: 400 -> 500 (Mangalore/global rate) -------
UPDATE service_rates r
  JOIN services s            ON s.id = r.service_id
  JOIN service_categories c  ON c.id = s.category_id
   SET r.effective_to = CURDATE() - INTERVAL 1 DAY
 WHERE c.code = 'CNF'
   AND s.name = 'Empty Container survey fee'
   AND r.customer_id IS NULL
   AND r.effective_to IS NULL;

INSERT INTO service_rates (service_id, customer_id, rate, gst_rate, effective_from)
SELECT s.id, NULL, 500.00, 18.00, CURDATE()
  FROM services s
  JOIN service_categories c ON c.id = s.category_id
 WHERE c.code = 'CNF'
   AND s.name = 'Empty Container survey fee';

-- ---- Cochin-only flag: a charge that never appears on a Mangalore bill ----
ALTER TABLE services
  ADD COLUMN cochin_only BOOLEAN NOT NULL DEFAULT FALSE AFTER is_standard;

-- ---- Tally Wages: new CNF charge, Cochin shipments only -------------------
-- Flat charge, same shape as the other flat CNF lines (EDI charges,
-- Certificate of origin): MANUAL calculation type, no unit, operator sees
-- the suggested rate but the amount is typed like any other flat line.
INSERT INTO services (category_id, name, print_template, calculation_type, default_unit, sort_order, is_standard, cochin_only)
SELECT c.id, 'Tally Wages', 'Tally Wages', 'MANUAL', NULL, 75, TRUE, TRUE
  FROM service_categories c
 WHERE c.code = 'CNF';

INSERT INTO service_rates (service_id, customer_id, rate, gst_rate, effective_from)
SELECT s.id, NULL, 500.00, 18.00, CURDATE()
  FROM services s
  JOIN service_categories c ON c.id = s.category_id
 WHERE c.code = 'CNF'
   AND s.name = 'Tally Wages';

-- ---- Cochin rate overrides for the standard CNF sheet ----------------------
-- Only for the charges whose Cochin rate differs from the Mangalore/global
-- one. One row per service; no date history needed here (unlike
-- service_rates) since a Cochin bill always uses today's Cochin rate — the
-- ~10/year volume doesn't justify carrying old-Cochin-rate history the way
-- ordinary global rate changes do.
CREATE TABLE cochin_rate_overrides (
  service_id  BIGINT PRIMARY KEY,
  rate        DECIMAL(14,2) NOT NULL,
  base_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  CONSTRAINT fk_cochin_override_service FOREIGN KEY (service_id) REFERENCES services(id)
) ENGINE=InnoDB;

INSERT INTO cochin_rate_overrides (service_id, rate)
SELECT s.id, v.rate FROM services s
  JOIN service_categories c ON c.id = s.category_id
  JOIN (
    SELECT 'EDI charges' AS name, 1500.00 AS rate UNION ALL
    SELECT 'Empty Container survey fee', 800.00 UNION ALL
    SELECT 'Customs Clearance charges', 2500.00 UNION ALL
    SELECT 'Port Expenses', 1750.00 UNION ALL
    SELECT 'VGM expenses', 750.00 UNION ALL
    SELECT 'CHA Service charges', 3500.00
  ) v ON v.name = s.name
 WHERE c.code = 'CNF';

-- ---- Cochin transport route -------------------------------------------------
-- Mirrors the existing 'Mangalore-Kushalnagar' row exactly, same shape,
-- just from Cochin instead. Shows up in the existing route dropdown
-- automatically — no application code change needed for transport at all.
INSERT INTO transport_routes (name, origin, destination, print_template, rate_per_container)
VALUES ('Cochin-Kushalnagar', 'Cochin', 'KUSHALNAGAR',
        'Transportation Charges from Cochin to KUSHALNAGAR and back', 59000.00);
