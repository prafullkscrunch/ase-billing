package com.ase.billing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "invoice_item_sublines")
@Getter
@Setter
public class InvoiceItemSubline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_item_id")
    private InvoiceItem item;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    @Column(nullable = false, length = 300)
    private String text;
}
