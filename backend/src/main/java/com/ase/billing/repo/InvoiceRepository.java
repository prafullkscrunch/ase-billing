package com.ase.billing.repo;

import com.ase.billing.domain.Invoice;
import com.ase.billing.domain.enums.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    /**
     * The SOA query. Ordered by running number, which is how the supplied workbook
     * is ordered, and which keeps each shipment's CNF/T pair adjacent.
     * CANCELLED invoices are excluded; DRAFT are too, so an unfinished bill never
     * reaches a GST return.
     */
    @Query("""
           select i from Invoice i
             join fetch i.category c
             left join fetch i.shipment s
            where i.customer.id = :customerId
              and i.invoiceDate between :from and :to
              and i.status = :status
            order by i.runningNumber asc
           """)
    List<Invoice> findForSoa(@Param("customerId") Long customerId,
                             @Param("from") LocalDate from,
                             @Param("to") LocalDate to,
                             @Param("status") InvoiceStatus status);

    List<Invoice> findByShipmentIdOrderByRunningNumberAsc(Long shipmentId);

    /**
     * The invoice list screen. Every filter is optional; a null means "any".
     * `text` is already lower-cased by the caller and matches the invoice number,
     * the HC invoice number or the ICO mark.
     */
    @Query("""
           select distinct i from Invoice i
             join fetch i.customer cu
             join fetch i.category c
             left join fetch i.shipment s
            where (:customerId is null or cu.id = :customerId)
              and (:from is null or i.invoiceDate >= :from)
              and (:to is null or i.invoiceDate <= :to)
              and (:categoryCode is null or upper(c.code) = upper(:categoryCode))
              and (:status is null or i.status = :status)
              and (:text is null
                   or lower(i.invoiceNumber) like concat('%', :text, '%')
                   or lower(coalesce(s.hcInvoiceNumber, '')) like concat('%', :text, '%')
                   or lower(coalesce(s.icoMarkFull, '')) like concat('%', :text, '%'))
            order by i.invoiceDate desc, i.runningNumber desc
           """)
    List<Invoice> search(@Param("customerId") Long customerId,
                         @Param("from") LocalDate from,
                         @Param("to") LocalDate to,
                         @Param("categoryCode") String categoryCode,
                         @Param("status") InvoiceStatus status,
                         @Param("text") String text);

    @Query("""
           select i from Invoice i
             join fetch i.category c
            where i.status = :status
              and i.invoiceDate between :from and :to
           """)
    List<Invoice> findFinalizedBetween(@Param("status") InvoiceStatus status,
                                       @Param("from") LocalDate from,
                                       @Param("to") LocalDate to);

    long countByStatus(InvoiceStatus status);

    /** The highest number issued for a customer in a financial year, or null. */
    @Query("""
           select max(i.runningNumber) from Invoice i
            where i.customer.id = :customerId
              and i.financialYear = :financialYear
           """)
    Integer highestRunningNumber(@Param("customerId") Long customerId,
                                 @Param("financialYear") String financialYear);

    boolean existsByCustomerIdAndFinancialYearAndRunningNumber(
            Long customerId, String financialYear, Integer runningNumber);

    /**
     * Invoices for a batch PDF, in issue order so the merged file reads like the
     * paper book. Every filter is optional.
     */
    @Query("""
           select i from Invoice i
             join fetch i.customer cu
             join fetch i.category c
             left join fetch i.shipment s
            where (:customerId is null or cu.id = :customerId)
              and (:status is null or i.status = :status)
              and (:fromNumber is null or i.runningNumber >= :fromNumber)
              and (:toNumber is null or i.runningNumber <= :toNumber)
              and (:financialYear is null or i.financialYear = :financialYear)
            order by i.runningNumber asc
           """)
    List<Invoice> findForBatch(@Param("customerId") Long customerId,
                               @Param("status") InvoiceStatus status,
                               @Param("fromNumber") Integer fromNumber,
                               @Param("toNumber") Integer toNumber,
                               @Param("financialYear") String financialYear);
}
