package com.ase.billing.web;

import com.ase.billing.domain.Invoice;
import com.ase.billing.domain.enums.InvoiceStatus;
import com.ase.billing.pdf.BatchPdfService;
import com.ase.billing.pdf.InvoicePdfService;
import com.ase.billing.repo.InvoiceRepository;
import com.ase.billing.service.FinancialYear;
import com.ase.billing.service.InvoiceDeletionService;
import com.ase.billing.service.InvoiceService;
import com.ase.billing.web.dto.Dtos.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.ase.billing.domain.Invoice;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private static final Logger log = LoggerFactory.getLogger(InvoiceController.class);

    private final InvoiceService service;
    private final InvoicePdfService pdf;
    private final BatchPdfService batchPdf;
    private final InvoiceDeletionService deletion;
    private final InvoiceRepository invoices;
    private final InvoiceMapper mapper;

    public InvoiceController(InvoiceService service, InvoicePdfService pdf,
                             BatchPdfService batchPdf, InvoiceDeletionService deletion,
                             InvoiceRepository invoices, InvoiceMapper mapper) {
        this.service = service;
        this.pdf = pdf;
        this.batchPdf = batchPdf;
        this.deletion = deletion;
        this.invoices = invoices;
        this.mapper = mapper;
    }

    @GetMapping
    public List<InvoiceSummary> list(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) String q) {
        return service.search(customerId, from, to, category, status, q)
                .stream().map(mapper::toSummary).toList();
    }

    @GetMapping("/{id}")
    public InvoiceView get(@PathVariable Long id) {
        return mapper.toView(service.load(id));
    }

    @PostMapping
    public InvoiceView create(@Valid @RequestBody InvoiceRequest req, Authentication auth) {
        return mapper.toView(service.createDraft(req, username(auth)));
    }

    /** Returns the invoice with every total recomputed. Render from the response. */
    @PutMapping("/{id}")
    public InvoiceView update(@PathVariable Long id,
                              @Valid @RequestBody InvoiceRequest req,
                              Authentication auth) {
        return mapper.toView(service.updateDraft(id, req, username(auth)));
    }

    @PostMapping("/{id}/finalize")
    public InvoiceView finalize(@PathVariable Long id, Authentication auth) {
        log.info("Finalise requested for invoice {} by {}.", id, username(auth));
        return mapper.toView(service.finalize(id, username(auth)));
    }

    @PostMapping("/{id}/cancel")
    public InvoiceView cancel(@PathVariable Long id, Authentication auth) {
        return mapper.toView(service.cancel(id, username(auth)));
    }

    @PostMapping("/{id}/duplicate")
    public InvoiceView duplicate(@PathVariable Long id,
                                 @RequestParam(required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                 Authentication auth) {
        return mapper.toView(service.duplicate(id, date, username(auth)));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        Invoice inv = service.load(id);
        byte[] bytes = pdf.render(inv);
        String filename = inv.getInvoiceNumber().replace('/', '-') + ".pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(filename).build().toString())
                .body(bytes);
    }

    /**
     * Deletes an invoice. A draft goes without ceremony; a finalised one needs a
     * reason, which is written to the audit log before the row disappears.
     */
    @DeleteMapping("/{id}")
    public DeleteResult delete(@PathVariable Long id,
                               @RequestParam(required = false) String reason,
                               Authentication auth) {
        log.info("Delete requested for invoice {} by {} (reason: {}).",
                id, username(auth), reason == null ? "(none)" : reason);
        var done = deletion.delete(id, reason, username(auth));
        String note = done.numberFreed()
                ? "Number %d is free again and will be issued next.".formatted(done.freedNumber())
                : "A later invoice exists, so this leaves a gap. Set the number explicitly to reuse it.";
        return new DeleteResult(done.invoiceNumber(), done.wasStatus().name(),
                done.numberFreed(), done.freedNumber(), note, done.rateNotes());
    }

    /** Approves a reviewed batch. One refusal does not stop the rest. */
    @PostMapping("/bulk-finalize")
    public BulkFinalizeResult bulkFinalize(@Valid @RequestBody BulkFinalizeRequest req,
                                           Authentication auth) {
        var result = service.finalizeAll(req.ids(), username(auth));
        List<BulkProblem> refused = result.refused().stream()
                .map(f -> new BulkProblem(f.id(), f.invoiceNumber(), f.message(), f.problems()))
                .toList();
        return new BulkFinalizeResult(result.approved(), refused,
                result.approved().size(), refused.size());
    }

    /**
     * Many bills in one PDF, in number order — the shape of ASE's own
     * "437-480 ASE HC bills.pdf". Filter by status for a draft review pack, or by
     * number range to reproduce an issued book.
     */
    @GetMapping("/batch-pdf")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> batchPdf(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(required = false) Integer fromNumber,
            @RequestParam(required = false) Integer toNumber,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate onDate) {

        String fy = onDate == null ? null : FinancialYear.of(onDate);
        List<Invoice> found = invoices.findForBatch(customerId, status, fromNumber, toNumber, fy);
        byte[] bytes = batchPdf.merge(found);

        String filename = status == InvoiceStatus.DRAFT
                ? "ASE_drafts_%d_bills.pdf".formatted(found.size())
                : "ASE_bills_%s-%s.pdf".formatted(
                    found.get(0).getRunningNumber(),
                    found.get(found.size() - 1).getRunningNumber());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(filename).build().toString())
                .body(bytes);
    }

    private String username(Authentication auth) {
        return auth == null ? "system" : auth.getName();
    }
}
