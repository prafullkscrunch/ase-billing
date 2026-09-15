package com.ase.billing.service;

import com.ase.billing.domain.Customer;
import com.ase.billing.domain.InvoiceSequence;
import com.ase.billing.exception.NotFoundException;
import com.ase.billing.exception.ValidationException;
import com.ase.billing.repo.CustomerRepository;
import com.ase.billing.repo.InvoiceRepository;
import com.ase.billing.repo.InvoiceSequenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Reading and setting the next invoice number.
 *
 * The running number carries on from wherever the paper books left off, and ASE
 * sometimes needs to skip ahead — a number written by hand on a pad, a block
 * reserved for something else. So the next number is shown before a bill is
 * created and can be moved forward deliberately.
 *
 * It can only ever move FORWARD. Moving it back would hand out a number that has
 * already been issued, and an invoice number is never reused.
 */
@Service
public class SequenceAdminService {

    private static final Logger log = LoggerFactory.getLogger(SequenceAdminService.class);

    private final InvoiceSequenceRepository sequences;
    private final InvoiceRepository invoices;
    private final CustomerRepository customers;

    public SequenceAdminService(InvoiceSequenceRepository sequences,
                                InvoiceRepository invoices,
                                CustomerRepository customers) {
        this.sequences = sequences;
        this.invoices = invoices;
        this.customers = customers;
    }

    /**
     * @param financialYear  derived from the date, e.g. "2026-27"
     * @param nextNumber     the number the next invoice will take
     * @param highestUsed    the highest number already issued, or null if none
     * @param previewNumber  what that invoice number will read, e.g. ASE/HC/CNF/518/2026-27
     */
    public record SequenceState(String customerCode, String financialYear,
                                int nextNumber, Integer highestUsed,
                                String previewNumber) {}

    @Transactional(readOnly = true)
    public SequenceState peek(Long customerId, String categoryCode, LocalDate date) {
        Customer customer = customers.findById(customerId)
                .orElseThrow(() -> new NotFoundException("Customer", customerId));
        String fy = FinancialYear.of(date);

        Integer highest = invoices.highestRunningNumber(customerId, fy);
        int next = sequences.findByCustomerIdAndFinancialYear(customerId, fy)
                .map(InvoiceSequence::getNextNumber)
                .orElse(1);

        String preview = "ASE/%s/%s/%d/%s".formatted(
                customer.getCode(), categoryCode == null ? "CNF" : categoryCode, next, fy);

        return new SequenceState(customer.getCode(), fy, next, highest, preview);
    }

    /**
     * Moves the next number forward. Refuses to go backwards or onto a number
     * that has already been issued.
     */
    @Transactional
    public SequenceState jumpTo(Long customerId, LocalDate date, int nextNumber, String categoryCode) {
        Customer customer = customers.findById(customerId)
                .orElseThrow(() -> new NotFoundException("Customer", customerId));
        String fy = FinancialYear.of(date);

        if (nextNumber < 1) {
            throw new ValidationException("An invoice number has to be 1 or more.");
        }

        Integer highest = invoices.highestRunningNumber(customerId, fy);
        if (highest != null && nextNumber <= highest) {
            throw new ValidationException(
                    "Number %d is already issued for %s in %s — the highest so far is %d, so the next one has to be %d or higher."
                            .formatted(nextNumber, customer.getCode(), fy, highest, highest + 1));
        }

        Optional<InvoiceSequence> existing =
                sequences.findByCustomerIdAndFinancialYear(customerId, fy);

        InvoiceSequence seq = existing.orElseGet(
                () -> sequences.save(new InvoiceSequence(customer, fy, nextNumber)));

        if (nextNumber < seq.getNextNumber()) {
            throw new ValidationException(
                    "The next number is already %d. It can be moved forward but not back, because %d may have been handed out."
                            .formatted(seq.getNextNumber(), nextNumber));
        }

        seq.setNextNumber(nextNumber);
        sequences.save(seq);
        log.info("Sequence for customer {} in {} jumped to {}.", customerId, fy, nextNumber);

        return peek(customerId, categoryCode, date);
    }
}
