package com.ase.billing.repo;

import com.ase.billing.domain.InvoiceSequence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InvoiceSequenceRepository extends JpaRepository<InvoiceSequence, Long> {
    Optional<InvoiceSequence> findByCustomerIdAndFinancialYear(Long customerId, String financialYear);
}
