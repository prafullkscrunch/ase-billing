package com.ase.billing.service;

import com.ase.billing.domain.Invoice;
import com.ase.billing.domain.InvoiceItem;
import com.ase.billing.domain.enums.CalculationType;
import com.ase.billing.domain.enums.GstTreatment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every figure here is taken from a real bill in the two supplied PDFs.
 * If one of these fails, the calculation engine is wrong — not the test.
 */
class BillFiguresTest {

    private final InvoiceCalculationService calc = new InvoiceCalculationService();

    private static InvoiceItem line(String description, String amount) {
        InvoiceItem i = new InvoiceItem();
        i.setPrintedDescription(description);
        i.setCalculationType(CalculationType.MANUAL);
        i.setAmount(new BigDecimal(amount));
        return i;
    }

    private static InvoiceItem perTeu(String description, String teu, String rate) {
        InvoiceItem i = new InvoiceItem();
        i.setPrintedDescription(description);
        i.setCalculationType(CalculationType.PER_TEU);
        i.setTeu(new BigDecimal(teu));
        i.setRate(new BigDecimal(rate));
        return i;
    }

    private static Invoice invoiceWith(InvoiceItem... items) {
        Invoice inv = new Invoice();
        for (InvoiceItem i : items) inv.addItem(i);
        return inv;
    }

    @Test
    @DisplayName("ASE/HC/CNF/370 — 12 items, 17,400 taxable, 20,532 grand total")
    void cnf370() {
        Invoice inv = invoiceWith(
                line("Pre shipment export documentation charges", "3000"),
                line("ICO/Permit, ROC Submission & Self-sealing Documentation", "1000"),
                line("Expenses on Phytosanitary certificate", "3000"),
                line("Certificate of weight & quality", "1500"),
                line("EDI charges", "1000"),
                line("Empty Container survey fee", "400"),
                line("Customs Clearance charges", "1750"),
                line("Port Expenses", "1250"),
                line("VGM expenses", "750"),
                line("LO/LO", "750"),
                line("CHA Service charges", "2500"),
                line("Post Shipment Document charges", "500"));

        calc.recalculate(inv, GstTreatment.INTRA);

        assertThat(inv.getTaxableAmount()).isEqualByComparingTo("17400.00");
        assertThat(inv.getCgstAmount()).isEqualByComparingTo("1566.00");
        assertThat(inv.getSgstAmount()).isEqualByComparingTo("1566.00");
        assertThat(inv.getGrandTotal()).isEqualByComparingTo("20532");
        assertThat(inv.getAmountInWords())
                .isEqualTo("Twenty thousand five hundred and thirty-two only");
    }

    @Test
    @DisplayName("ASE/HC/CNF/439 — 16 items, 52,950 taxable, 62,481 grand total")
    void cnf439() {
        Invoice inv = invoiceWith(
                line("Pre shipment export documentation charges", "3000"),
                line("ICO/Permit, ROC Submission & Self-sealing Documentation", "2000"),
                line("Expenses on Phytosanitary certificate addl. @Rs.750", "4500"),
                line("Phytosanitary addl. declaration", "5000"),
                line("Health Certificate", "5750"),
                line("Certificate of origin", "1750"),
                line("Reissue of Health Certificate as per INSTR.", "6000"),
                line("Handling Charges", "2000"),
                line("EDI charges", "1000"),
                line("Empty Container survey fee", "1200"),
                line("Customs Clearance charges", "5250"),
                line("Port Expenses", "3750"),
                line("VGM expenses 3@RS500/TEU", "1500"),
                line("LO/LO", "2250"),
                line("CHA Service charges 3X20", "7500"),
                line("Post Shipment Document charges", "500"));

        calc.recalculate(inv, GstTreatment.INTRA);

        assertThat(inv.getTaxableAmount()).isEqualByComparingTo("52950.00");
        assertThat(inv.getCgstAmount()).isEqualByComparingTo("4765.50");
        assertThat(inv.getGrandTotal()).isEqualByComparingTo("62481");
    }

    @Test
    @DisplayName("ASE/HC/CNF/459 — printed subtotal is wrong on paper; items sum to 55,650")
    void cnf459SubtotalTypoIsCaught() {
        // The paper bill prints 'TOTAL Rs 54850.00' but its GST of 5,008.50 is 9% of
        // 55,650, and the SOA records 55,650. The printed subtotal was typed, not summed.
        Invoice inv = invoiceWith(
                line("Pre shipment export documentation charges", "3000"),
                line("ICO/Permit, ROC Submission & Self-sealing Documentation", "1500"),
                line("Expenses on Phytosanitary certificate with addl. declaration", "5250"),
                perTeu("Fumigation charges per TEU @ RS 6000/-", "2", "6000"),
                line("Halting charges 3 days per TEU per day @Rs 2000/-", "12000"),
                line("Certificate of weight & quality", "2250"),
                line("Certificate of origin", "1750"),
                line("Containers Seal Scanning Charges", "1600"),
                line("EDI charges", "1000"),
                line("Empty Container survey fee", "800"),
                line("Customs Clearance charges", "3500"),
                line("Port Expenses", "2500"),
                line("VGM expenses", "1500"),
                line("LO/LO @Rs.750", "1500"),
                line("CHA Service charges", "5000"),
                line("Post Shipment Document charges", "500"));

        calc.recalculate(inv, GstTreatment.INTRA);

        assertThat(inv.getTaxableAmount()).isEqualByComparingTo("55650.00");
        assertThat(inv.getTaxableAmount()).isNotEqualByComparingTo("54850.00");
        assertThat(inv.getCgstAmount()).isEqualByComparingTo("5008.50");
        assertThat(inv.getGrandTotal()).isEqualByComparingTo("65667");
    }

    @Test
    @DisplayName("ASE/HC/T/373 — 4 containers to Kushalnagar plus Hassan for 3 TEU")
    void t373() {
        InvoiceItem transport = new InvoiceItem();
        transport.setPrintedDescription("Transportation Charges from Mangalore to Kushalnagar and back");
        transport.setCalculationType(CalculationType.SIMPLE);
        transport.setQuantity(new BigDecimal("4"));
        transport.setRate(new BigDecimal("27000"));
        transport.addSubLine("1.Trailer No. TN02BX4800 &Container No. FATU1354288");
        transport.addSubLine("2.Trailer No. KA 01 AN1597 &Container No. MSDU1530129");
        transport.addSubLine("3.Trailer No. KA01AN1598 &Container No. MEDU6173393");
        transport.addSubLine("4.Trailer No. TN02BX4500 &Container No. FCIU4320208");

        Invoice inv = invoiceWith(transport,
                perTeu("Additional transportation via Hassan charges @4000/- for 3 TEU", "3", "4000"));

        calc.recalculate(inv, GstTreatment.INTRA);

        assertThat(transport.getAmount()).isEqualByComparingTo("108000.00");
        assertThat(transport.getSubLines()).hasSize(4);
        assertThat(inv.getTaxableAmount()).isEqualByComparingTo("120000.00");
        assertThat(inv.getCgstAmount()).isEqualByComparingTo("10800.00");
        assertThat(inv.getGrandTotal()).isEqualByComparingTo("141600");
    }

    @Test
    @DisplayName("ASE/HC/T/476 — two routes on one bill, 7 TEU Hassan, 219,750 taxable")
    void t476() {
        InvoiceItem somwarpet = new InvoiceItem();
        somwarpet.setPrintedDescription("Transportation Charges from Mangalore to SOMWARPET and back");
        somwarpet.setCalculationType(CalculationType.SIMPLE);
        somwarpet.setQuantity(BigDecimal.ONE);
        somwarpet.setRate(new BigDecimal("29750"));
        somwarpet.addSubLine("1x20");

        InvoiceItem kushalnagar = new InvoiceItem();
        kushalnagar.setPrintedDescription("Transportation Charges from Mangalore to KUSHALNAGAR and back");
        kushalnagar.setCalculationType(CalculationType.SIMPLE);
        kushalnagar.setQuantity(new BigDecimal("6"));
        kushalnagar.setRate(new BigDecimal("27000"));
        kushalnagar.addSubLine("6x20");

        Invoice inv = invoiceWith(somwarpet, kushalnagar,
                perTeu("Addnl. Charges for Transportation via Hassan", "7", "4000"));

        calc.recalculate(inv, GstTreatment.INTRA);

        assertThat(kushalnagar.getAmount()).isEqualByComparingTo("162000.00");
        assertThat(inv.getTaxableAmount()).isEqualByComparingTo("219750.00");
        assertThat(inv.getCgstAmount()).isEqualByComparingTo("19777.50");
        assertThat(inv.getGrandTotal()).isEqualByComparingTo("259305");
    }

    @Test
    @DisplayName("ASE/HC/TA/450 — taxi hire, 5,000 taxable, 5,900 grand total")
    void ta450() {
        Invoice inv = invoiceWith(line("Taxi hire paid for PQ inspection", "5000"));
        calc.recalculate(inv, GstTreatment.INTRA);

        assertThat(inv.getCgstAmount()).isEqualByComparingTo("450.00");
        assertThat(inv.getGrandTotal()).isEqualByComparingTo("5900");
        assertThat(inv.getAmountInWords()).isEqualTo("Five thousand nine hundred only");
    }

    @Test
    @DisplayName("ASE/HC/CB/437 — 31 sets, 54,250 taxable, 64,015 grand total")
    void cb437() {
        InvoiceItem online = new InvoiceItem();
        online.setPrintedDescription("31 Sets of applications through online processing charges @1250/set");
        online.setCalculationType(CalculationType.PER_SET);
        online.setQuantity(new BigDecimal("31"));
        online.setRate(new BigDecimal("1250"));

        InvoiceItem scanning = new InvoiceItem();
        scanning.setPrintedDescription("31sets of documentation and scanning charges@INR.500/set");
        scanning.setCalculationType(CalculationType.PER_SET);
        scanning.setQuantity(new BigDecimal("31"));
        scanning.setRate(new BigDecimal("500"));

        Invoice inv = invoiceWith(online, scanning);
        calc.recalculate(inv, GstTreatment.INTRA);

        assertThat(online.getAmount()).isEqualByComparingTo("38750.00");
        assertThat(scanning.getAmount()).isEqualByComparingTo("15500.00");
        assertThat(inv.getTaxableAmount()).isEqualByComparingTo("54250.00");
        assertThat(inv.getCgstAmount()).isEqualByComparingTo("4882.50");
        assertThat(inv.getGrandTotal()).isEqualByComparingTo("64015");
    }

    @Test
    @DisplayName("ASE/HC/S/438 — DHL deduction is applied after GST; SOA still reports 60,180")
    void s438PostTaxDeduction() {
        InvoiceItem psc = new InvoiceItem();
        psc.setPrintedDescription("EXPENSES ON PHYTOSANITARY CERTIFICATE 17 SETS @RS2500/-");
        psc.setCalculationType(CalculationType.PER_SET);
        psc.setQuantity(new BigDecimal("17"));
        psc.setRate(new BigDecimal("2500"));

        InvoiceItem svc = new InvoiceItem();
        svc.setPrintedDescription("SERVICE CHARGES 17 SETS@RS.500/-");
        svc.setCalculationType(CalculationType.PER_SET);
        svc.setQuantity(new BigDecimal("17"));
        svc.setRate(new BigDecimal("500"));

        Invoice inv = invoiceWith(psc, svc);
        inv.setPostTaxAdjustmentLabel("LESS DHL CHARGES A/C ASE-ACC RS 3758/-");
        inv.setPostTaxAdjustmentAmount(new BigDecimal("3758"));

        calc.recalculate(inv, GstTreatment.INTRA);

        assertThat(inv.getTaxableAmount()).isEqualByComparingTo("51000.00");
        assertThat(inv.getCgstAmount()).isEqualByComparingTo("4590.00");
        // What the SOA reports.
        assertThat(inv.getGrandTotal()).isEqualByComparingTo("60180");
        // What the customer actually pays, and what the bill prints in words.
        assertThat(inv.getNetPayable()).isEqualByComparingTo("56422");
        assertThat(inv.getAmountInWords())
                .isEqualTo("Fifty-six thousand four hundred and twenty-two only");
    }

    @Test
    @DisplayName("Halting charges use TEU x days x rate")
    void haltingPerTeuPerDay() {
        InvoiceItem halting = new InvoiceItem();
        halting.setPrintedDescription("Halting charges 3 days per TEU per day @Rs 2000/-");
        halting.setCalculationType(CalculationType.PER_TEU_PER_DAY);
        halting.setTeu(new BigDecimal("2"));
        halting.setDays(3);
        halting.setRate(new BigDecimal("2000"));

        Invoice inv = invoiceWith(halting);
        calc.recalculate(inv, GstTreatment.INTRA);

        assertThat(halting.getAmount()).isEqualByComparingTo("12000.00");
    }

    @Test
    @DisplayName("Financial year comes from the date, not from what was typed on the bill")
    void financialYearDerivation() {
        // TA/450 prints 2025-26 on an 08-08-2026 date. The date wins.
        assertThat(FinancialYear.of(LocalDate.of(2026, 8, 8))).isEqualTo("2026-27");
        assertThat(FinancialYear.of(LocalDate.of(2026, 3, 31))).isEqualTo("2025-26");
        assertThat(FinancialYear.of(LocalDate.of(2026, 4, 1))).isEqualTo("2026-27");
    }

    @Test
    @DisplayName("ICO mark ranges collapse to their first value for the SOA")
    void soaMarkFromRange() {
        assertThat(com.ase.billing.domain.Shipment.deriveSoaMark("14/1850/2026/283")).isEqualTo("283");
        assertThat(com.ase.billing.domain.Shipment.deriveSoaMark("14/1850/2026/292-295")).isEqualTo("292");
        assertThat(com.ase.billing.domain.Shipment.deriveSoaMark("14/1850/2026/324-326")).isEqualTo("324");
    }
}
