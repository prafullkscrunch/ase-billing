-- "Empty Container survey fee" was seeded flat (SIMPLE, 400.00) by V5's
-- blanket MANUAL -> SIMPLE conversion, and V11 never picked it up when it
-- moved the other container-scaled charges to PER_TEU. Checked against every
-- CNF bill in "437-480 ASE HC bills.pdf" that has more than one container:
--
--   bill   containers   printed amount   400 x containers   match?
--   439    3            1200             1200                yes
--   467    2             800              800                yes
--   470    2             800              800                yes
--   473    2             800              800                yes
--   475    7            2800             2800                yes
--   070    4            1200             1600                no  (typed wrong on that bill)
--   445    2            3300              800                no  (typed wrong on that bill)
--
-- Five of seven agree exactly with 400 x containers; the two that don't are
-- isolated to those specific historical bills (070 also mislabels its own
-- line "@Rs400 per TEU" right next to a total that doesn't multiply out, so
-- the annotation and the figure disagree with each other, not just with this
-- formula) and are not repeated on any other bill. Treated as bookkeeping
-- slips in those two documents, not evidence of a different formula.

UPDATE service_rates r
  JOIN services s            ON s.id = r.service_id
  JOIN service_categories c  ON c.id = s.category_id
   SET r.effective_to = '2026-03-31'
 WHERE c.code = 'CNF'
   AND s.name = 'Empty Container survey fee'
   AND r.customer_id IS NULL
   AND r.rate = 400.00
   AND r.effective_to IS NULL;

UPDATE services s
  JOIN service_categories c ON c.id = s.category_id
   SET s.calculation_type = 'PER_TEU'
 WHERE c.code = 'CNF'
   AND s.name = 'Empty Container survey fee';

INSERT INTO service_rates (service_id, customer_id, rate, gst_rate, effective_from)
SELECT s.id, NULL, 400.00, 18.00, '2026-04-01'
  FROM services s
  JOIN service_categories c ON c.id = s.category_id
 WHERE c.code = 'CNF'
   AND s.name = 'Empty Container survey fee';
