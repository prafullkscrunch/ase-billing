package com.ase.billing.web;

import com.ase.billing.domain.Invoice;
import com.ase.billing.domain.enums.InvoiceStatus;
import com.ase.billing.repo.InvoiceRepository;
import com.ase.billing.web.dto.Dtos.CategoryTotal;
import com.ase.billing.web.dto.Dtos.DashboardView;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy");

    private final InvoiceRepository invoices;

    public DashboardController(InvoiceRepository invoices) {
        this.invoices = invoices;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public DashboardView summary(
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {

        YearMonth ym = month == null ? YearMonth.now() : month;
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        List<Invoice> rows = invoices.findFinalizedBetween(InvoiceStatus.FINALIZED, from, to);

        Map<String, List<Invoice>> grouped = rows.stream()
                .collect(Collectors.groupingBy(i -> i.getCategory().getCode()));

        List<CategoryTotal> byCategory = grouped.entrySet().stream()
                .map(e -> new CategoryTotal(e.getKey(), e.getValue().size(),
                        e.getValue().stream().map(Invoice::getGrandTotal)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)))
                .sorted(Comparator.comparing(CategoryTotal::grandTotal).reversed())
                .toList();

        BigDecimal taxable = rows.stream().map(Invoice::getTaxableAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal gst = rows.stream()
                .map(i -> i.getCgstAmount().add(i.getSgstAmount()).add(i.getIgstAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal billed = rows.stream().map(Invoice::getGrandTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new DashboardView(
                from.format(MONTH), rows.size(), billed, taxable, gst, byCategory,
                invoices.countByStatus(InvoiceStatus.DRAFT));
    }
}
