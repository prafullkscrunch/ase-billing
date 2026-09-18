package com.ase.billing.wcqc.word;

import com.ase.billing.wcqc.domain.Certificate;
import com.ase.billing.wcqc.domain.CertificateWording;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Fills the real NKG COPROCAFE IBERICA S.A. quality-certificate Word template
 * (wcqc-templates/coprocafe-qc-master.docx — copied from an actual issued
 * certificate) rather than generating a Word document from scratch.
 *
 * <p>The template's dynamic values (bag count, total weight, variety, marks,
 * moisture) sit inside runs that were split at odd points by whoever last
 * hand-edited the source file (e.g. the mark number is spread across three
 * runs: "...202", "6", "/407"). Trying to locate and patch those exact run
 * boundaries would be fragile and would not generalise to a certificate whose
 * numbers have a different number of digits. Instead, each paragraph that
 * carries a dynamic value is matched by its fixed leading wording, all of its
 * runs are cleared, and one new run is written with the complete sentence —
 * same fixed wording, same position in the document, same fonts (inherited
 * from the paragraph's style), only the numbers/names changed. Every fixed
 * sentence fragment lives in CertificateWording, never inline here.
 */
@Service
public class CoprocafeWordService {

    private static final String TEMPLATE_PATH = "wcqc-templates/coprocafe-qc-master.docx";
    private static final String INDENT = " ".repeat(54); // matches the template's own indentation column

    public byte[] generate(Certificate c) throws IOException {
        try (InputStream in = new ClassPathResource(TEMPLATE_PATH).getInputStream();
             XWPFDocument doc = new XWPFDocument(in);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            List<XWPFParagraph> paragraphs = doc.getParagraphs();
            for (int i = 0; i < paragraphs.size(); i++) {
                XWPFParagraph p = paragraphs.get(i);
                String trimmed = fullText(p).trim();

                if (trimmed.startsWith("WE CERTIFY THAT THE")) {
                    replaceText(p, CertificateWording.COPROCAFE_BODY_TEMPLATE.formatted(
                            c.getCoprocafeBagsCount(), formatKg(c.getCoprocafeTotalKg()),
                            c.getCoprocafeVariety(), c.getMarksAndNos()));
                } else if (trimmed.startsWith("SAID COFFEE WAS LOADED")) {
                    // The next paragraph is the one carrying the moisture figure.
                    if (i + 1 < paragraphs.size()) {
                        replaceText(paragraphs.get(i + 1), CertificateWording.COPROCAFE_HUMIDITY_LINE_2_TEMPLATE
                                .formatted(formatPercent(c.getCoprocafeMoisturePercent())));
                    }
                } else if (trimmed.startsWith("MARKS:")) {
                    replaceText(p, INDENT + "MARKS: " + c.getMarksAndNos());
                } else if (trimmed.startsWith("MOISTURE:")) {
                    replaceText(p, INDENT + "MOISTURE: " + formatPercent(c.getCoprocafeMoisturePercent()) + " %");
                }
                // Titles, "TO WHOM IT MAY CONCERN", the ISO line and the
                // signature block carry no dynamic value and are left exactly
                // as the template has them.
            }

            doc.write(out);
            return out.toByteArray();
        }
    }

    private String fullText(XWPFParagraph p) {
        StringBuilder sb = new StringBuilder();
        for (XWPFRun r : p.getRuns()) {
            String t = r.getText(0);
            if (t != null) sb.append(t);
        }
        return sb.toString();
    }

    private void replaceText(XWPFParagraph p, String newText) {
        while (!p.getRuns().isEmpty()) {
            p.removeRun(0);
        }
        XWPFRun run = p.createRun();
        run.setText(newText);
    }

    private String formatKg(java.math.BigDecimal kg) {
        if (kg == null) return "";
        return kg.stripTrailingZeros().toPlainString();
    }

    private String formatPercent(java.math.BigDecimal pct) {
        if (pct == null) return "";
        return pct.stripTrailingZeros().toPlainString();
    }
}
