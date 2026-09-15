package com.ase.billing.service;

import com.ase.billing.domain.Invoice;
import com.ase.billing.domain.InvoiceItem;
import com.ase.billing.domain.Shipment;
import com.ase.billing.domain.enums.CalculationType;
import com.ase.billing.domain.enums.GstTreatment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression tests for defects found in review. Each one previously produced a
 * wrong number on a tax document; these lock in the corrected behaviour.
 */
class CalculationRegressionTest {

    private final InvoiceCalculationService calc = new InvoiceCalculationService();

    private static InvoiceItem line(String desc, String amount, boolean taxable) {
        InvoiceItem i = new InvoiceItem();
        i.setPrintedDescription(desc);
        i.setCalculationType(CalculationType.MANUAL);
        i.setAmount(new BigDecimal(amount));
        i.setTaxable(taxable);
        return i;
    }

    @Test
    @DisplayName("an inter-state customer is billed IGST at the combined rate")
    void interStateBillsIgst() {
        Invoice inv = new Invoice();
        inv.addItem(line("Customs Clearance charges", "52950", true));

        calc.recalculate(inv, GstTreatment.INTER);

        // Was 0.00 before the fix: igstRate defaulted to zero and nothing set it.
        assertThat(inv.getIgstRate()).isEqualByComparingTo("18.00");
        assertThat(inv.getIgstAmount()).isEqualByComparingTo("9531.00");
        assertThat(inv.getCgstAmount()).isEqualByComparingTo("0.00");
        assertThat(inv.getSgstAmount()).isEqualByComparingTo("0.00");
        assertThat(inv.getGrandTotal()).isEqualByComparingTo("62481");
    }

    @Test
    @DisplayName("a non-taxable line is excluded from tax but still payable")
    void nonTaxableLineStillPayable() {
        Invoice inv = new Invoice();
        inv.addItem(line("Customs Clearance charges", "10000", true));
        inv.addItem(line("Reimbursement, no GST", "2000", false));

        calc.recalculate(inv, GstTreatment.INTRA);

        assertThat(inv.getSubtotal()).isEqualByComparingTo("12000.00");
        assertThat(inv.getTaxableAmount()).isEqualByComparingTo("10000.00");
        assertThat(inv.getCgstAmount()).isEqualByComparingTo("900.00");
        // Was 11800 before the fix, silently dropping the 2,000 reimbursement.
        assertThat(inv.getGrandTotal()).isEqualByComparingTo("13800");
    }

    @Test
    @DisplayName("Shipment derives teu and soaMarkNo before it is persisted")
    void shipmentDerivesItsOwnFields() {
        Shipment s = new Shipment();
        s.setHcInvoiceNumber("ES/HC/2027/094");
        s.setIcoMarkFull("14/1850/2026/292-295");
        s.setContainerCount(3);
        s.setContainerSize("20");

        s.deriveFields();   // @PrePersist / @PreUpdate

        // Both columns are NOT NULL; before the fix every save threw.
        assertThat(s.getTeu()).isEqualByComparingTo("3.00");
        assertThat(s.getSoaMarkNo()).isEqualTo("292");

        Shipment forty = new Shipment();
        forty.setContainerCount(2);
        forty.setContainerSize("40");
        forty.setIcoMarkFull("14/1850/2026/283");
        forty.deriveFields();
        assertThat(forty.getTeu()).isEqualByComparingTo("4.00");
        assertThat(forty.getSoaMarkNo()).isEqualTo("283");
    }

    @Test
    @DisplayName("a negative amount still renders words instead of a blank")
    void negativeAmountInWords() {
        // Was " only" before the fix, and that string went onto the bill.
        assertThat(AmountInWords.of(new BigDecimal("-9820")))
                .isEqualTo("Minus nine thousand eight hundred and twenty only");
    }

    @Test
    @DisplayName("a PER_TEU line with no TEU is refused rather than billed as 1 TEU")
    void perTeuWithNullTeuIsRefused() {
        InvoiceItem fumigation = new InvoiceItem();
        fumigation.setPrintedDescription("Fumigation charges per TEU @ RS 6000/-");
        fumigation.setCalculationType(CalculationType.PER_TEU);
        fumigation.setTeu(null);
        fumigation.setRate(new BigDecimal("6000"));

        Invoice inv = new Invoice();
        inv.addItem(fumigation);

        // Previously billed 6,000 for a 3x20 shipment: 12,000 under-billed, silently.
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> calc.recalculate(inv, GstTreatment.INTRA))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TEU is required");
    }

    @Test
    @DisplayName("ICO/Permit, phytosanitary and weight-and-quality certificates scale with container count")
    void containerScaledChargesMatchRealBills() {
        // Pinned to 437-480 ASE HC bills.pdf. Each was flat at its 1-container
        // amount before the fix, which under-billed every multi-container CNF.
        //                                          containers: 1      2      3      7
        assertScaled("ICO/Permit, ROC Submission & Self-sealing Documentation",
                new BigDecimal("500"), new BigDecimal("500"),
                new int[]{1, 2, 3, 7}, new String[]{"1000", "1500", "2000", "4000"});

        assertScaled("Expenses on Phytosanitary certificate",
                new BigDecimal("2250"), new BigDecimal("750"),
                new int[]{1, 2, 3, 7}, new String[]{"3000", "3750", "4500", "7500"});

        assertScaled("Certificate of weight & quality",
                new BigDecimal("750"), new BigDecimal("750"),
                new int[]{1, 2, 7}, new String[]{"1500", "2250", "6000"});
    }

    private void assertScaled(String desc, BigDecimal base, BigDecimal rate,
                              int[] containers, String[] expected) {
        for (int i = 0; i < containers.length; i++) {
            InvoiceItem item = new InvoiceItem();
            item.setPrintedDescription(desc);
            item.setCalculationType(CalculationType.PER_TEU);
            item.setTeu(BigDecimal.valueOf(containers[i]));
            item.setRate(rate);
            item.setBaseAmount(base);

            assertThat(calc.amountFor(item))
                    .as("%s at %d containers", desc, containers[i])
                    .isEqualByComparingTo(expected[i]);
        }
    }

    @Test
    @DisplayName("addItem does not reuse a sequence number after a removal")
    void sequenceNumbersDoNotCollide() {
        Invoice inv = new Invoice();
        InvoiceItem a = line("A", "100", true);
        InvoiceItem b = line("B", "200", true);
        InvoiceItem c = line("C", "300", true);
        inv.addItem(a); inv.addItem(b); inv.addItem(c);

        inv.getItems().remove(b);                  // raw removal, no renumber
        InvoiceItem d = line("D", "400", true);
        inv.addItem(d);

        // Was 3 before the fix, colliding with item C.
        assertThat(d.getSequenceNo()).isEqualTo(4);
        assertThat(inv.getItems()).extracting(InvoiceItem::getSequenceNo)
                .doesNotHaveDuplicates();

        inv.renumberItems();
        assertThat(inv.getItems()).extracting(InvoiceItem::getSequenceNo)
                .containsExactly(1, 2, 3);
    }
}
