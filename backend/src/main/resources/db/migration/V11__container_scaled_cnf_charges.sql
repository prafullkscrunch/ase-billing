-- ICO/Permit, the phytosanitary certificate and the weight & quality
-- certificate all grow with container count on the real bills (437-480 ASE HC
-- bills.pdf), but the standard sheet had them fixed at their 1-container
-- amount no matter how many containers a shipment has. Reading every CNF bill
-- in that file:
--
--   containers      1      2      3      7
--   ICO/Permit   1,000  1,500  2,000  4,000   ->  500 + 500  x containers
--   Phytosanitary 3,000  3,750  4,500  7,500   -> 2,250 + 750  x containers
--   Weight & qty 1,500  2,250    -    6,000   ->   750 + 750  x containers
--
-- Every one of those 21 bills fits its line exactly, with one exception: a
-- handful of 2-container bills show a higher phytosanitary figure (4,500 or
-- 5,250 instead of 3,750) whenever the line reads "...with addl. declaration"
-- -- that is an extra document needed for that particular shipment, already
-- covered by the separate 'Phytosanitary additional declaration' line in the
-- master list, added by hand when it applies. It is not part of this formula.
--
-- quantity x rate alone cannot express the fixed +500/+2,250/+750 component,
-- only the per-container part, so a base_amount is added alongside rate.

ALTER TABLE services
    ADD COLUMN base_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00;

ALTER TABLE invoice_items
    ADD COLUMN base_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00;

-- Move the three charges from a flat SIMPLE amount to PER_TEU (quantity comes
-- from the shipment's container count, same as Customs Clearance / Port
-- Expenses / VGM / LO-LO / CHA Service already do), and record each one's
-- fixed component.
UPDATE services s
  JOIN service_categories c ON c.id = s.category_id
   SET s.calculation_type = 'PER_TEU',
       s.base_amount = CASE s.name
           WHEN 'ICO/Permit, ROC Submission & Self-sealing Documentation' THEN 500.00
           WHEN 'Expenses on Phytosanitary certificate'                   THEN 2250.00
           WHEN 'Certificate of weight & quality'                         THEN 750.00
       END
 WHERE c.code = 'CNF'
   AND s.name IN (
       'ICO/Permit, ROC Submission & Self-sealing Documentation',
       'Expenses on Phytosanitary certificate',
       'Certificate of weight & quality');

-- The rate on file for each was the correct 1-container total (1000/3000/1500);
-- now that base_amount carries the fixed part, rate must hold only the
-- per-container increment (500/750/750). Close the old rate rather than
-- deleting it, so any invoice already issued keeps the figure it was billed
-- with, the same way V6 corrected the phytosanitary rate.
UPDATE service_rates r
  JOIN services s            ON s.id = r.service_id
  JOIN service_categories c  ON c.id = s.category_id
   SET r.effective_to = '2026-03-31'
 WHERE c.code = 'CNF'
   AND s.name IN (
       'ICO/Permit, ROC Submission & Self-sealing Documentation',
       'Expenses on Phytosanitary certificate',
       'Certificate of weight & quality')
   AND r.customer_id IS NULL
   AND r.effective_to IS NULL;

INSERT INTO service_rates (service_id, customer_id, rate, gst_rate, effective_from)
SELECT s.id, NULL, r.increment, 18.00, '2026-04-01'
  FROM services s
  JOIN service_categories c ON c.id = s.category_id
  JOIN (
    SELECT 'ICO/Permit, ROC Submission & Self-sealing Documentation' n, 500.00 increment UNION ALL
    SELECT 'Expenses on Phytosanitary certificate', 750.00 UNION ALL
    SELECT 'Certificate of weight & quality', 750.00
  ) r ON r.n = s.name
 WHERE c.code = 'CNF';
