-- NMPA to Baikampady is billed at 6,000 per container, not the 12,000 seeded
-- from the Mangalore-Baikampady leg in V2. Only this one route changes; the
-- other three keep the prices they have.
--
-- Invoices already issued are not touched. Every transport line stores the rate
-- it was billed at when it was created, so an old bill still reads 12,000.
--
-- Route prices are editable from the transport bill screen now, so this is the
-- starting price rather than a fixed one.

UPDATE transport_routes
   SET rate_per_container = 6000.00
 WHERE name = 'NMPA-Baikampady';
