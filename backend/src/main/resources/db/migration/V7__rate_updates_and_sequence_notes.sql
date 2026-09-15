-- Records where a rate came from, so a rate that was changed while billing can
-- be told apart from one set deliberately in the rate master.
--
-- ASE's rates move between shipments (CNF/400 and CNF/430 bill VGM at 750 and
-- 500 per TEU in the same month). When the operator types a different rate on a
-- line, that becomes the rate the next bill suggests — but the invoice keeps a
-- copy of what it was billed at, so nothing already issued changes.

ALTER TABLE service_rates
    ADD COLUMN source          VARCHAR(24) NOT NULL DEFAULT 'MASTER',
    ADD COLUMN set_from_invoice BIGINT NULL,
    ADD COLUMN set_by          VARCHAR(60) NULL;

ALTER TABLE service_rates
    ADD CONSTRAINT fk_service_rates_invoice
    FOREIGN KEY (set_from_invoice) REFERENCES invoices(id);
