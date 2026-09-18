package com.ase.billing.wcqc.web.dto;

import com.ase.billing.wcqc.domain.enums.CertificateStatus;
import com.ase.billing.wcqc.domain.enums.CertificateType;
import com.ase.billing.wcqc.domain.enums.CertificateVariant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The WC/QC module's wire contract. Kept in its own class, in its own
 * package, so nothing here can be confused with — or accidentally reuse —
 * com.ase.billing.web.dto.Dtos.
 */
public final class CertificateDtos {

    private CertificateDtos() {}

    /**
     * What InvoiceUploadService hands back after reading an uploaded invoice
     * file. Every field the extractor could not confidently read is left
     * null and its name appears in {@code missingFields} — the frontend
     * shows "Required information missing — please enter manually" for
     * exactly those, and never a guessed value.
     *
     * <p>{@code consigneeNotifyBlock} is the module's default suggestion for
     * the certificate's single combined "CONSIGNEE/NOTIFY" block — normally
     * the invoice's own NOTIFY party, since that's what the historical
     * certificates for this shipment pattern actually used. Because an
     * invoice can carry a second, distinct party (a Buyer, or a bare
     * Consignee line when there's no separate Buyer block), that second
     * option comes back as {@code alternatePartyBlock}/{@code
     * alternatePartyLabel} so the operator can switch to it — some
     * shipments' certificates use the buyer/consignee party instead of the
     * notify party, and there's no reliable way to know which without
     * asking.
     */
    public record ExtractedInvoiceData(
            String sourceFileName,
            String suggestedMarksAndNos,
            String invoiceNumber,
            LocalDate invoiceDate,
            String consigneeNotifyBlock,
            String alternatePartyBlock,
            String alternatePartyLabel,
            String preCarriageBy,
            String placeOfReceipt,
            String countryOfOrigin,
            String portOfLoading,
            String portOfDischarge,
            String finalDestination,
            String countryOfFinalDestination,
            String marksColumnText,
            String descriptionColumnText,
            String quantityColumnText,
            /** Auto-composed weight-breakdown suggestion (WC only) when bag count and both totals were found in the invoice — always a suggestion to review, never final. */
            String summaryStatementLines,
            /** Auto-composed quality-statement suggestion (QC only), built from the invoice's own grade/origin lines — always a suggestion to review, never final. */
            String qcQualityStatementLines,
            List<String> missingFields
    ) {}

    /** Create or update a certificate's fields. Every field here is something a human typed or confirmed — nothing is computed by this request. */
    public record CertificateFieldsRequest(
            @NotNull CertificateType type,
            CertificateVariant variant,
            String sourceInvoiceFileName,
            @NotBlank(message = "The certificate needs the shipment's mark number") String marksAndNos,
            String invoiceNumber,
            LocalDate invoiceDate,
            String declarationLine,
            String consigneeNotifyBlock,
            String preCarriageBy,
            String placeOfReceipt,
            String countryOfOrigin,
            String portOfLoading,
            String portOfDischarge,
            String finalDestination,
            String countryOfFinalDestination,
            String marksColumnText,
            String descriptionColumnText,
            String quantityColumnText,
            String summaryStatementLabel,
            String summaryStatementLines,
            Integer coprocafeBagsCount,
            BigDecimal coprocafeTotalKg,
            BigDecimal coprocafeMoisturePercent,
            String coprocafeVariety,
            String notes
    ) {}

    public record CertificateView(
            Long id,
            CertificateType type,
            CertificateVariant variant,
            CertificateStatus status,
            String sourceInvoiceFileName,
            String marksAndNos,
            String invoiceNumber,
            LocalDate invoiceDate,
            String declarationLine,
            String consigneeNotifyBlock,
            String preCarriageBy,
            String placeOfReceipt,
            String countryOfOrigin,
            String portOfLoading,
            String portOfDischarge,
            String finalDestination,
            String countryOfFinalDestination,
            String marksColumnText,
            String descriptionColumnText,
            String quantityColumnText,
            String summaryStatementLabel,
            String summaryStatementLines,
            Integer coprocafeBagsCount,
            BigDecimal coprocafeTotalKg,
            BigDecimal coprocafeMoisturePercent,
            String coprocafeVariety,
            String notes,
            String generatedAt,
            String generatedBy,
            boolean wordAvailable
    ) {}

    /** One row of the Certificate History table. Lighter than CertificateView on purpose. */
    public record CertificateSummaryView(
            Long id,
            CertificateType type,
            CertificateVariant variant,
            CertificateStatus status,
            String marksAndNos,
            String invoiceNumber,
            LocalDate invoiceDate,
            /** First line of the consignee/notify block — the closest thing this module has to a "customer" column, since certificates are not linked to a billing Customer record. */
            String consigneeName,
            String createdAt,
            String generatedAt,
            boolean wordAvailable
    ) {}

    /**
     * "Generate both WC and QC from one upload, one verify step" — the combo
     * workflow. Shared shipment/route/body fields appear once; the two
     * certificates' own summary sections (WC's weight breakdown, QC's
     * quality statement) are separate because their wording is genuinely
     * different, not because the certificates are otherwise independent.
     */
    public record ComboCertificateRequest(
            @NotBlank(message = "The certificate needs the shipment's mark number") String marksAndNos,
            String invoiceNumber,
            LocalDate invoiceDate,
            String declarationLine,
            String consigneeNotifyBlock,
            String preCarriageBy,
            String placeOfReceipt,
            String countryOfOrigin,
            String portOfLoading,
            String portOfDischarge,
            String finalDestination,
            String countryOfFinalDestination,
            String marksColumnText,
            String descriptionColumnText,
            String quantityColumnText,
            String wcSummaryStatementLines,
            String qcSummaryStatementLines,
            CertificateVariant qcVariant,
            Integer coprocafeBagsCount,
            BigDecimal coprocafeTotalKg,
            BigDecimal coprocafeMoisturePercent,
            String coprocafeVariety,
            String notes
    ) {}

    public record ComboCertificateView(CertificateView wc, CertificateView qc) {}
}
