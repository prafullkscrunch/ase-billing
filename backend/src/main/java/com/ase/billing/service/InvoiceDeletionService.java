package com.ase.billing.service;

import com.ase.billing.domain.AuditLog;
import com.ase.billing.domain.Invoice;
import com.ase.billing.domain.InvoiceSequence;
import com.ase.billing.domain.enums.InvoiceStatus;
import com.ase.billing.exception.NotFoundException;
import com.ase.billing.exception.ValidationException;
import com.ase.billing.repo.AuditLogRepository;
import com.ase.billing.repo.InvoiceRepository;
import com.ase.billing.repo.InvoiceSequenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deleting an invoice and freeing its number.
 *
 * A draft is work in progress and deletes without ceremony. A FINALISED invoice
 * is an issued tax document: under GST, numbers are meant to stay unique and
 * sequential, so deleting one and reissuing the number is a real compliance
 * risk. ASE chose to allow it, so it is allowed — but it requires a stated
 * reason and everything about it is written to the audit log first, which is
 * what makes the decision defensible if it is ever questioned.
 *
 * The number goes back into circulation when the deleted invoice held the
 * highest number for its customer and financial year. Deleting from the middle
 * of the run leaves a gap, which the operator can fill by setting the number
 * explicitly when creating the replacement.
 */
@Service
public class InvoiceDeletionService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceDeletionService.class);

    private final InvoiceRepository invoices;
    private final InvoiceSequenceRepository sequences;
    private final AuditLogRepository audit;

    public InvoiceDeletionService(InvoiceRepository invoices,
                                  InvoiceSequenceRepository sequences,
                                  AuditLogRepository audit) {
        this.invoices = invoices;
        this.sequences = sequences;
        this.audit = audit;
    }

    /** What happened, so the UI can say whether the number came back. */
    public record Deleted(String invoiceNumber, InvoiceStatus wasStatus,
                          boolean numberFreed, Integer freedNumber) {}

    @Transactional
    public Deleted delete(Long id, String reason, String username) {
        Invoice inv = invoices.findById(id)
                .orElseThrow(() -> new NotFoundException("Invoice", id));

        InvoiceStatus was = inv.getStatus();
        if (was != InvoiceStatus.DRAFT && (reason == null || reason.isBlank())) {
            throw new ValidationException(
                    "Invoice %s is %s. Deleting an issued bill needs a reason, because its number "
                            .formatted(inv.getInvoiceNumber(), was.name().toLowerCase())
                    + "has already been reported and reusing it is a GST risk.");
        }

        // Record before deleting, so the trail survives the row.
        AuditLog log = new AuditLog();
        log.setEntityType("Invoice");
        log.setEntityId(inv.getId());
        log.setAction("DELETE");
        log.setUsername(username);
        log.setDetail("""
                Deleted %s (%s) dated %s for %s. Taxable %s, grand total %s. Reason: %s"""
                .formatted(inv.getInvoiceNumber(), was, inv.getInvoiceDate(),
                        inv.getCustomer().getName(), inv.getTaxableAmount(),
                        inv.getGrandTotal(), reason == null || reason.isBlank() ? "(draft)" : reason));
        audit.save(log);

        Long customerId = inv.getCustomer().getId();
        String fy = inv.getFinancialYear();
        int running = inv.getRunningNumber();
        String number = inv.getInvoiceNumber();

        invoices.delete(inv);
        invoices.flush();

        boolean freed = rollBackSequenceIfHighest(customerId, fy, running);
        if (freed) {
            log.info("Deleted {} ({}) by {}; number {} is free again for customer {} in {}.",
                    number, was, username, running, customerId, fy);
        } else {
            log.info("Deleted {} ({}) by {}; number {} is NOT free (a later invoice exists) — "
                    + "reuse it by setting the number explicitly on the next bill.",
                    number, was, username, running);
        }
        return new Deleted(number, was, freed, freed ? running : null);
    }

    /**
     * Puts the number back only when nothing above it exists. Rolling back past a
     * later invoice would hand out a number that is already in use.
     */
    private boolean rollBackSequenceIfHighest(Long customerId, String fy, int running) {
        Integer highest = invoices.highestRunningNumber(customerId, fy);
        if (highest != null && highest >= running) {
            return false;   // a later invoice exists; this leaves a gap instead
        }
        return sequences.findByCustomerIdAndFinancialYear(customerId, fy)
                .map(seq -> {
                    if (seq.getNextNumber() > running) {
                        seq.setNextNumber(running);
                        sequences.save(seq);
                        return true;
                    }
                    return false;
                })
                .orElse(false);
    }

    /** True when this number is free to be issued for that customer and year. */
    @Transactional(readOnly = true)
    public boolean isNumberAvailable(Long customerId, String financialYear, int running) {
        return !invoices.existsByCustomerIdAndFinancialYearAndRunningNumber(
                customerId, financialYear, running);
    }
}
