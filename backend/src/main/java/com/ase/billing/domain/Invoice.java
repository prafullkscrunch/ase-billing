package com.ase.billing.domain;

import com.ase.billing.domain.enums.InvoiceStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoices")
@Getter
@Setter
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 60)
    private String invoiceNumber;

    @Column(name = "running_number", nullable = false)
    private Integer runningNumber;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    /** Derived from invoiceDate by FinancialYear.of(). Never set from user input. */
    @Column(name = "financial_year", nullable = false, length = 9)
    private String financialYear;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private ServiceCategory category;

    /** Null for CB and S invoices, which are not tied to a shipment. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id")
    private Shipment shipment;

    @Column(name = "hsn_code", nullable = false, length = 12)
    private String hsnCode = "996713";

    /** e.g. "EXPORT INCENTIVE CLAIM FOR THE YEAR 2025-2026" on CB bills. */
    @Column(name = "header_note", length = 255)
    private String headerNote;

    @Column(precision = 14, scale = 2, nullable = false)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "taxable_amount", precision = 14, scale = 2, nullable = false)
    private BigDecimal taxableAmount = BigDecimal.ZERO;

    @Column(name = "cgst_rate", precision = 5, scale = 2, nullable = false)
    private BigDecimal cgstRate = new BigDecimal("9.00");

    @Column(name = "cgst_amount", precision = 14, scale = 2, nullable = false)
    private BigDecimal cgstAmount = BigDecimal.ZERO;

    @Column(name = "sgst_rate", precision = 5, scale = 2, nullable = false)
    private BigDecimal sgstRate = new BigDecimal("9.00");

    @Column(name = "sgst_amount", precision = 14, scale = 2, nullable = false)
    private BigDecimal sgstAmount = BigDecimal.ZERO;

    @Column(name = "igst_rate", precision = 5, scale = 2, nullable = false)
    private BigDecimal igstRate = BigDecimal.ZERO;

    @Column(name = "igst_amount", precision = 14, scale = 2, nullable = false)
    private BigDecimal igstAmount = BigDecimal.ZERO;

    @Column(name = "grand_total", precision = 14, scale = 2, nullable = false)
    private BigDecimal grandTotal = BigDecimal.ZERO;

    /** e.g. "LESS DHL CHARGES A/C ASE-ACC RS 3758/-" on ASE/HC/S/438. */
    @Column(name = "post_tax_adjustment_label", length = 160)
    private String postTaxAdjustmentLabel;

    @Column(name = "post_tax_adjustment_amount", precision = 14, scale = 2, nullable = false)
    private BigDecimal postTaxAdjustmentAmount = BigDecimal.ZERO;

    /** grandTotal minus the post-tax adjustment. The SOA reports grandTotal, not this. */
    @Column(name = "net_payable", precision = 14, scale = 2, nullable = false)
    private BigDecimal netPayable = BigDecimal.ZERO;

    @Column(name = "amount_in_words", nullable = false, length = 400)
    private String amountInWords = "";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Column(name = "back_entered", nullable = false)
    private boolean backEntered = false;

    /** The financial year printed on a historical paper bill, when it disagrees with the date. */
    @Column(name = "printed_fy", length = 9)
    private String printedFy;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNo ASC")
    private List<InvoiceItem> items = new ArrayList<>();

    @OneToOne(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private InvoiceAnnexure annexure;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "created_by", length = 60)
    private String createdBy;

    @Column(name = "updated_by", length = 60)
    private String updatedBy;

    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

    @Column(name = "finalized_by", length = 60)
    private String finalizedBy;

    public void addItem(InvoiceItem item) {
        item.setInvoice(this);
        // max + 1, not size + 1: after a removal, size + 1 collides with an
        // existing sequence number and the two rows then order unpredictably.
        int next = items.stream()
                .map(InvoiceItem::getSequenceNo)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max().orElse(0) + 1;
        item.setSequenceNo(next);
        items.add(item);
    }

    /** Removes an item and closes the gap, so printed numbering stays 1..n. */
    public void removeItem(InvoiceItem item) {
        items.remove(item);
        item.setInvoice(null);
        renumberItems();
    }

    public void renumberItems() {
        int n = 1;
        for (InvoiceItem i : items) {
            i.setSequenceNo(n++);
        }
    }

    @PreUpdate
    void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isEditable() {
        return status == InvoiceStatus.DRAFT;
    }
}
