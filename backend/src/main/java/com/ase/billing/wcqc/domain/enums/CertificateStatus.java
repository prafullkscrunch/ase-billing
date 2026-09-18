package com.ase.billing.wcqc.domain.enums;

/**
 * DRAFT: fields captured (from upload or by hand) and saved, nothing printed yet.
 * GENERATED: at least one export (PDF/Excel/Word) has been produced from these
 * fields. Editing a GENERATED certificate and exporting again just refreshes
 * generatedAt/generatedBy — it does not fork a new certificate.
 */
public enum CertificateStatus {
    DRAFT,
    GENERATED
}
