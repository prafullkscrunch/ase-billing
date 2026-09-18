package com.ase.billing.wcqc.service;

import com.ase.billing.wcqc.domain.Certificate;
import com.ase.billing.wcqc.domain.enums.CertificateType;
import com.ase.billing.wcqc.domain.enums.CertificateVariant;
import com.ase.billing.wcqc.repo.CertificateRepository;
import com.ase.billing.wcqc.web.dto.CertificateDtos.CertificateSummaryView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Backs the Certificate History screen. Deliberately its own service rather
 * than folded into CertificateService, so the history/list concerns (search,
 * view shaping) stay separate from the create/update/delete concerns.
 */
@Service
public class CertificateHistoryService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private final CertificateRepository repo;

    public CertificateHistoryService(CertificateRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<CertificateSummaryView> list(CertificateType type, String q) {
        List<Certificate> rows = (type == null && (q == null || q.isBlank()))
                ? repo.findAllByOrderByIdDesc()
                : repo.search(type, (q == null || q.isBlank()) ? null : q);
        return rows.stream().map(this::toSummary).toList();
    }

    private CertificateSummaryView toSummary(Certificate c) {
        return new CertificateSummaryView(
                c.getId(), c.getType(), c.getVariant(), c.getStatus(),
                c.getMarksAndNos(), c.getInvoiceNumber(), c.getInvoiceDate(),
                firstLine(c.getConsigneeNotifyBlock()),
                c.getCreatedAt() != null ? TS.format(c.getCreatedAt().atZone(java.time.ZoneId.systemDefault())) : null,
                c.getGeneratedAt() != null ? TS.format(c.getGeneratedAt().atZone(java.time.ZoneId.systemDefault())) : null,
                c.getType() == CertificateType.QC && c.getVariant() == CertificateVariant.COPROCAFE
        );
    }

    private String firstLine(String block) {
        if (block == null || block.isBlank()) return null;
        return block.split("\\r?\\n")[0].trim();
    }
}
