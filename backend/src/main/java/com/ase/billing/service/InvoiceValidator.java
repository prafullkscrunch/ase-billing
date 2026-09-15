package com.ase.billing.service;

import com.ase.billing.domain.Invoice;
import com.ase.billing.domain.InvoiceItem;
import com.ase.billing.domain.enums.GstTreatment;
import com.ase.billing.exception.ValidationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The checks an invoice must pass before it can leave DRAFT.
 *
 * Each one corresponds to a mistake that actually exists in the historical
 * documents, so none of this is defensive theatre:
 *   - CNF/459 prints a subtotal of 54,850 against items summing to 55,650
 *   - SOA rows 11 and 13 have CGST != SGST from transposed digits
 *   - SOA row 37 is 0.17 out on the 9% calculation
 *   - eleven RI rows fail total = taxable + CGST + SGST
 *   - four bills carry a financial year that contradicts their own date
 *   - eight bills omit the HSN code
 */
@Service
public class InvoiceValidator {

    public void validateForFinalize(Invoice inv, GstTreatment treatment) {
        List<String> problems = new ArrayList<>();

        if (inv.getItems().isEmpty()) {
            problems.add("The invoice has no lines.");
        }

        for (InvoiceItem item : inv.getItems()) {
            if (item.getPrintedDescription() == null || item.getPrintedDescription().isBlank()) {
                problems.add("Line " + item.getSequenceNo() + " has no description.");
            }
            if (item.getAmount() == null) {
                problems.add("Line " + item.getSequenceNo() + " has no amount.");
            }
        }

        // Subtotal must be the sum of the lines, not a typed figure.
        BigDecimal itemSum = inv.getItems().stream()
                .map(i -> i.getAmount() == null ? BigDecimal.ZERO : i.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (inv.getSubtotal().compareTo(itemSum) != 0) {
            problems.add("Subtotal %s does not match the lines, which sum to %s."
                    .formatted(inv.getSubtotal(), itemSum));
        }

        // Tax must be exactly the rate applied to the taxable amount.
        if (treatment == GstTreatment.INTER) {
            BigDecimal expected = MoneyPolicy.taxOf(inv.getTaxableAmount(), inv.getIgstRate());
            if (inv.getIgstAmount().compareTo(expected) != 0) {
                problems.add("IGST is %s but %s%% of %s is %s."
                        .formatted(inv.getIgstAmount(), inv.getIgstRate(),
                                   inv.getTaxableAmount(), expected));
            }
            if (inv.getIgstRate().signum() == 0 && inv.getTaxableAmount().signum() > 0) {
                problems.add("An inter-state invoice cannot carry a zero IGST rate.");
            }
        } else {
            if (inv.getCgstAmount().compareTo(inv.getSgstAmount()) != 0) {
                problems.add("CGST %s and SGST %s must be equal on an intra-state invoice."
                        .formatted(inv.getCgstAmount(), inv.getSgstAmount()));
            }
            BigDecimal expectedCgst = MoneyPolicy.taxOf(inv.getTaxableAmount(), inv.getCgstRate());
            if (inv.getCgstAmount().compareTo(expectedCgst) != 0) {
                problems.add("CGST is %s but %s%% of %s is %s."
                        .formatted(inv.getCgstAmount(), inv.getCgstRate(),
                                   inv.getTaxableAmount(), expectedCgst));
            }
        }

        // Grand total must reconcile.
        BigDecimal expectedTotal = MoneyPolicy.total(inv.getSubtotal()
                .add(inv.getCgstAmount()).add(inv.getSgstAmount()).add(inv.getIgstAmount()));
        if (inv.getGrandTotal().compareTo(expectedTotal) != 0) {
            problems.add("Grand total %s does not equal subtotal plus tax, which is %s."
                    .formatted(inv.getGrandTotal(), expectedTotal));
        }

        // A deduction bigger than the bill would print a negative amount in words.
        if (inv.getNetPayable().signum() < 0) {
            problems.add("The deduction of %s is larger than the bill total of %s."
                    .formatted(inv.getPostTaxAdjustmentAmount(), inv.getGrandTotal()));
        }

        // Financial year is derived; a mismatch means something wrote it by hand.
        String derived = FinancialYear.of(inv.getInvoiceDate());
        if (!derived.equals(inv.getFinancialYear())) {
            problems.add("Financial year %s does not match the invoice date %s, which falls in %s."
                    .formatted(inv.getFinancialYear(), inv.getInvoiceDate(), derived));
        }

        if (inv.getHsnCode() == null || inv.getHsnCode().isBlank()) {
            problems.add("The HSN code is missing.");
        }

        if (inv.getCategory().isRequiresShipment() && inv.getShipment() == null) {
            problems.add("A %s invoice needs shipment details."
                    .formatted(inv.getCategory().getCode()));
        }

        if (inv.getAmountInWords() == null || inv.getAmountInWords().isBlank()) {
            problems.add("The amount in words is empty.");
        }

        if (!problems.isEmpty()) {
            throw new ValidationException("This invoice cannot be finalised yet.", problems);
        }
    }
}
