package com.ase.billing.pdf;

import com.ase.billing.domain.*;
import com.ase.billing.repo.CompanySettingsRepository;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;

/**
 * Renders an invoice in ASE's house format, reproducing their Word template.
 *
 * The template is a THREE-ROW, TWO-COLUMN table, and the row structure is what
 * makes the page look right:
 *
 *   row 0   "Particulars" + HC INV / ICO / HSN      |  "Amount In Rs."
 *   row 1   numbered charges, blank lines, then     |  the amounts, blank lines,
 *           the bank details, all in ONE cell       |  then the totals stack
 *   row 2   "Amount in words: ..."                  |  "G- Total.RS.23541/-"
 *
 * The bank block and the totals stack live inside row 1, not in rows of their
 * own. That is why they sit side by side on the paper bill, and why splitting
 * them into separate table rows produced a page that did not match.
 *
 * Amounts print in ASE's own notation: "3000/-" on the charge lines, and the
 * fuller "TOTAL   Rs 19950.00" form on the totals stack.
 */
@Service
public class InvoicePdfService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private static final Font F_COMPANY = new Font(Font.HELVETICA, 12, Font.BOLD);
    private static final Font F_ADDRESS = new Font(Font.HELVETICA, 10);
    private static final Font F_META    = new Font(Font.HELVETICA, 10);
    private static final Font F_LABEL   = new Font(Font.HELVETICA, 10, Font.BOLD);
    private static final Font F_BODY    = new Font(Font.HELVETICA, 10);
    private static final Font F_SUB     = new Font(Font.HELVETICA, 9);

    private final CompanySettingsRepository companyRepo;

    public InvoicePdfService(CompanySettingsRepository companyRepo) {
        this.companyRepo = companyRepo;
    }

    /**
     * open-in-view is disabled, so customer, category, shipment, items and
     * sub-lines are lazy proxies by the time a controller hands the invoice over.
     * Without a transaction the first dereference throws and no PDF is produced.
     */
    @Transactional(readOnly = true)
    public byte[] render(Invoice invoice) {
        CompanySettings co = companyRepo.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Company settings are not configured."));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 56, 56, 40, 40);
        PdfWriter.getInstance(doc, out);
        doc.open();

        letterhead(doc, co);
        meta(doc, invoice);
        billTo(doc, invoice.getCustomer());
        doc.add(Chunk.NEWLINE);
        bodyTable(doc, invoice, co);
        signature(doc, co);

        if (invoice.getAnnexure() != null) {
            doc.newPage();
            annexure(doc, invoice.getAnnexure());
        }

        doc.close();
        return out.toByteArray();
    }

    // ---------- letterhead ----------------------------------------------------

    private void letterhead(Document doc, CompanySettings co) {
        doc.add(centred(co.getName(), F_COMPANY));
        doc.add(centred(co.getAddressLine1(), F_ADDRESS));
        if (notBlank(co.getAddressLine2())) doc.add(centred(co.getAddressLine2(), F_ADDRESS));
        if (notBlank(co.getAddressLine3())) doc.add(centred(co.getAddressLine3(), F_ADDRESS));
        doc.add(centred(co.getCity() + " " + co.getPin(), F_ADDRESS));
        doc.add(centred("GSTIN: " + co.getGstin(), F_ADDRESS));

        // The Word template puts a bottom border on the GSTIN paragraph:
        //   <w:bottom w:val="single" w:sz="6" w:space="1"/>
        // w:sz is in eighths of a point, so 6/8 = 0.75pt, with 1pt of space
        // above it. This is the rule that sits between the letterhead and the
        // invoice number.
        LineSeparator rule = new LineSeparator(0.75f, 100f, null, Element.ALIGN_CENTER, 0f);
        Paragraph ruleLine = new Paragraph();
        ruleLine.setSpacingBefore(1f);
        ruleLine.add(rule);
        doc.add(ruleLine);
    }

    private void meta(Document doc, Invoice inv) {
        Paragraph p = new Paragraph();
        p.setAlignment(Element.ALIGN_RIGHT);
        p.setSpacingBefore(10);
        p.add(new Chunk("No: " + inv.getInvoiceNumber() + "\n", F_META));
        p.add(new Chunk("Date :  " + inv.getInvoiceDate().format(DATE), F_META));
        doc.add(p);
    }

    private void billTo(Document doc, Customer c) {
        Paragraph p = new Paragraph();
        p.setSpacingBefore(16);
        p.add(new Chunk("To,\n", F_BODY));
        p.add(new Chunk("M/s. " + c.getName() + "\n", F_BODY));
        for (String line : new String[]{c.getAddressLine1(), c.getAddressLine2(), c.getAddressLine3()}) {
            if (notBlank(line)) p.add(new Chunk(line + "\n", F_BODY));
        }
        if (notBlank(c.getCity())) {
            p.add(new Chunk(notBlank(c.getPin()) ? c.getCity() + "-" + c.getPin() : c.getCity(), F_BODY));
            p.add(Chunk.NEWLINE);
        }
        if (notBlank(c.getGstin())) p.add(new Chunk("GSTIN " + c.getGstin(), F_BODY));
        doc.add(p);
    }

    // ---------- the three-row body table -------------------------------------

    private void bodyTable(Document doc, Invoice inv, CompanySettings co) {
        PdfPTable t = new PdfPTable(new float[]{70f, 30f});
        t.setWidthPercentage(100);
        t.setSpacingBefore(6);

        // Row 0 — column headings, with the shipment context under "Particulars".
        t.addCell(headerCell(particularsHeading(inv), Element.ALIGN_LEFT));
        t.addCell(headerCell("Amount\nIn Rs.", Element.ALIGN_CENTER));

        // Row 1 — charges over bank details | amounts over the totals stack.
        PdfPCell left = new PdfPCell(chargesAndBank(inv, co));
        left.setPadding(6);
        t.addCell(left);

        PdfPCell right = new PdfPCell(amountsAndTotals(inv));
        right.setPadding(6);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(right);

        // Row 2 — amount in words | grand total.
        BigDecimal printed = inv.getPostTaxAdjustmentAmount().signum() == 0
                ? inv.getGrandTotal() : inv.getNetPayable();

        PdfPCell words = new PdfPCell(new Phrase("Amount in words: " + inv.getAmountInWords(), F_BODY));
        words.setPadding(5);
        t.addCell(words);

        PdfPCell grand = new PdfPCell(new Phrase("G- Total.RS." + slash(printed), F_LABEL));
        grand.setPadding(5);
        grand.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(grand);

        doc.add(t);
    }

    /** "Particulars" plus the HC INV / ICO / container line and the HSN code. */
    private String particularsHeading(Invoice inv) {
        StringBuilder sb = new StringBuilder("Particulars");
        if (notBlank(inv.getHeaderNote())) {
            sb.append('\n').append(inv.getHeaderNote());
        }
        Shipment s = inv.getShipment();
        if (s != null) {
            sb.append("\nHC INV NO: -").append(s.getHcInvoiceNumber())
              .append(" & ICO mark NO.").append(s.getIcoMarkFull())
              .append(" - ").append(s.containerNotation());
        }
        if (notBlank(inv.getHsnCode())) {
            sb.append("\nHsn code.").append(inv.getHsnCode());
        }
        return sb.toString();
    }

    private Phrase chargesAndBank(Invoice inv, CompanySettings co) {
        Phrase p = new Phrase();
        int n = 1;
        for (InvoiceItem item : inv.getItems()) {
            p.add(new Chunk(n++ + "." + item.getPrintedDescription() + "\n", F_BODY));
            for (InvoiceItemSubline sl : item.getSubLines()) {
                p.add(new Chunk("       " + sl.getText() + "\n", F_SUB));
            }
        }

        // Blank lines separate the charges from the bank block, as on the template.
        p.add(new Chunk("\n\n", F_BODY));

        p.add(new Chunk("BANK DETAILS: " + co.getName() + "\n", F_LABEL));
        p.add(new Chunk(co.getBankName() + "\n", F_LABEL));
        p.add(new Chunk("CA. A/C.No." + co.getBankAccountNo() + "\n", F_LABEL));
        p.add(new Chunk("IFSC CODE: " + co.getBankIfsc() + "\n", F_LABEL));
        for (String line : co.getBankBranchLines().split("\\R")) {
            p.add(new Chunk(line + "\n", F_LABEL));
        }
        return p;
    }

    private Phrase amountsAndTotals(Invoice inv) {
        Phrase p = new Phrase();

        for (InvoiceItem item : inv.getItems()) {
            p.add(new Chunk(slash(item.getAmount()) + "\n", F_BODY));
            // Keep the amount column in step with sub-lines on the left.
            for (int i = 0; i < item.getSubLines().size(); i++) {
                p.add(new Chunk("\n", F_SUB));
            }
        }

        p.add(new Chunk("\n\n", F_BODY));

        p.add(new Chunk("TOTAL   Rs " + twoDp(inv.getTaxableAmount()) + "\n", F_LABEL));
        if (inv.getIgstRate() != null && inv.getIgstRate().signum() > 0) {
            p.add(new Chunk("IGST " + rate(inv.getIgstRate()) + "% Rs."
                    + twoDp(inv.getIgstAmount()) + "\n", F_BODY));
        } else {
            p.add(new Chunk("SGST " + rate(inv.getSgstRate()) + "% Rs."
                    + twoDp(inv.getSgstAmount()) + "\n", F_BODY));
            p.add(new Chunk("CGST" + rate(inv.getCgstRate()) + "% Rs. "
                    + twoDp(inv.getCgstAmount()) + "\n", F_BODY));
        }
        p.add(new Chunk("RS." + twoDp(inv.getGrandTotal()) + "\n", F_LABEL));

        if (inv.getPostTaxAdjustmentAmount().signum() != 0) {
            p.add(new Chunk(inv.getPostTaxAdjustmentLabel() + "\n", F_BODY));
            p.add(new Chunk("RS." + twoDp(inv.getNetPayable()) + "\n", F_LABEL));
        }
        return p;
    }

    /**
     * The proprietor's signature block, as it appears on every bill ASE issues.
     * Falls back to the plain text line if the image is missing, so a bill still
     * prints rather than failing outright.
     */
    private void signature(Document doc, CompanySettings co) {
        if (!co.isShowSignature()) return;
        try {
            ClassPathResource res = new ClassPathResource("signature/ase-signature.png");
            if (res.exists()) {
                Image img = Image.getInstance(res.getContentAsByteArray());
                img.scaleToFit(150f, 70f);
                img.setAlignment(Element.ALIGN_RIGHT);
                img.setSpacingBefore(14f);
                doc.add(img);
                return;
            }
        } catch (Exception e) {
            // fall through to the text line below
        }
        Paragraph p = new Paragraph(co.getSignatoryLine(), F_BODY);
        p.setAlignment(Element.ALIGN_RIGHT);
        p.setSpacingBefore(24);
        doc.add(p);
    }

    // ---------- annexure ------------------------------------------------------

    private void annexure(Document doc, InvoiceAnnexure a) {
        doc.add(new Paragraph(a.getTitle(), F_LABEL));
        doc.add(Chunk.NEWLINE);

        PdfPTable t = new PdfPTable(new float[]{60f, 40f});
        t.setWidthPercentage(80);
        t.setHorizontalAlignment(Element.ALIGN_LEFT);
        t.addCell(headerCell(a.getCol1Header(), Element.ALIGN_LEFT));
        t.addCell(headerCell(a.getCol2Header(), Element.ALIGN_LEFT));

        for (InvoiceAnnexureRow row : a.getRows()) {
            t.addCell(plainCell(row.getCol1()));
            t.addCell(plainCell(row.getCol2() == null ? "" : row.getCol2()));
        }
        doc.add(t);

        if (notBlank(a.getFooterText())) {
            Paragraph f = new Paragraph(a.getFooterText(), F_BODY);
            f.setSpacingBefore(18);
            doc.add(f);
        }
    }

    // ---------- small helpers -------------------------------------------------

    private Paragraph centred(String text, Font f) {
        Paragraph p = new Paragraph(text, f);
        p.setAlignment(Element.ALIGN_CENTER);
        return p;
    }

    private PdfPCell headerCell(String text, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, F_LABEL));
        c.setHorizontalAlignment(align);
        c.setPadding(5);
        return c;
    }

    private PdfPCell plainCell(String text) {
        PdfPCell c = new PdfPCell(new Phrase(text, F_BODY));
        c.setPadding(4);
        return c;
    }

    /** ASE's charge-line notation: 3000 -> "3000/-", 1106.50 -> "1106.50/-". */
    private String slash(BigDecimal v) {
        if (v == null) return "";
        BigDecimal x = v.setScale(2, RoundingMode.HALF_UP);
        String s = x.stripTrailingZeros().scale() <= 0
                ? x.setScale(0, RoundingMode.HALF_UP).toPlainString()
                : x.toPlainString();
        return s + "/-";
    }

    /** Totals notation: always two decimals, ungrouped, as the bills print them. */
    private String twoDp(BigDecimal v) {
        return v == null ? "" : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** 9.00 -> "9", so the bill reads "SGST 9%" rather than "SGST 9.00%". */
    private String rate(BigDecimal r) {
        return r.stripTrailingZeros().toPlainString();
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
