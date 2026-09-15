package com.ase.billing.service;

import com.ase.billing.domain.Customer;
import com.ase.billing.domain.InvoiceSequence;
import com.ase.billing.domain.ServiceCategory;
import com.ase.billing.repo.InvoiceSequenceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Produces ASE/{customerCode}/{categoryCode}/{runningNumber}/{financialYear}.
 *
 * The running number is shared across categories within a customer and financial
 * year. Every shipment in both PDFs bills as a consecutive CNF/T pair
 * (CNF/439 + T/440, CNF/441 + T/442), so a per-category sequence would break the
 * pairing. Do not "fix" this by splitting the series.
 *
 * The row is locked pessimistically for the life of the transaction, so two
 * concurrent creates queue rather than collide. MAX(invoice_number)+1 is not safe.
 */
@Service
public class InvoiceNumberService {

    private final EntityManager em;
    private final InvoiceSequenceRepository sequences;

    public InvoiceNumberService(EntityManager em, InvoiceSequenceRepository sequences) {
        this.em = em;
        this.sequences = sequences;
    }

    public record Allocated(String invoiceNumber, int runningNumber, String financialYear) {}

    @Transactional(propagation = Propagation.MANDATORY)
    public Allocated allocate(Customer customer, ServiceCategory category, LocalDate invoiceDate) {
        String fy = FinancialYear.of(invoiceDate);

        InvoiceSequence seq = sequences
                .findByCustomerIdAndFinancialYear(customer.getId(), fy)
                .orElseGet(() -> sequences.save(new InvoiceSequence(customer, fy, 1)));

        // Re-read under a write lock so the increment is serialised.
        em.refresh(seq, LockModeType.PESSIMISTIC_WRITE);

        int running = seq.getNextNumber();
        seq.setNextNumber(running + 1);

        String number = "ASE/%s/%s/%d/%s".formatted(
                customer.getCode(), category.getCode(), running, fy);

        return new Allocated(number, running, fy);
    }

    /**
     * For back-entering a historical bill that already has a number on paper.
     * Keeps the sequence ahead of whatever was entered so future numbers never collide.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserveHistorical(Customer customer, String financialYear, int runningNumber) {
        InvoiceSequence seq = sequences
                .findByCustomerIdAndFinancialYear(customer.getId(), financialYear)
                .orElseGet(() -> sequences.save(new InvoiceSequence(customer, financialYear, 1)));
        em.refresh(seq, LockModeType.PESSIMISTIC_WRITE);
        if (runningNumber >= seq.getNextNumber()) {
            seq.setNextNumber(runningNumber + 1);
        }
    }
}
