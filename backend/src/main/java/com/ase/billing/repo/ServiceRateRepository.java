package com.ase.billing.repo;

import com.ase.billing.domain.ServiceRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ServiceRateRepository extends JpaRepository<ServiceRate, Long> {

    /**
     * Resolution order is customer rate first, then global rate, most recent
     * effective_from wins. A manual override on the invoice beats both and is
     * handled in the service layer, not here.
     */
    @Query("""
           select r from ServiceRate r
            where r.service.id = :serviceId
              and (r.customer is null or r.customer.id = :customerId)
              and r.effectiveFrom <= :onDate
              and (r.effectiveTo is null or r.effectiveTo >= :onDate)
            order by case when r.customer is null then 1 else 0 end asc,
                     r.effectiveFrom desc
           """)
    List<ServiceRate> resolve(@Param("serviceId") Long serviceId,
                              @Param("customerId") Long customerId,
                              @Param("onDate") LocalDate onDate);
}
