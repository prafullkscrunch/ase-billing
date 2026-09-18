package com.ase.billing.wcqc.service;

import com.ase.billing.wcqc.web.dto.CertificateDtos.ExtractedInvoiceData;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link InvoiceUploadService} against real ASE invoices — one
 * per packaging type, kept as fixtures under {@code
 * src/test/resources/wcqc-fixtures/} — rather than synthetic Excel files
 * built just for the test. Every assertion here traces back to either a
 * number the source invoice states directly, or a real historical WC/QC
 * certificate for that same shipment, cross-checked while building this
 * module (see the WC & QC module guide for the full trail).
 *
 * <p>{@link InvoiceUploadService} takes no collaborators, so these are
 * plain unit tests — no Spring context needed, just {@code new
 * InvoiceUploadService()}.
 */
class InvoiceUploadServiceTest {

    private final InvoiceUploadService service = new InvoiceUploadService();

    private MockMultipartFile fixture(String name) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("wcqc-fixtures/" + name)) {
            assertThat(in).as("fixture " + name + " must be on the test classpath").isNotNull();
            return new MockMultipartFile("file", name,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", in.readAllBytes());
        }
    }

    // ---- Jute bag (320 B), ordinary header, no wrap ------------------------------
    // HC_358-INV-_SUCAFINA-JULY_2026.xlsx — the invoice this module's extraction was
    // first validated against.

    @Test
    void juteBag_extractsShipmentReferenceAndRoute() throws IOException {
        ExtractedInvoiceData data = service.extract(fixture("invoice-358-sucafina-320-jutebag.xlsx"));

        assertThat(data.suggestedMarksAndNos()).isEqualTo("14/1850/2026/358");
        assertThat(data.invoiceNumber()).isEqualTo("ES/HC/2027/162");
        assertThat(data.invoiceDate()).isEqualTo(LocalDate.of(2026, 7, 7));
        assertThat(data.portOfLoading()).isEqualTo("MANGALORE");
        assertThat(data.portOfDischarge()).contains("Istanbul");
        // Final Destination is verbatim from the invoice, even when it's
        // identical to Port of Discharge — no shortening. See the class doc
        // on InvoiceUploadService for why an earlier version's shortening
        // heuristic was removed.
        assertThat(data.finalDestination()).isEqualTo(data.portOfDischarge());
    }

    @Test
    void juteBag_composesWeightBreakdownWithJuteWording() throws IOException {
        ExtractedInvoiceData data = service.extract(fixture("invoice-358-sucafina-320-jutebag.xlsx"));

        assertThat(data.summaryStatementLines())
                .as("weight breakdown suggestion, checked against the real issued WC certificate for this shipment")
                .isNotNull()
                .contains("Total Gross Weight of 320 jute bag each Wg.60.70kgs:=19424.00KGS")
                .contains("Total Nett Weight of 320 jute  bag each Wg 60.00kgs:=19200.00KGS")
                .contains("Total Tare Wight of 320 bags each Wg.0.70Kgs:=224.00kgs");
    }

    @Test
    void juteBag_marksColumnStartsWithRealBodyContent() throws IOException {
        ExtractedInvoiceData data = service.extract(fixture("invoice-358-sucafina-320-jutebag.xlsx"));
        assertThat(data.marksColumnText()).isNotNull();
        assertThat(data.marksColumnText().split("\\r?\\n")[0]).isEqualTo("FRONT SIDE:");
    }

    // ---- Big bags on pallets (80 BB), header wraps onto "Container No." ----------
    // HC_369_INV-_TOUTON_SA_AUG_2026-80_BB.xlsx — this is the invoice that surfaced
    // both the header-wrap bug and the missing big-bag/pallet wording template.

    @Test
    void bigBagOnPallets_marksColumnSkipsTheWrappedHeaderLine() throws IOException {
        ExtractedInvoiceData data = service.extract(fixture("invoice-369-touton-bigbag-pallets.xlsx"));

        assertThat(data.marksColumnText()).isNotNull();
        String firstLine = data.marksColumnText().split("\\r?\\n")[0];
        assertThat(firstLine)
                .as("'Container No.' is the wrapped second line of the column header, not body content — "
                        + "regression test for the bug reported against this exact invoice")
                .isNotEqualTo("Container No.")
                .isEqualTo("HANGAL COFFEE EXPORTING PVT LTD");
    }

    @Test
    void bigBagOnPallets_composesWeightBreakdownWithPalletWording_notJute() throws IOException {
        ExtractedInvoiceData data = service.extract(fixture("invoice-369-touton-bigbag-pallets.xlsx"));

        assertThat(data.summaryStatementLines())
                .as("must not fall back to jute-bag wording for a big-bag/pallet shipment — "
                        + "checked against two independent real WC certificates for this packaging type")
                .isNotNull()
                .doesNotContain("jute")
                .contains("80 BIG BAGS COFFEE")
                .contains("40 PALLETS");
    }

    @Test
    void bigBagOnPallets_findsBagCountWithNoRangeMarkerPresent() throws IOException {
        // This invoice has no "1/N" bag-range line anywhere on the sheet —
        // bag count only comes from "PACKING IN 80 BIG BAGS PER 1 MT EACH
        // ON PALLETS."
        ExtractedInvoiceData data = service.extract(fixture("invoice-369-touton-bigbag-pallets.xlsx"));
        assertThat(data.summaryStatementLines()).contains("80 BIG BAGS COFFEE");
    }

    @Test
    void bigBagOnPallets_qualityStatementSuggestionExcludesAdminLines() throws IOException {
        ExtractedInvoiceData data = service.extract(fixture("invoice-369-touton-bigbag-pallets.xlsx"));

        assertThat(data.qcQualityStatementLines())
                .as("QC's quality statement was printing blank before this suggestion existed")
                .isNotNull()
                .startsWith("QUALITY:")
                .doesNotContain("Container No.")
                .doesNotContain("14/1850/2026/369")
                .doesNotContain("NET WEIGHT");
    }

    // ---- Jute bag (640 B), header wraps, description sits in the header's OWN column ----
    // HC_397-INV-AUG_2026_EFICO_KAREN_TRADE.xlsx — this is the invoice that surfaced the
    // header-wrap detector's over-eager "keep skipping while non-blank" bug, a hyphen-
    // after-colon artifact in NOTIFY, a "DATE." instead of "DT." date label with dot-
    // separated dates, and a NOTIFY address continuing into an adjacent column on a row
    // that isn't the anchor row.

    @Test
    void juteBagWithWrappedHeader_marksAndDescriptionBothResolve_notFreightText() throws IOException {
        // The header-wrap detector, before being made more conservative,
        // kept advancing past every row where the "Description of Goods"
        // header's own column stayed non-blank — which on THIS invoice is
        // also where real description content lives, so it skipped clean
        // past the entire body and picked up unrelated freight/payment
        // text much further down the sheet instead.
        ExtractedInvoiceData data = service.extract(fixture("invoice-397-efico-karentrade-640-jutebag.xlsx"));

        assertThat(data.marksColumnText())
                .isNotNull()
                .doesNotContain("FREIGHT PAYABLE")
                .doesNotContain("AMOUNT CHARGEABLE");
        assertThat(data.marksColumnText().split("\\r?\\n")[0]).isEqualTo("14/1850/2026/397");
        assertThat(data.descriptionColumnText())
                .as("this invoice's description content sits directly under its own header column, not one column over")
                .isNotNull()
                .contains("640 BAGS OF");
    }

    @Test
    void juteBagWithWrappedHeader_readsDateLabelledDATEWithDotSeparators() throws IOException {
        // "ES/HC/2026/199. DATE.25.08.2026" — spells the label as "DATE"
        // rather than "DT", and separates the date with dots rather than
        // slashes. Both needed a dedicated fix.
        ExtractedInvoiceData data = service.extract(fixture("invoice-397-efico-karentrade-640-jutebag.xlsx"));

        assertThat(data.invoiceNumber()).isEqualTo("ES/HC/2026/199");
        assertThat(data.invoiceDate()).isEqualTo(LocalDate.of(2026, 8, 25));
    }

    @Test
    void juteBagWithWrappedHeader_notifyBlockHasNoLeadingHyphenAndIncludesTheAddress() throws IOException {
        // "NOTIFY 1:- KAREN TRADE LTD..." has its hyphen AFTER the colon,
        // which an earlier version of the label pattern left in place as a
        // stray leading "-". The real street address then continues in an
        // adjacent column on the row below the anchor, not the anchor row
        // itself, which a same-row-only continuation check missed entirely.
        ExtractedInvoiceData data = service.extract(fixture("invoice-397-efico-karentrade-640-jutebag.xlsx"));

        assertThat(data.consigneeNotifyBlock())
                .isNotNull()
                .doesNotStartWith("-")
                .startsWith("KAREN TRADE LTD")
                .contains("AHGMASHENEBELI STR 34A BATUMI");
    }

    @Test
    void juteBagWithWrappedHeader_composesWeightBreakdownFromASentenceEmbeddedFigure() throws IOException {
        // This invoice's per-bag net weight has no "NET WEIGHT:" or "NETT
        // WT:" label at all — it's embedded mid-sentence: "PACKED IN JUTE
        // BAG WG NETT60.00KGS".
        ExtractedInvoiceData data = service.extract(fixture("invoice-397-efico-karentrade-640-jutebag.xlsx"));

        assertThat(data.summaryStatementLines())
                .isNotNull()
                .contains("Total Gross Weight of 640 jute bag each Wg.60.70kgs:=38848.00KGS")
                .contains("Total Nett Weight of 640 jute  bag each Wg 60.00kgs:=38400.00KGS");
    }

    @Test
    void juteBagWithWrappedHeader_quantityReadsARawExcelNumber_notTheLaterFallbackText() throws IOException {
        // The real quantity cell holds a bare Excel number (38.4) with "MT"
        // only in its display format, not as text — matching on text alone
        // skipped past it and picked up a "TOTAL QTY:38.4MT" line much
        // further down the sheet instead.
        ExtractedInvoiceData data = service.extract(fixture("invoice-397-efico-karentrade-640-jutebag.xlsx"));

        assertThat(data.quantityColumnText())
                .isNotNull()
                .doesNotContain("TOTAL QTY")
                .contains("38.4");
    }

    // ---- Jute bag (320 B), header wraps AND description header wraps too --------
    // HC_405_INV_JULY_26_NKG_COPROCAFE_SA_320_BAGS.xlsx — both the marks header
    // ("Marks & Nos/ No. " / "Container No.") and the description header
    // ("Description of Goods and " / "kind of pkges") wrap onto a second row here,
    // which is exactly the case that made the original (over-eager) header-wrap
    // detector skip straight past the entire body.

    @Test
    void juteBagWithBothHeadersWrapped_marksAndDescriptionBothResolve() throws IOException {
        ExtractedInvoiceData data = service.extract(fixture("invoice-405-nkg-coprocafe-320-jutebag.xlsx"));

        assertThat(data.marksColumnText()).isNotNull().doesNotContain("Container No.");
        assertThat(data.marksColumnText().split("\\r?\\n")[0]).isEqualTo("14/1850/2026/405");
        assertThat(data.descriptionColumnText())
                .isNotNull()
                .doesNotContain("kind of pkges")
                .contains("320 BAGS OF");
    }

    // ---- Jute bag (640 B), ONLY the marks header wraps (description header doesn't) ----
    // HC 403- INV BIJDENDIJK 2026.xlsx — this invoice's marks header wraps onto
    // "Container No." same as 369/405/397, but its "Description of Goods" header
    // does NOT wrap — content starts immediately in that column. The header-wrap
    // detector originally only ever checked the description side, so it never
    // fired here and "Container No." leaked into the marks column while the
    // description column came back completely empty.

    @Test
    void juteBagWithOnlyMarksHeaderWrapped_bothColumnsResolve() throws IOException {
        ExtractedInvoiceData data = service.extract(fixture("invoice-403-bijdendijk-640-jutebag-marksonly-wrap.xlsx"));

        assertThat(data.marksColumnText())
                .as("only the marks side wraps here — the description header never does")
                .isNotNull()
                .doesNotContain("Container No.");
        assertThat(data.marksColumnText().split("\\r?\\n")[0]).isEqualTo("14/1850/2026/403");
        assertThat(data.descriptionColumnText())
                .as("must not come back empty — this was the actual reported bug")
                .isNotNull()
                .contains("640 BAGS GREEN COFFEE BEANS");
    }

    // ---- Big bags on pallets (20 BB), "/" as the CONSIGNEE-NOTIFY combiner --------
    // HC 407-INV- SEPT 2026 COPROCAFE 20 BB.xlsx — "CONSGINEE /NOTIFY:-" uses "/"
    // to combine the two labels; the pattern only recognised "&" as a combiner,
    // so "/NOTIFY:-" leaked into the front of the extracted address.

    @Test
    void combinedConsigneeNotifyWithSlashSeparator_hasNoLabelLeakage() throws IOException {
        ExtractedInvoiceData data = service.extract(fixture("invoice-407-coprocafe-20bb-slash-consignee.xlsx"));

        assertThat(data.consigneeNotifyBlock())
                .isNotNull()
                .doesNotStartWith("/")
                .doesNotContain("NOTIFY")
                .startsWith("NKG COPROCAFE S.A.");
    }

    // ---- Big bags on pallets (20 BB), comma before the date label -----------------
    // HC 400-INV AUG 2026 NKG BERO-20 BB.xlsx — "ES/HC/2027/202 , DT:25/08/2026" has
    // a comma (not a period) separating the invoice number from the date label.

    @Test
    void invoiceNumberDateWithCommaSeparator_stillParses() throws IOException {
        ExtractedInvoiceData data = service.extract(fixture("invoice-400-nkgbero-20bb-comma-date.xlsx"));

        assertThat(data.invoiceNumber()).isEqualTo("ES/HC/2027/202");
        assertThat(data.invoiceDate()).isEqualTo(LocalDate.of(2026, 8, 25));
    }

    // ---- Bulk bag, single unit ---------------------------------------------------
    // HC INV 417 GROUP SOPEX 1 BULK.xlsx — has no "Total Nett/Gross Wt" line at all,
    // since with one bulk bag the per-unit figure already IS the shipment total.

    @Test
    void singleBulkBag_fallsBackToPerUnitAsTotal() throws IOException {
        ExtractedInvoiceData data = service.extract(fixture("invoice-417-groupsopex-1-bulk.xlsx"));

        assertThat(data.summaryStatementLines())
                .isNotNull()
                .contains("Total Gross Weight of 1 BULK BAGS each Wg.21610.00kgs:=21610.00KGS")
                .contains("Total Nett Weight of 1 BULK BAGS each Wg 21600.00kgs:=21600.00KGS");
    }

    // ---- Bulk bag, multiple units -------------------------------------------------
    // HC 402 INV AUG 2026 3 BULK BERNHARD-HAM.xlsx — this invoice's own per-unit
    // weight line has NO unit suffix at all ("NETT WT:21600.00", no "KGS"), which a
    // stricter regex requiring KGS silently failed to read.

    @Test
    void multiBulkBag_readsPerUnitFigureEvenWithoutAKgsSuffix() throws IOException {
        ExtractedInvoiceData data = service.extract(fixture("invoice-402-bernhard-3-bulk.xlsx"));

        assertThat(data.summaryStatementLines())
                .as("the per-unit NETT WT line on this specific invoice has no KGS suffix at all")
                .isNotNull()
                .contains("Total Gross Weight of 3 BULK BAGS each Wg.21610.00kgs:=64830.00KGS")
                .contains("Total Nett Weight of 3 BULK BAGS each Wg 21600.00kgs:=64800.00KGS");
    }

    @Test
    void multiBulkBag_bagCountComesFromRangeMarker() throws IOException {
        // Unlike invoice 417, this one DOES have a "1/3" bag-range line —
        // confirms the range marker still takes priority when present.
        ExtractedInvoiceData data = service.extract(fixture("invoice-402-bernhard-3-bulk.xlsx"));
        assertThat(data.summaryStatementLines()).contains("Total Gross Weight of 3 BULK BAGS");
    }

    // ---- Cross-cutting: every fixture must resolve a usable mark and consignee ----

    @Test
    void everyFixture_resolvesAMarkAndAConsigneeBlock() throws IOException {
        for (String name : new String[]{
                "invoice-358-sucafina-320-jutebag.xlsx",
                "invoice-369-touton-bigbag-pallets.xlsx",
                "invoice-397-efico-karentrade-640-jutebag.xlsx",
                "invoice-400-nkgbero-20bb-comma-date.xlsx",
                "invoice-402-bernhard-3-bulk.xlsx",
                "invoice-403-bijdendijk-640-jutebag-marksonly-wrap.xlsx",
                "invoice-405-nkg-coprocafe-320-jutebag.xlsx",
                "invoice-407-coprocafe-20bb-slash-consignee.xlsx",
                "invoice-417-groupsopex-1-bulk.xlsx"}) {
            ExtractedInvoiceData data = service.extract(fixture(name));
            assertThat(data.suggestedMarksAndNos()).as(name).matches("\\d{2}/\\d{3,4}/\\d{4}/\\d{1,4}");
            assertThat(data.consigneeNotifyBlock()).as(name).isNotBlank();
        }
    }
}
