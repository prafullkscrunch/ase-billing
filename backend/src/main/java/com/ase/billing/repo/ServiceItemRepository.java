package com.ase.billing.repo;

import com.ase.billing.domain.ServiceItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceItemRepository extends JpaRepository<ServiceItem, Long> {

    List<ServiceItem> findByCategoryCodeIgnoreCaseAndActiveTrueOrderBySortOrderAsc(String categoryCode);

    List<ServiceItem> findByActiveTrueOrderBySortOrderAsc();
}
