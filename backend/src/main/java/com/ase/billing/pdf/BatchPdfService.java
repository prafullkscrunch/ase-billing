package com.ase.billing.pdf;

import com.ase.billing.domain.Invoice;
import com.ase.billing.exception.ValidationException;
import com.lowagie.text.Document;
import com.lowagie.text.pdf.PdfCopy;
import com.lowagie.text.pdf.PdfImportedPage;
import com.lowagie.text.pdf.PdfReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Merges many invoices into one PDF, the way ASE's own
 * "437-480 ASE HC bills.pdf" is put together: every bill in number order, one
 * after another, in a single file to print or send.
 *
 * Two uses, and they are different jobs:
 *   - every DRAFT, to read through on paper before approving a batch
 *   - a range of FINALISED numbers, to reproduce an issued book
 */
@Service
public class BatchPdfService {

    /** Beyond this a single request starts to time out and the file gets unwieldy. */
    private static final int MAX_INVOICES = 200;

    private final InvoicePdfService invoicePdf;

    public BatchPdfService(InvoicePdfService invoicePdf) {
        this.invoicePdf = invoicePdf;
    }

    private static void closeQuietly(Document doc, PdfCopy copy) {
        try {
            if (doc.isOpen()) doc.close();
        } catch (Exception ignored) {
            // nothing useful to do; the real cause is already being thrown
        }
        try {
            copy.close();
        } catch (Exception ignored) {
            // same
        }
    }

    /**
     * Runs in a transaction because each invoice's customer, category, shipment
     * and items are lazy. Without one the entities arrive detached and the first
     * render throws, which the merge then reports as an empty document.
     */
    @Transactional(readOnly = true)
    public byte[] merge(List<Invoice> invoices) {
        if (invoices.isEmpty()) {
            throw new ValidationException("No invoices match that selection.");
        }
        if (invoices.size() > MAX_INVOICES) {
            throw new ValidationException(
                    "That selection covers %d invoices. Narrow it to %d or fewer — a wider range is better split into a few files."
                            .formatted(invoices.size(), MAX_INVOICES));
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document merged = new Document();
        PdfCopy copy;

        try {
            copy = new PdfCopy(merged, out);
            merged.open();
        } catch (Exception e) {
            throw new IllegalStateException("Could not start the combined PDF: " + e.getMessage(), e);
        }

        try {
            for (Invoice inv : invoices) {
                byte[] single = invoicePdf.render(inv);
                PdfReader reader = new PdfReader(single);
                for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                    PdfImportedPage imported = copy.getImportedPage(reader, page);
                    copy.addPage(imported);
                }
                copy.freeReader(reader);
                reader.close();
            }
        } catch (Exception e) {
            // Close quietly on the failure path. Document.close() on a document
            // with no pages throws its own exception, and letting that escape
            // from a finally block replaces the real cause with a useless
            // "The document has no pages" — which is exactly what happened here.
            closeQuietly(merged, copy);
            throw new IllegalStateException(
                    "Could not build the combined PDF: " + e.getMessage(), e);
        }

        merged.close();
        copy.close();
        return out.toByteArray();
    }
}
