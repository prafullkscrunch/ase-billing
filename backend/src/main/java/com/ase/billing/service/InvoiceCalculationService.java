package com.ase.billing.service;

import com.ase.billing.domain.Invoice;
import com.ase.billing.domain.InvoiceItem;
import com.ase.billing.domain.enums.CalculationType;
import com.ase.billing.domain.enums.GstTreatment;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Computes every monetary field on an invoice from its items. Call this on every
 * save and again on finalise; never trust a total supplied by the client.
 *
 * This is what catches the kind of error bill ASE/HC/CNF/459 carries: its items
 * sum to 55,650 but the printed subtotal reads 54,850, while the GST on it
 * (5,008.50) was in fact computed on 55,650. The printed subtotal was typed.
 */
@Service
public class InvoiceCalculationService {

    /** The combined GST rate applied when nothing more specific is configured. */
    private static final BigDecimal DEFAULT_COMBINED_GST = new BigDecimal("18.00");

    /** Amount for one line, from its calculation type. Returns the stored amount for MANUAL. */
    public BigDecimal amountFor(InvoiceItem item) {
        BigDecimal rate = item.getRate() == null ? BigDecimal.ZERO : item.getRate();
        BigDecimal qty  = item.getQuantity() == null ? BigDecimal.ONE : item.getQuantity();
        BigDecimal days = item.getDays() == null ? BigDecimal.ONE : BigDecimal.valueOf(item.getDays());
        // Zero for every line except the few (ICO/Permit, the phytosanitary and
        // weight-and-quality certificates) that are not purely proportional to
        // container count: ASE bills those at a fixed amount plus so-much per
        // container, e.g. 500 + 500 x TEU, not 500 x TEU.
        BigDecimal base = item.getBaseAmount() == null ? BigDecimal.ZERO : item.getBaseAmount();

        CalculationType type = Objects.requireNonNullElse(item.getCalculationType(), CalculationType.MANUAL);

        // A PER_TEU line with no TEU is a data fault, not a one-TEU line. Defaulting
        // it to 1 silently under-bills a multi-container shipment, so refuse instead.
        if ((type == CalculationType.PER_TEU || type == CalculationType.PER_TEU_PER_DAY)
                && item.getTeu() == null) {
            throw new IllegalStateException(
                    "TEU is required for a " + type + " line: " + item.getPrintedDescription());
        }
        BigDecimal teu = item.getTeu();

        return switch (type) {
            case SIMPLE, PER_SET -> MoneyPolicy.money(base.add(qty.multiply(rate)));
            case PER_TEU         -> MoneyPolicy.money(base.add(teu.multiply(rate)));
            case PER_TEU_PER_DAY -> MoneyPolicy.money(base.add(teu.multiply(days).multiply(rate)));
            case MANUAL          -> MoneyPolicy.money(item.getAmount());
        };
    }

    /**
     * Recomputes subtotal, taxable, CGST/SGST or IGST, grand total, net payable
     * and the amount in words, and writes them onto the invoice.
     */
    public void recalculate(Invoice invoice, GstTreatment treatment) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxable  = BigDecimal.ZERO;

        for (InvoiceItem item : invoice.getItems()) {
            BigDecimal amount = amountFor(item);
            item.setAmount(amount);
            subtotal = subtotal.add(amount);
            if (item.isTaxable()) {
                taxable = taxable.add(amount);
            }
        }

        invoice.setSubtotal(MoneyPolicy.money(subtotal));
        invoice.setTaxableAmount(MoneyPolicy.money(taxable));

        if (treatment == GstTreatment.INTER) {
            // Inter-state: one IGST line at the combined rate, CGST and SGST both nil.
            // Deriving the rate here is what stops an inter-state bill going out with
            // no tax at all when igstRate was left at its zero default.
            BigDecimal igstRate = invoice.getIgstRate();
            if (igstRate == null || igstRate.signum() == 0) {
                BigDecimal combined = nullToZero(invoice.getCgstRate()).add(nullToZero(invoice.getSgstRate()));
                igstRate = combined.signum() > 0 ? combined : DEFAULT_COMBINED_GST;
                invoice.setIgstRate(igstRate);
            }
            invoice.setIgstAmount(MoneyPolicy.taxOf(invoice.getTaxableAmount(), igstRate));
            invoice.setCgstRate(BigDecimal.ZERO);
            invoice.setSgstRate(BigDecimal.ZERO);
            invoice.setCgstAmount(BigDecimal.ZERO);
            invoice.setSgstAmount(BigDecimal.ZERO);
        } else {
            invoice.setCgstAmount(MoneyPolicy.taxOf(invoice.getTaxableAmount(), invoice.getCgstRate()));
            invoice.setSgstAmount(MoneyPolicy.taxOf(invoice.getTaxableAmount(), invoice.getSgstRate()));
            invoice.setIgstRate(BigDecimal.ZERO);
            invoice.setIgstAmount(BigDecimal.ZERO);
        }

        // Grand total is built on the SUBTOTAL, not the taxable amount. A line marked
        // non-taxable is still payable; excluding it from the tax base must not remove
        // it from what the customer owes.
        BigDecimal grand = invoice.getSubtotal()
                .add(invoice.getCgstAmount())
                .add(invoice.getSgstAmount())
                .add(invoice.getIgstAmount());
        invoice.setGrandTotal(MoneyPolicy.total(grand));

        // Applied after GST. Bill ASE/HC/S/438: 60,180.00 - 3,758.00 = 56,422.00.
        // The SOA reports grandTotal, not netPayable.
        BigDecimal adjustment = nullToZero(invoice.getPostTaxAdjustmentAmount());
        invoice.setNetPayable(MoneyPolicy.total(invoice.getGrandTotal().subtract(adjustment)));

        // The bill prints net payable when an adjustment exists, otherwise the grand total.
        BigDecimal printed = adjustment.signum() == 0 ? invoice.getGrandTotal() : invoice.getNetPayable();
        invoice.setAmountInWords(AmountInWords.of(printed));
    }

    private static BigDecimal nullToZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
