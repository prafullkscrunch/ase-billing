-- The CNF phytosanitary charge was seeded at 2,500 instead of 3,000.
--
-- V2 joined its rate list to services by name, and MySQL's default collation
-- (utf8mb4_unicode_ci) is case-insensitive, so 'Expenses on Phytosanitary
-- Certificate' — the S-category PSC-set charge at 2,500 per set — also matched
-- the CNF charge 'Expenses on Phytosanitary certificate', which ASE bills at
-- 3,000. The standard CNF sheet came to 19,450 instead of the 19,950 on
-- ASE/HC/CNF/516.
--
-- Closes the wrong rate rather than deleting it, so any invoice already issued
-- keeps the figure it was billed with.

UPDATE service_rates r
  JOIN services s            ON s.id = r.service_id
  JOIN service_categories c  ON c.id = s.category_id
   SET r.effective_to = '2026-03-31'
 WHERE c.code = 'CNF'
   AND s.name = 'Expenses on Phytosanitary certificate'
   AND r.customer_id IS NULL
   AND r.rate = 2500.00
   AND r.effective_to IS NULL;

INSERT INTO service_rates (service_id, customer_id, rate, gst_rate, effective_from)
SELECT s.id, NULL, 3000.00, 18.00, '2026-04-01'
  FROM services s
  JOIN service_categories c ON c.id = s.category_id
 WHERE c.code = 'CNF'
   AND s.name = 'Expenses on Phytosanitary certificate';
