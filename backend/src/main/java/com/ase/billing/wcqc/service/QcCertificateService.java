package com.ase.billing.wcqc.service;

import com.ase.billing.wcqc.domain.Certificate;
import com.ase.billing.wcqc.domain.enums.CertificateType;
import com.ase.billing.wcqc.domain.enums.CertificateVariant;
import com.ase.billing.wcqc.web.dto.CertificateDtos.ExtractedInvoiceData;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

/**
 * QC-specific defaults, plus the extra validation the COPROCAFE variant needs
 * (its Word export has no sensible fallback for a missing bag count or
 * moisture figure the way a PDF table cell might).
 */
@Service
public class QcCertificateService {

    private static final DateTimeFormatter DECL_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public CertificateType type() {
        return CertificateType.QC;
    }

    public String defaultSummaryStatementLabel() {
        return "QUALITY:";
    }

    public String suggestDeclarationLine(ExtractedInvoiceData extracted) {
        if (extracted.invoiceNumber() == null || extracted.invoiceDate() == null) return null;
        return "Declaration: -INVOICE NO. %s. DT:%s"
                .formatted(extracted.invoiceNumber(), extracted.invoiceDate().format(DECL_DATE));
    }

    /**
     * COPROCAFE QC is the only certificate type this module exports as a Word
     * document, and its fixed sentence needs bag count, total weight, variety
     * and moisture filled in — none of those have a safe default. Returns the
     * field names still missing; an empty list means the Word export can run.
     */
    public List<String> missingCoprocafeFields(Certificate c) {
        List<String> missing = new ArrayList<>();
        if (c.getVariant() != CertificateVariant.COPROCAFE) return missing;
        if (c.getCoprocafeBagsCount() == null) missing.add("coprocafeBagsCount");
        if (c.getCoprocafeTotalKg() == null) missing.add("coprocafeTotalKg");
        if (c.getCoprocafeMoisturePercent() == null) missing.add("coprocafeMoisturePercent");
        if (c.getCoprocafeVariety() == null || c.getCoprocafeVariety().isBlank()) missing.add("coprocafeVariety");
        if (c.getMarksAndNos() == null || c.getMarksAndNos().isBlank()) missing.add("marksAndNos");
        return missing;
    }
}
