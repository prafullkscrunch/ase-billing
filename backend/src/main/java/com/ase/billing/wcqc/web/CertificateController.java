package com.ase.billing.wcqc.web;

import com.ase.billing.wcqc.domain.Certificate;
import com.ase.billing.wcqc.domain.enums.CertificateType;
import com.ase.billing.wcqc.service.CertificateComboService;
import com.ase.billing.wcqc.service.CertificateGenerationService;
import com.ase.billing.wcqc.service.CertificateHistoryService;
import com.ase.billing.wcqc.service.CertificateService;
import com.ase.billing.wcqc.service.InvoiceUploadService;
import com.ase.billing.wcqc.web.dto.CertificateDtos.CertificateFieldsRequest;
import com.ase.billing.wcqc.web.dto.CertificateDtos.CertificateSummaryView;
import com.ase.billing.wcqc.web.dto.CertificateDtos.CertificateView;
import com.ase.billing.wcqc.web.dto.CertificateDtos.ComboCertificateRequest;
import com.ase.billing.wcqc.web.dto.CertificateDtos.ComboCertificateView;
import com.ase.billing.wcqc.web.dto.CertificateDtos.ExtractedInvoiceData;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Every route here lives under /api/wcqc — entirely separate from
 * /api/invoices, /api/shipments etc. This controller never calls into
 * com.ase.billing.* and no billing controller calls into this package.
 */
@RestController
@RequestMapping("/api/wcqc")
public class CertificateController {

    private final InvoiceUploadService uploadService;
    private final CertificateService certificateService;
    private final CertificateHistoryService historyService;
    private final CertificateGenerationService generationService;
    private final CertificateComboService comboService;
    private final CertificateMapper mapper;

    public CertificateController(InvoiceUploadService uploadService,
                                  CertificateService certificateService,
                                  CertificateHistoryService historyService,
                                  CertificateGenerationService generationService,
                                  CertificateComboService comboService,
                                  CertificateMapper mapper) {
        this.uploadService = uploadService;
        this.certificateService = certificateService;
        this.historyService = historyService;
        this.generationService = generationService;
        this.comboService = comboService;
        this.mapper = mapper;
    }

    /** Reads an uploaded invoice (PDF or Excel) and returns whatever fields could be found. Nothing is saved here. */
    @PostMapping("/upload")
    public ExtractedInvoiceData upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Choose an invoice file to upload.");
        }
        return uploadService.extract(file);
    }

    @PostMapping("/certificates")
    public CertificateView create(@Valid @RequestBody CertificateFieldsRequest req) {
        return mapper.toView(certificateService.create(req));
    }

    /** Creates a WC and a QC certificate together from one shared field set — the combo workflow. */
    @PostMapping("/certificates/combo")
    public ComboCertificateView createCombo(@Valid @RequestBody ComboCertificateRequest req) {
        Certificate[] pair = comboService.create(req);
        return new ComboCertificateView(mapper.toView(pair[0]), mapper.toView(pair[1]));
    }

    @PutMapping("/certificates/{id}")
    public CertificateView update(@PathVariable Long id, @Valid @RequestBody CertificateFieldsRequest req) {
        return mapper.toView(certificateService.update(id, req));
    }

    @GetMapping("/certificates/{id}")
    public CertificateView get(@PathVariable Long id) {
        return mapper.toView(certificateService.get(id));
    }

    @DeleteMapping("/certificates/{id}")
    public void delete(@PathVariable Long id) {
        certificateService.delete(id);
    }

    /** Certificate History. type/q are both optional. */
    @GetMapping("/certificates")
    public List<CertificateSummaryView> list(@RequestParam(required = false) CertificateType type,
                                              @RequestParam(required = false) String q) {
        return historyService.list(type, q);
    }

    @PostMapping("/certificates/{id}/regenerate")
    public CertificateView regenerate(@PathVariable Long id) throws IOException {
        // "Regenerate" means: re-run the export from what is currently
        // saved, refreshing generatedAt/By.
        generationService.excel(id);
        return mapper.toView(certificateService.get(id));
    }

    @GetMapping("/certificates/{id}/excel")
    public ResponseEntity<byte[]> excel(@PathVariable Long id) throws IOException {
        Certificate c = certificateService.get(id);
        byte[] bytes = generationService.excel(id);
        return fileResponse(bytes, fileName(c, "xlsx"),
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @GetMapping("/certificates/{id}/word")
    public ResponseEntity<byte[]> word(@PathVariable Long id) throws IOException {
        Certificate c = certificateService.get(id);
        byte[] bytes = generationService.word(id);
        return fileResponse(bytes, fileName(c, "docx"),
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
    }

    private String fileName(Certificate c, String ext) {
        String mark = c.getMarksAndNos() != null ? c.getMarksAndNos().replaceAll("[^A-Za-z0-9-]", "_") : String.valueOf(c.getId());
        return "%s-%s.%s".formatted(c.getType(), mark, ext);
    }

    private ResponseEntity<byte[]> fileResponse(byte[] bytes, String fileName, MediaType type) {
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(bytes);
    }
}
