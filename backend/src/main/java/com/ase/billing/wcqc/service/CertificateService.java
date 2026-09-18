package com.ase.billing.wcqc.service;

import com.ase.billing.wcqc.domain.Certificate;
import com.ase.billing.wcqc.domain.enums.CertificateStatus;
import com.ase.billing.wcqc.repo.CertificateRepository;
import com.ase.billing.wcqc.web.dto.CertificateDtos.CertificateFieldsRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/**
 * Plain CRUD for certificates, shared by WcCertificateService and
 * QcCertificateService rather than duplicated in each. Type-specific
 * defaulting/validation lives in those two classes, not here.
 */
@Service
public class CertificateService {

    private static final Logger log = LoggerFactory.getLogger(CertificateService.class);

    private final CertificateRepository repo;

    public CertificateService(CertificateRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public Certificate create(CertificateFieldsRequest req) {
        Certificate c = new Certificate();
        apply(c, req);
        Certificate saved = repo.save(c);
        log.info("Created {} certificate id={} mark={}", saved.getType(), saved.getId(), saved.getMarksAndNos());
        return saved;
    }

    @Transactional
    public Certificate update(Long id, CertificateFieldsRequest req) {
        Certificate c = get(id);
        apply(c, req);
        Certificate saved = repo.save(c);
        log.info("Updated {} certificate id={} mark={}", saved.getType(), saved.getId(), saved.getMarksAndNos());
        return saved;
    }

    @Transactional(readOnly = true)
    public Certificate get(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No certificate with id " + id));
    }

    @Transactional
    public void delete(Long id) {
        log.info("Deleting certificate id={}", id);
        repo.deleteById(id);
    }

    private void apply(Certificate c, CertificateFieldsRequest req) {
        c.setType(req.type());
        c.setVariant(req.variant() != null ? req.variant() : c.getVariant());
        c.setSourceInvoiceFileName(req.sourceInvoiceFileName());
        c.setMarksAndNos(req.marksAndNos());
        c.setInvoiceNumber(req.invoiceNumber());
        c.setInvoiceDate(req.invoiceDate());
        c.setDeclarationLine(req.declarationLine());
        c.setConsigneeNotifyBlock(req.consigneeNotifyBlock());
        if (req.preCarriageBy() != null) c.setPreCarriageBy(req.preCarriageBy());
        c.setPlaceOfReceipt(req.placeOfReceipt());
        if (req.countryOfOrigin() != null) c.setCountryOfOrigin(req.countryOfOrigin());
        if (req.portOfLoading() != null) c.setPortOfLoading(req.portOfLoading());
        c.setPortOfDischarge(req.portOfDischarge());
        c.setFinalDestination(req.finalDestination());
        c.setCountryOfFinalDestination(req.countryOfFinalDestination());
        c.setMarksColumnText(req.marksColumnText());
        c.setDescriptionColumnText(req.descriptionColumnText());
        c.setQuantityColumnText(req.quantityColumnText());
        c.setSummaryStatementLabel(req.summaryStatementLabel());
        c.setSummaryStatementLines(req.summaryStatementLines());
        c.setCoprocafeBagsCount(req.coprocafeBagsCount());
        c.setCoprocafeTotalKg(req.coprocafeTotalKg());
        if (req.coprocafeMoisturePercent() != null) c.setCoprocafeMoisturePercent(req.coprocafeMoisturePercent());
        c.setCoprocafeVariety(req.coprocafeVariety());
        c.setNotes(req.notes());

        // Editing a certificate's fields after it was generated means the last
        // export no longer reflects what's saved — back it to DRAFT so the
        // history screen doesn't claim a stale export is current.
        if (c.getStatus() == CertificateStatus.GENERATED) {
            c.setStatus(CertificateStatus.DRAFT);
        }
    }
}
