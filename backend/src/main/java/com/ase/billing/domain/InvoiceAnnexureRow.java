package com.ase.billing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "invoice_annexure_rows")
@Getter
@Setter
public class InvoiceAnnexureRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "annexure_id")
    private InvoiceAnnexure annexure;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    @Column(nullable = false, length = 200)
    private String col1;

    @Column(length = 200)
    private String col2;
}
