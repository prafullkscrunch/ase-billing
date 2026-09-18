package com.ase.billing.wcqc.web;

import com.ase.billing.wcqc.domain.Certificate;
import com.ase.billing.wcqc.domain.enums.CertificateType;
import com.ase.billing.wcqc.domain.enums.CertificateVariant;
import com.ase.billing.wcqc.web.dto.CertificateDtos.CertificateView;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class CertificateMapper {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    public CertificateView toView(Certificate c) {
        boolean wordAvailable = c.getType() == CertificateType.QC && c.getVariant() == CertificateVariant.COPROCAFE;
        return new CertificateView(
                c.getId(), c.getType(), c.getVariant(), c.getStatus(),
                c.getSourceInvoiceFileName(), c.getMarksAndNos(), c.getInvoiceNumber(), c.getInvoiceDate(),
                c.getDeclarationLine(), c.getConsigneeNotifyBlock(), c.getPreCarriageBy(), c.getPlaceOfReceipt(),
                c.getCountryOfOrigin(), c.getPortOfLoading(), c.getPortOfDischarge(), c.getFinalDestination(),
                c.getCountryOfFinalDestination(), c.getMarksColumnText(), c.getDescriptionColumnText(),
                c.getQuantityColumnText(), c.getSummaryStatementLabel(), c.getSummaryStatementLines(),
                c.getCoprocafeBagsCount(), c.getCoprocafeTotalKg(), c.getCoprocafeMoisturePercent(),
                c.getCoprocafeVariety(), c.getNotes(),
                c.getGeneratedAt() != null ? TS.format(c.getGeneratedAt().atZone(ZoneId.systemDefault())) : null,
                c.getGeneratedBy(), wordAvailable);
    }
}
