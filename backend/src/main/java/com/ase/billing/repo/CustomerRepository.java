package com.ase.billing.repo;

import com.ase.billing.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    List<Customer> findByActiveTrueOrderByNameAsc();
}
