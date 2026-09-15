-- Two changes, both driven by how ASE's Word templates actually work.
--
-- 1. Amount from rate x quantity.
--    V2 seeded nearly every CNF charge as MANUAL, so the operator had to retype
--    a figure that the rate master already knows. A flat per-bill charge is
--    SIMPLE with quantity 1: amount = 1 x 3000 = 3000, and billing three
--    containers is a quantity change, not arithmetic in someone's head.
--
-- 2. The standard CNF charge sheet.
--    Every CNF bill in the Word templates carries the same fourteen charges in
--    the same order. They are marked standard here so a new CNF bill starts
--    pre-filled, and any line can still be removed before finalising.

ALTER TABLE services
    ADD COLUMN is_standard      BOOLEAN       NOT NULL DEFAULT FALSE,
    ADD COLUMN default_quantity DECIMAL(12,3) NOT NULL DEFAULT 1.000;

-- Flat charges: amount = quantity x rate.
UPDATE services
   SET calculation_type = 'SIMPLE'
 WHERE calculation_type = 'MANUAL';

-- The standard CNF sheet, in the order it prints on ASE/HC/CNF/516.
UPDATE services s
   JOIN service_categories c ON c.id = s.category_id
   SET s.is_standard = TRUE,
       s.sort_order = CASE s.name
           WHEN 'Pre shipment export documentation charges'              THEN 10
           WHEN 'ICO/Permit, ROC Submission & Self-sealing Documentation' THEN 20
           WHEN 'Expenses on Phytosanitary certificate'                   THEN 30
           WHEN 'Certificate of weight & quality'                         THEN 40
           WHEN 'Certificate of origin'                                   THEN 50
           WHEN 'JSW containers and Seal Scanning Charges'                THEN 60
           WHEN 'EDI charges'                                             THEN 70
           WHEN 'Empty Container survey fee'                              THEN 80
           WHEN 'Customs Clearance charges'                               THEN 90
           WHEN 'Port Expenses'                                           THEN 100
           WHEN 'VGM expenses'                                            THEN 110
           WHEN 'LO/LO'                                                   THEN 120
           WHEN 'CHA Service charges'                                     THEN 130
           WHEN 'Post Shipment Document charges'                          THEN 140
           ELSE s.sort_order END
 WHERE c.code = 'CNF'
   AND s.name IN (
       'Pre shipment export documentation charges',
       'ICO/Permit, ROC Submission & Self-sealing Documentation',
       'Expenses on Phytosanitary certificate',
       'Certificate of weight & quality',
       'Certificate of origin',
       'JSW containers and Seal Scanning Charges',
       'EDI charges',
       'Empty Container survey fee',
       'Customs Clearance charges',
       'Port Expenses',
       'VGM expenses',
       'LO/LO',
       'CHA Service charges',
       'Post Shipment Document charges');

-- The standard sheet prints a plain charge name with no rate arithmetic in it.
UPDATE services SET print_template = name WHERE is_standard = TRUE;

-- Rates for the standard sheet, as billed on ASE/HC/CNF/516 (1X20, total 19,950).
INSERT INTO service_rates (service_id, customer_id, rate, gst_rate, effective_from)
SELECT s.id, NULL, r.rate, 18.00, '2026-04-01'
  FROM services s
  JOIN service_categories c ON c.id = s.category_id
  JOIN (
    SELECT 'Certificate of weight & quality' n, 1500.00 rate UNION ALL
    SELECT 'Empty Container survey fee', 400.00 UNION ALL
    SELECT 'ICO/Permit, ROC Submission & Self-sealing Documentation', 1000.00 UNION ALL
    SELECT 'Expenses on Phytosanitary certificate', 3000.00
  ) r ON r.n = s.name
 WHERE c.code = 'CNF'
   AND NOT EXISTS (SELECT 1 FROM service_rates x
                    WHERE x.service_id = s.id AND x.customer_id IS NULL);

-- A signature block is printed on every bill; this records whether to draw it.
ALTER TABLE company_settings
    ADD COLUMN show_signature BOOLEAN NOT NULL DEFAULT TRUE;
