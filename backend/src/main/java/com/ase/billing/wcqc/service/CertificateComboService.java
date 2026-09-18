package com.ase.billing.wcqc.service;

import com.ase.billing.wcqc.domain.Certificate;
import com.ase.billing.wcqc.domain.enums.CertificateType;
import com.ase.billing.wcqc.domain.enums.CertificateVariant;
import com.ase.billing.wcqc.web.dto.CertificateDtos.CertificateFieldsRequest;
import com.ase.billing.wcqc.web.dto.CertificateDtos.ComboCertificateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backs the combo workflow: one invoice upload, one set of shared fields
 * verified once, both a WC and a QC certificate created together — what the
 * shipment actually needs, since ASE issues both for the same shipment from
 * the same source data. This does not merge WC and QC into one entity; it
 * creates two ordinary Certificate rows with the shared fields copied into
 * each, so History, editing, and every export still work exactly as they do
 * for a certificate created the standalone way.
 */
@Service
public class CertificateComboService {

    private static final Logger log = LoggerFactory.getLogger(CertificateComboService.class);

    private final CertificateService certificateService;

    public CertificateComboService(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @Transactional
    public Certificate[] create(ComboCertificateRequest req) {
        CertificateFieldsRequest wcReq = new CertificateFieldsRequest(
                CertificateType.WC, CertificateVariant.STANDARD, null,
                req.marksAndNos(), req.invoiceNumber(), req.invoiceDate(), req.declarationLine(),
                req.consigneeNotifyBlock(), req.preCarriageBy(), req.placeOfReceipt(), req.countryOfOrigin(),
                req.portOfLoading(), req.portOfDischarge(), req.finalDestination(), req.countryOfFinalDestination(),
                req.marksColumnText(), req.descriptionColumnText(), req.quantityColumnText(),
                "WEIGHT:-", req.wcSummaryStatementLines(),
                null, null, null, null, req.notes());

        CertificateVariant qcVariant = req.qcVariant() != null ? req.qcVariant() : CertificateVariant.STANDARD;
        CertificateFieldsRequest qcReq = new CertificateFieldsRequest(
                CertificateType.QC, qcVariant, null,
                req.marksAndNos(), req.invoiceNumber(), req.invoiceDate(), req.declarationLine(),
                req.consigneeNotifyBlock(), req.preCarriageBy(), req.placeOfReceipt(), req.countryOfOrigin(),
                req.portOfLoading(), req.portOfDischarge(), req.finalDestination(), req.countryOfFinalDestination(),
                req.marksColumnText(), req.descriptionColumnText(), req.quantityColumnText(),
                "QUALITY:", req.qcSummaryStatementLines(),
                req.coprocafeBagsCount(), req.coprocafeTotalKg(), req.coprocafeMoisturePercent(),
                req.coprocafeVariety(), req.notes());

        Certificate wc = certificateService.create(wcReq);
        Certificate qc = certificateService.create(qcReq);
        log.info("Combo-created WC id={} and QC id={} for mark={}", wc.getId(), qc.getId(), req.marksAndNos());
        return new Certificate[]{wc, qc};
    }
}
