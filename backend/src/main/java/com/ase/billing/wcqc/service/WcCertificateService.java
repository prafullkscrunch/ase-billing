package com.ase.billing.wcqc.service;

import com.ase.billing.wcqc.domain.enums.CertificateType;
import com.ase.billing.wcqc.domain.enums.CertificateVariant;
import com.ase.billing.wcqc.web.dto.CertificateDtos.ExtractedInvoiceData;

import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;

/**
 * Turns an ExtractedInvoiceData (or a blank start) into the WC-specific
 * defaults an operator sees on the Generate WC screen — the "WEIGHT:-" label
 * and the standard declaration line. Nothing here is persisted directly; the
 * controller hands the operator's confirmed/edited version to
 * CertificateService.create/update.
 */
@Service
public class WcCertificateService {

    private static final DateTimeFormatter DECL_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public CertificateType type() {
        return CertificateType.WC;
    }

    public String defaultSummaryStatementLabel() {
        return "WEIGHT:-";
    }

    /**
     * "Declaration: -INVOICE NO. ES/HC/2027/181. DT:06/08/2026" — the dominant
     * format across the WC files sampled. Punctuation varies slightly from
     * clerk to clerk in the historical files, which is why this is only a
     * starting suggestion: the operator edits it before generating if the
     * exact punctuation needs to match something specific.
     */
    public String suggestDeclarationLine(ExtractedInvoiceData extracted) {
        if (extracted.invoiceNumber() == null || extracted.invoiceDate() == null) return null;
        return "Declaration: -INVOICE NO. %s. DT:%s"
                .formatted(extracted.invoiceNumber(), extracted.invoiceDate().format(DECL_DATE));
    }

    public CertificateVariant onlyVariant() {
        return CertificateVariant.STANDARD;
    }
}
