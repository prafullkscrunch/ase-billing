-- Category "S" was seeded with the generic description "Service", which reads
-- confusingly on the Quotation rate card. Confirmed by the customer: S bills
-- the phytosanitary certificate and service charges for coffee samples sent
-- ahead of the shipment itself (see every ASE/HC/S/... bill's "COFFEE SAMPLES
-- PSC DETAILS" heading) — it is not a general "service" category.

UPDATE service_categories
   SET description = 'Sample invoice (pre-shipment coffee samples, PSC)'
 WHERE code = 'S';
