package com.ase.billing.web;

import com.ase.billing.domain.Customer;
import com.ase.billing.domain.Invoice;
import com.ase.billing.domain.enums.InvoiceStatus;
import com.ase.billing.excel.SoaExcelService;
import com.ase.billing.exception.NotFoundException;
import com.ase.billing.repo.CustomerRepository;
import com.ase.billing.repo.InvoiceRepository;
import com.ase.billing.web.dto.Dtos.InvoiceSummary;
import com.ase.billing.web.dto.Dtos.SoaPreview;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * GST sales details, generated from the database.
 *
 * Only FINALIZED invoices appear. A draft is an unfinished bill and must never
 * reach a GST return; a cancelled number is burnt and reports nothing.
 */
@RestController
@RequestMapping("/api/soa")
public class SoaController {

    private final InvoiceRepository invoices;
    private final CustomerRepository customers;
    private final SoaExcelService excel;
    private final InvoiceMapper mapper;

    public SoaController(InvoiceRepository invoices, CustomerRepository customers,
                         SoaExcelService excel, InvoiceMapper mapper) {
        this.invoices = invoices;
        this.customers = customers;
        this.excel = excel;
        this.mapper = mapper;
    }

    @GetMapping("/preview")
    @Transactional(readOnly = true)
    public SoaPreview preview(@RequestParam Long customerId,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Customer customer = customer(customerId);
        List<Invoice> rows = rows(customerId, from, to);

        return new SoaPreview(
                customer.getName(), customer.getGstin(), from, to,
                rows.stream().map(mapper::toSummary).toList(),
                sum(rows, Invoice::getGrandTotal),
                sum(rows, Invoice::getTaxableAmount),
                sum(rows, Invoice::getCgstAmount),
                sum(rows, Invoice::getSgstAmount),
                sum(rows, Invoice::getIgstAmount));
    }

    @GetMapping("/excel")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> excel(@RequestParam Long customerId,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to)
            throws IOException {
        Customer customer = customer(customerId);
        byte[] bytes = excel.generate(customer.getCode(), customer.getName(), customer.getGstin(),
                                      from, to, rows(customerId, from, to));

        String filename = "ASE_SOA_%s_%s_%s.xlsx".formatted(customer.getCode(), from, to);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(bytes);
    }

    private List<Invoice> rows(Long customerId, LocalDate from, LocalDate to) {
        return invoices.findForSoa(customerId, from, to, InvoiceStatus.FINALIZED);
    }

    private Customer customer(Long id) {
        return customers.findById(id).orElseThrow(() -> new NotFoundException("Customer", id));
    }

    private BigDecimal sum(List<Invoice> rows, java.util.function.Function<Invoice, BigDecimal> f) {
        return rows.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
