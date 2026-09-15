package com.ase.billing.repo;

import com.ase.billing.domain.ServiceCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, Long> {

    Optional<ServiceCategory> findByCodeIgnoreCase(String code);

    List<ServiceCategory> findByActiveTrueOrderBySortOrderAsc();
}
