-- InvoiceAnnexure maps a @OneToOne on invoice_id, but V1 declared only a foreign
-- key. Two annexure rows for one invoice are accepted by the database and then
-- make that invoice permanently unloadable (NonUniqueResultException) and
-- unprintable. This adds the constraint the mapping already assumes.

ALTER TABLE invoice_annexures
    ADD CONSTRAINT uk_invoice_annexures_invoice UNIQUE (invoice_id);
