package com.ase.billing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "shipments")
@Getter
@Setter
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    /** e.g. 'ES/HC/2027/094' */
    @Column(name = "hc_invoice_number", nullable = false, length = 60)
    private String hcInvoiceNumber;

    /** Full ICO mark as printed. Can be a range: '14/1850/2026/292-295'. */
    @Column(name = "ico_mark_full", nullable = false, length = 80)
    private String icoMarkFull;

    /** The short mark the SOA carries. Derived from icoMarkFull; ranges take the first value. */
    @Column(name = "soa_mark_no", nullable = false, length = 24)
    private String soaMarkNo;

    @Column(name = "container_count", nullable = false)
    private Integer containerCount;

    @Column(name = "container_size", nullable = false, length = 8)
    private String containerSize = "20";

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal teu;

    @Column(name = "destination_country", length = 80)
    private String destinationCountry;

    @Column(length = 255)
    private String notes;

    /**
     * Both teu and soaMarkNo are NOT NULL in the schema and are derived, never
     * typed. Deriving them here means no caller can save a shipment that violates
     * the constraint, and no PER_TEU line can silently bill 1 TEU.
     */
    @PrePersist
    @PreUpdate
    public void deriveFields() {
        if (containerCount != null && containerSize != null) {
            teu = teuFor(containerCount, containerSize);
        }
        if (icoMarkFull != null && !icoMarkFull.isBlank()) {
            soaMarkNo = deriveSoaMark(icoMarkFull);
        }
    }

    /** Prints as '3X20' in the invoice header block. */
    public String containerNotation() {
        return containerCount + "X" + containerSize;
    }

    /** 20ft = 1 TEU, 40ft = 2 TEU. */
    public static BigDecimal teuFor(int containerCount, String containerSize) {
        BigDecimal perContainer = "40".equals(containerSize)
                ? new BigDecimal("2.00") : new BigDecimal("1.00");
        return perContainer.multiply(BigDecimal.valueOf(containerCount));
    }

    /** '14/1850/2026/292-295' -> '292'.  '14/1850/2026/283' -> '283'. */
    public static String deriveSoaMark(String icoMarkFull) {
        String tail = icoMarkFull.substring(icoMarkFull.lastIndexOf('/') + 1).trim();
        int dash = tail.indexOf('-');
        return dash > 0 ? tail.substring(0, dash).trim() : tail;
    }
}
