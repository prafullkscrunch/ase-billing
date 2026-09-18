package com.ase.billing.repo;

import com.ase.billing.domain.ServiceRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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

    /**
     * The general house rate only, ignoring any customer-specific override —
     * what the Quotation (rate card) screen reads and writes, since that
     * screen manages the general rate directly rather than any one
     * customer's arrangement.
     */
    @Query("""
           select r from ServiceRate r
            where r.service.id = :serviceId
              and r.customer is null
              and r.effectiveFrom <= :onDate
              and (r.effectiveTo is null or r.effectiveTo >= :onDate)
            order by r.effectiveFrom desc
           """)
    List<ServiceRate> resolveGlobal(@Param("serviceId") Long serviceId, @Param("onDate") LocalDate onDate);

    /**
     * Every rate row that records this invoice as the one that set it (via
     * "keep this rate for next time"). Looked up before deleting an invoice —
     * the FK on set_from_invoice would otherwise refuse the delete outright.
     */
    List<ServiceRate> findBySetFromInvoice(Long invoiceId);

    /**
     * The global rate row closed on a given date — i.e. the one a later
     * change superseded. Used when undoing a rate change: this is what the
     * master rate reverts to.
     */
    Optional<ServiceRate> findByServiceIdAndCustomerIsNullAndEffectiveTo(Long serviceId, LocalDate effectiveTo);
}
