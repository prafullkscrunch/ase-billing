package com.ase.billing.domain;

import com.ase.billing.domain.enums.PdfLayout;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "service_categories")
@Getter
@Setter
public class ServiceCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** CNF, T, S, TA, CB. Goes into the invoice number and the SOA category column. */
    @Column(nullable = false, unique = true, length = 8)
    private String code;

    @Column(nullable = false, length = 120)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "pdf_layout", nullable = false, length = 16)
    private PdfLayout pdfLayout = PdfLayout.ITEMISED;

    @Column(name = "requires_shipment", nullable = false)
    private boolean requiresShipment = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(nullable = false)
    private boolean active = true;
}
