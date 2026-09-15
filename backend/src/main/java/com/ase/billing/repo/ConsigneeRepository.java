package com.ase.billing.repo;

import com.ase.billing.domain.Consignee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConsigneeRepository extends JpaRepository<Consignee, Long> {

    List<Consignee> findByActiveTrueOrderByNameAsc();

    Optional<Consignee> findByNameIgnoreCase(String name);
}
