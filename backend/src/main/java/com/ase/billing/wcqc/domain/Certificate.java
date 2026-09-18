package com.ase.billing.wcqc.domain;

import com.ase.billing.wcqc.domain.enums.CertificateStatus;
import com.ase.billing.wcqc.domain.enums.CertificateType;
import com.ase.billing.wcqc.domain.enums.CertificateVariant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A single WC or QC certificate, completely independent of the Billing module's
 * Invoice/Shipment tables. Nothing here has a foreign key into billing — an
 * uploaded invoice is read once, for extraction, and never referenced again.
 *
 * <p>Field shape reflects what studying ~500 real WC/QC files in wc.zip and
 * qc.zip actually showed, not a guess at what a certificate "should" look
 * like:
 *
 * <ul>
 *   <li>Rows 1-20 of the historical sheets (exporter block, consignee/notify,
 *       pre-carriage, ports, destination) sit at the same cell coordinates in
 *       every sample checked. Those become real, individually-editable fields
 *       below.</li>
 *   <li>Rows ~21-31 (marks, packaging, grade, weights/quality, HS code,
 *       totals) do NOT sit at consistent coordinates — different clerks typed
 *       a different number of lines in a different order depending on how
 *       long the description ran. Modelling that as small atomic fields would
 *       mean silently reformatting wording that was typed free-hand, which
 *       is exactly what the "never paraphrase, never guess" requirement
 *       rules out. Those sections are instead three ordered, multi-line text
 *       blocks (marks/left column, description/goods column, quantity
 *       column) that the operator reviews and edits as whole lines, not
 *       fields the system recomposes from smaller pieces.</li>
 *   <li>The certification sentence at the foot of both templates ("We
 *       declare that this weight/quality certificate shows...") was
 *       byte-for-byte identical, typos included, across every sample of its
 *       type. It is never stored per-certificate and never editable — see
 *       CertificateWording.</li>
 * </ul>
 */
@Entity
@Table(name = "certificates")
@Getter
@Setter
public class Certificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private CertificateType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CertificateVariant variant = CertificateVariant.STANDARD;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CertificateStatus status = CertificateStatus.DRAFT;

    /** Name of the invoice file the operator uploaded, kept for the history screen. Nullable — a certificate can be started from a blank form. */
    @Column(name = "source_invoice_file_name", length = 255)
    private String sourceInvoiceFileName;

    /**
     * The shipment's own mark number, e.g. "14/1850/2026/377" — printed as
     * "Marks & Nos" on the certificate. This is deliberately NOT a WC/QC-only
     * serial: studying the historical files found no certificate-specific
     * number anywhere on the document itself. The only identifying number
     * that appears is this mark, which is also the one printed on the
     * matching invoice. Reusing it (rather than minting a new sequence) is
     * what the historical documents actually show, so that is what this
     * module does. See CertificateHistoryService for how this is used as the
     * history list's "WC/QC number" column.
     */
    @Column(name = "marks_and_nos", nullable = false, length = 80)
    private String marksAndNos;

    @Column(name = "invoice_number", length = 60)
    private String invoiceNumber;

    @Column(name = "invoice_date")
    private LocalDate invoiceDate;

    /**
     * The "Declaration: -INVOICE NO. ... DT:..." line. Pre-filled from
     * invoiceNumber/invoiceDate in the dominant historical format, but kept
     * as its own editable field rather than always recomposed, since a
     * handful of historical bills carry slightly different punctuation and
     * the operator may need to match one exactly.
     */
    @Column(name = "declaration_line", length = 200)
    private String declarationLine;

    @Column(name = "consignee_notify_block", columnDefinition = "TEXT")
    private String consigneeNotifyBlock;

    @Column(name = "pre_carriage_by", length = 80)
    private String preCarriageBy = "ROAD";

    @Column(name = "place_of_receipt", length = 80)
    private String placeOfReceipt;

    @Column(name = "country_of_origin", length = 80)
    private String countryOfOrigin = "INDIA";

    @Column(name = "port_of_loading", length = 80)
    private String portOfLoading = "MANGALORE";

    @Column(name = "port_of_discharge", length = 80)
    private String portOfDischarge;

    @Column(name = "final_destination", length = 80)
    private String finalDestination;

    @Column(name = "country_of_final_destination", length = 80)
    private String countryOfFinalDestination;

    /** Column A body, rows ~22 on — marks/origin/grade lines, newline-separated, as the operator wants them printed. */
    @Column(name = "marks_column_text", columnDefinition = "TEXT")
    private String marksColumnText;

    /** Column B/C body — packaging + description of goods lines, newline-separated. */
    @Column(name = "description_column_text", columnDefinition = "TEXT")
    private String descriptionColumnText;

    /** Column E body — "QUANTITY" / "IN MT" / the MT figure, newline-separated. */
    @Column(name = "quantity_column_text", columnDefinition = "TEXT")
    private String quantityColumnText;

    /** "WEIGHT:-" on a WC, a short quality line on a QC. */
    @Column(name = "summary_statement_label", length = 40)
    private String summaryStatementLabel;

    /** The weight breakdown sentences (WC) or the quality statement (QC), newline-separated, exactly as they should print. */
    @Column(name = "summary_statement_lines", columnDefinition = "TEXT")
    private String summaryStatementLines;

    // ---- COPROCAFE QC only (variant == COPROCAFE) --------------------------

    @Column(name = "coprocafe_bags_count")
    private Integer coprocafeBagsCount;

    @Column(name = "coprocafe_total_kg", precision = 12, scale = 2)
    private BigDecimal coprocafeTotalKg;

    @Column(name = "coprocafe_moisture_percent", precision = 5, scale = 2)
    private BigDecimal coprocafeMoisturePercent = new BigDecimal("12");

    /** e.g. "INDIA ROBUSTA CHERRY AA" — dropped into "...OF THE {variety} VARIETY...". */
    @Column(name = "coprocafe_variety", length = 120)
    private String coprocafeVariety;

    @Column(length = 500)
    private String notes;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Column(name = "generated_by", length = 80)
    private String generatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
