-- ICO/Permit, ROC Submission & Self-sealing Documentation moves from
-- "500 + 500 x TEU" (1,000 at 1 container) to "1,000 + 500 x TEU"
-- (1,500 at 1 container, +500 for every container after that) — confirmed
-- directly by the customer: "the first TEU is 1500 and after that it is 500
-- more each container, [effective] today."
--
-- Every rate mentioned to us so far applies only to Hangal Coffee Exporting
-- (the sole customer in this data), so this is recorded as the general/house
-- rate rather than a customer-specific override — same as every other rate
-- already in these migrations.
--
-- base_amount lives on the service record itself, not in service_rates, so
-- it isn't date-versioned the way `rate` is — but that's harmless here: an
-- invoice item copies its own base_amount at creation time (see
-- InvoiceService.buildItem / the frontend's lineFromService), so every
-- invoice already issued keeps whatever figure it was billed with. Only
-- lines created from this point on pick up the new 1,000 base.
--
-- The per-TEU rate itself (500) is unchanged — only the fixed part moves.

UPDATE services s
  JOIN service_categories c ON c.id = s.category_id
   SET s.base_amount = 1000.00
 WHERE c.code = 'CNF'
   AND s.name = 'ICO/Permit, ROC Submission & Self-sealing Documentation';
