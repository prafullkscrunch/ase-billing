package com.ase.billing.wcqc.repo;

import com.ase.billing.wcqc.domain.Certificate;
import com.ase.billing.wcqc.domain.enums.CertificateType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {

    List<Certificate> findByTypeOrderByIdDesc(CertificateType type);

    List<Certificate> findAllByOrderByIdDesc();

    @Query("select c from Certificate c where "
         + "(:type is null or c.type = :type) and "
         + "(:q is null or lower(c.marksAndNos) like lower(concat('%', :q, '%')) "
         + "            or lower(c.invoiceNumber) like lower(concat('%', :q, '%'))) "
         + "order by c.id desc")
    List<Certificate> search(CertificateType type, String q);
}
