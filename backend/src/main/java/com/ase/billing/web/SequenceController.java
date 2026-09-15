package com.ase.billing.web;

import com.ase.billing.service.InvoiceDeletionService;
import com.ase.billing.service.SequenceAdminService;
import com.ase.billing.service.SequenceAdminService.SequenceState;
import com.ase.billing.web.dto.Dtos.SequenceJumpRequest;
import com.ase.billing.web.dto.Dtos.SequenceView;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * The next invoice number, and moving it forward.
 *
 * Shown before a bill is created so the operator can see what number it will
 * take, and can skip ahead when the paper books ran on without the system.
 */
@RestController
@RequestMapping("/api/invoice-sequence")
public class SequenceController {

    private final SequenceAdminService sequences;
    private final InvoiceDeletionService deletion;

    public SequenceController(SequenceAdminService sequences, InvoiceDeletionService deletion) {
        this.sequences = sequences;
        this.deletion = deletion;
    }

    /**
     * Whether a specific number — typically one just freed by a deletion, or a
     * gap in the middle of the run — is free to issue. Does not move anything;
     * only {@code POST}/{@code PUT} the invoice itself actually reserves it.
     */
    @GetMapping("/available")
    public boolean available(@RequestParam Long customerId,
                             @RequestParam String financialYear,
                             @RequestParam int runningNumber) {
        return deletion.isNumberAvailable(customerId, financialYear, runningNumber);
    }

    @GetMapping
    public SequenceView peek(@RequestParam Long customerId,
                             @RequestParam(required = false, defaultValue = "CNF") String category,
                             @RequestParam(required = false)
                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return toView(sequences.peek(customerId, category, date == null ? LocalDate.now() : date));
    }

    /** Moves the next number forward. Going backwards is refused. */
    @PutMapping
    public SequenceView jump(@Valid @RequestBody SequenceJumpRequest req) {
        return toView(sequences.jumpTo(req.customerId(), req.date(), req.nextNumber(),
                req.categoryCode() == null ? "CNF" : req.categoryCode()));
    }

    private SequenceView toView(SequenceState s) {
        return new SequenceView(s.customerCode(), s.financialYear(),
                s.nextNumber(), s.highestUsed(), s.previewNumber());
    }
}
