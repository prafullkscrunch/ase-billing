package com.ase.billing.domain;

import com.ase.billing.domain.enums.GstTreatment;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "customers")
@Getter
@Setter
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Goes into the invoice number: 'HC' -> ASE/HC/CNF/439/2026-27 */
    @Column(nullable = false, unique = true, length = 16)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "address_line1", length = 160) private String addressLine1;
    @Column(name = "address_line2", length = 160) private String addressLine2;
    @Column(name = "address_line3", length = 160) private String addressLine3;

    @Column(length = 80) private String city;
    @Column(length = 80) private String state;
    @Column(length = 12) private String pin;
    @Column(length = 20) private String gstin;

    @Column(name = "contact_person", length = 120) private String contactPerson;
    @Column(length = 40)  private String phone;
    @Column(length = 120) private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "gst_treatment", nullable = false, length = 8)
    private GstTreatment gstTreatment = GstTreatment.INTRA;

    @Column(nullable = false)
    private boolean active = true;
}
