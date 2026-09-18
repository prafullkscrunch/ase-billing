package com.ase.billing.wcqc.service;

import com.ase.billing.wcqc.domain.Certificate;
import com.ase.billing.wcqc.domain.enums.CertificateStatus;
import com.ase.billing.wcqc.domain.enums.CertificateType;
import com.ase.billing.wcqc.domain.enums.CertificateVariant;
import com.ase.billing.wcqc.excel.CertificateExcelService;
import com.ase.billing.wcqc.repo.CertificateRepository;
import com.ase.billing.wcqc.word.CoprocafeWordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * The one place that turns a certificate's saved fields into an exported
 * file. Every export goes through here (not straight from the controller to
 * the Excel/Word service) so that generatedAt/generatedBy/status are always
 * kept in step with what was actually produced.
 *
 * <p>There is deliberately no PDF export. An earlier version had one (a
 * hand-built OpenPDF layout, since a PDF has no template to fill the way
 * Excel does) but it was removed on request — Excel is the certificate
 * format that's actually used.
 */
@Service
public class CertificateGenerationService {

    private static final Logger log = LoggerFactory.getLogger(CertificateGenerationService.class);

    private final CertificateRepository repo;
    private final CertificateExcelService excelService;
    private final CoprocafeWordService wordService;
    private final QcCertificateService qcCertificateService;

    public CertificateGenerationService(CertificateRepository repo,
                                         CertificateExcelService excelService,
                                         CoprocafeWordService wordService,
                                         QcCertificateService qcCertificateService) {
        this.repo = repo;
        this.excelService = excelService;
        this.wordService = wordService;
        this.qcCertificateService = qcCertificateService;
    }

    @Transactional
    public byte[] excel(Long id) throws IOException {
        Certificate c = load(id);
        byte[] bytes = excelService.generate(c);
        markGenerated(c);
        return bytes;
    }

    @Transactional
    public byte[] word(Long id) throws IOException {
        Certificate c = load(id);
        if (c.getType() != CertificateType.QC || c.getVariant() != CertificateVariant.COPROCAFE) {
            throw new IllegalStateException("Word export is only available for COPROCAFE QC certificates.");
        }
        List<String> missing = qcCertificateService.missingCoprocafeFields(c);
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Required information missing — please enter manually: " + String.join(", ", missing));
        }
        byte[] bytes = wordService.generate(c);
        markGenerated(c);
        return bytes;
    }

    private Certificate load(Long id) {
        return repo.findById(id).orElseThrow(() ->
                new java.util.NoSuchElementException("No certificate with id " + id));
    }

    private void markGenerated(Certificate c) {
        c.setStatus(CertificateStatus.GENERATED);
        c.setGeneratedAt(Instant.now());
        c.setGeneratedBy(currentUsername());
        repo.save(c);
        log.info("Generated {} certificate id={} mark={} by={}",
                c.getType(), c.getId(), c.getMarksAndNos(), c.getGeneratedBy());
    }

    private String currentUsername() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}
