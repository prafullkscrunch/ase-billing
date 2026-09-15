package com.ase.billing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "invoice_sequences",
       uniqueConstraints = @UniqueConstraint(columnNames = {"customer_id", "financial_year"}))
@Getter
@Setter
@NoArgsConstructor
public class InvoiceSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "financial_year", nullable = false, length = 9)
    private String financialYear;

    @Column(name = "next_number", nullable = false)
    private Integer nextNumber;

    public InvoiceSequence(Customer customer, String financialYear, Integer nextNumber) {
        this.customer = customer;
        this.financialYear = financialYear;
        this.nextNumber = nextNumber;
    }
}
