package com.ase.billing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * The statement printed as page 2 of S invoices:
 * 'HC SAMPLE PSC DETAILS-AUG -2026 ASE bill no 438', 17 consignee rows, then a
 * hand-totalled footer. Part of the invoice document, not an upload.
 */
@Entity
@Table(name = "invoice_annexures")
@Getter
@Setter
public class InvoiceAnnexure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id")
    private Invoice invoice;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "col1_header", nullable = false, length = 80)
    private String col1Header = "CONSIGNEE";

    @Column(name = "col2_header", nullable = false, length = 80)
    private String col2Header = "PSC  DT";

    @Column(name = "footer_text", columnDefinition = "TEXT")
    private String footerText;

    /**
     * The row count is the set count billed. 17 consignee rows means the invoice
     * line reads "17 SET PSC @RS 2500/-", so the statement and the bill can never
     * disagree about how many sets were done.
     */
    @Column(name = "drives_quantity", nullable = false)
    private boolean drivesQuantity = true;

    @OneToMany(mappedBy = "annexure", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNo ASC")
    private List<InvoiceAnnexureRow> rows = new ArrayList<>();
}
