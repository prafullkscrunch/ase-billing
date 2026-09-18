package com.ase.billing.wcqc.service;

import com.ase.billing.wcqc.web.dto.CertificateDtos.ExtractedInvoiceData;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads an uploaded invoice (PDF or Excel) and pulls out whatever this module
 * needs to pre-fill a WC/QC certificate. Read-only, one-shot, no dependency
 * on com.ase.billing.* — it does not create or touch a billing invoice, and
 * the uploaded file is never stored past this request.
 *
 * <h2>Why this is label-scanning, not fixed-coordinate, extraction</h2>
 * The first version of this class read fixed cell coordinates, based on the
 * one invoice layout studied while building the module. A real second
 * invoice (HC_358-INV-_SUCAFINA-JULY_2026.xlsx) turned up with an extra
 * "Vessel Name & Voy no." row that isn't in the first sample, shifting every
 * row below it down by one — which silently broke every fixed-coordinate
 * read for Port of Discharge, Final Destination and Country of Final
 * Destination on that file. Coordinates are not a safe assumption across
 * ASE's own invoice variants.
 *
 * <p>Every field below is instead found by searching for its own printed
 * label, wherever it happens to sit, and then checking — in order — the
 * text after the label on the same cell (label and value share one cell,
 * e.g. "Place of Receipt : SOMWARPET"), the cell directly below it (label
 * and value stacked in the same column, e.g. "Port of Discharge" over
 * "Istanbul, Turkey, (TRIST), PORT"), then the next non-blank cell to the
 * right on the same row (label and value side by side, e.g. "Pre-Carriage
 * by :" in column A with "ROAD" in column B). This is what makes the same
 * code work whether a given invoice's columns/rows have shifted or not.
 */
@Service
public class InvoiceUploadService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceUploadService.class);

    // "14/1850/2026/377" or a range "14/1850/2026/292-295"
    private static final Pattern MARKS_PATTERN =
            Pattern.compile("\\b\\d{2}/\\d{3,4}/\\d{4}/\\d{1,4}(-\\d{1,4})?\\b");

    // "ES/HC/2027/171 DT.11/07/2026", "ES/HC/2026/303. DT:27/02/2026",
    // "ES/HC/2027/202 , DT:25/08/2026" (comma instead of period before the
    // label — invoice 400), or "ES/HC/2026/199. DATE.25.08.2026" (invoice
    // 397 spells out "DATE" instead of "DT" and separates the date with
    // dots instead of slashes)
    private static final Pattern INVOICE_NO_DATE_PATTERN = Pattern.compile(
            "(ES/HC/\\d{4}/\\d{2,4})[.,\\s]*(?:DT|DATE)[.:]?\\s*(\\d{2}[./]\\d{2}[./]\\d{4})",
            Pattern.CASE_INSENSITIVE);

    private static final DateTimeFormatter INVOICE_DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Route/header labels this scans for. Each maps to "the text starts with
    // this, optionally followed by ':'" — see findLabelledValue().
    private static final Pattern L_PRE_CARRIAGE = Pattern.compile("(?i)^Pre[- ]?Carriage\\s+by\\s*:?\\s*");
    private static final Pattern L_PLACE_OF_RECEIPT = Pattern.compile("(?i)^Place\\s+of\\s+Receipt\\s*:?\\s*");
    private static final Pattern L_COUNTRY_ORIGIN = Pattern.compile("(?i)^Country\\s+of\\s+Origin(?:\\s+of\\s+Goods)?\\s*:?\\s*");
    private static final Pattern L_PORT_OF_LOADING = Pattern.compile("(?i)^Port\\s+of\\s+Loading\\s*:?\\s*");
    private static final Pattern L_PORT_OF_DISCHARGE = Pattern.compile("(?i)^Port\\s+of\\s+Discharge\\s*:?\\s*");
    private static final Pattern L_FINAL_DEST = Pattern.compile("(?i)^Final\\s+Destination\\s*:?\\s*");
    private static final Pattern L_COUNTRY_FINAL_DEST = Pattern.compile("(?i)^Country\\s+of\\s+Final\\s+Destination\\s*:?\\s*");
    // "CONSIGNEE" is correctly spelled in some invoices, transposed to
    // "CONSGINEE" in others (confirmed real, not a one-off — invoice 417 and
    // 402 both have it), and sometimes combined with NOTIFY using "&" or
    // "/" as the separator ("CONSGINEE& NOTIFY :", "CONSGINEE /NOTIFY:-",
    // "CONSGINEE/NOTIFY :" — all three confirmed real) when consignee and
    // notify are the same party. This pattern accepts both spellings and
    // either separator.
    private static final Pattern L_CONSIGNEE = Pattern.compile("(?i)^CONS[GI]{2}NEE(\\s*[&/]\\s*NOTIFY)?\\s*:?-?\\s*");
    // "NOTIFY :", "NOTIFY-1:" / "NOTIFY 2:" (invoice 417 numbers them as a
    // suffix), or "1ST NOTIFY:" / "2ND NOTIFY:" (invoice 369 numbers them
    // as an ordinal prefix instead) — this matches whichever comes first in
    // the sheet; a second/third notify party is a known gap, see
    // InvoiceUploadService's class doc. The trailing "-?" (after the colon,
    // not just before the number) matters: invoice 397 writes "NOTIFY 1:-
    // KAREN TRADE..." with the hyphen AFTER the colon, which an earlier
    // version left in place as a stray leading "-" on the extracted block.
    private static final Pattern L_NOTIFY = Pattern.compile("(?i)^(\\d(ST|ND|RD|TH)\\s+)?NOTIFY\\s*-?\\s*\\d*\\s*:?-?\\s*");
    private static final Pattern L_BUYER = Pattern.compile("(?i)^Buyer\\s*(?:\\(.*?\\))?\\s*:?\\s*");
    private static final Pattern L_MARKS_HEADER = Pattern.compile("(?i)Marks\\s*&\\s*Nos");
    private static final Pattern L_STOP_BODY = Pattern.compile("(?i)^(Declaration|AMOUNT IN WORDS|Total Qty)\\b");

    // A cell that is itself another label — used so "check the cell below/
    // right" never mistakes the NEXT field's label for THIS field's value.
    private static final Set<String> LABEL_STARTS = Set.of(
            "pre carriage by", "pre-carriage by", "place of receipt", "country of origin",
            "port of loading", "port of discharge", "final destination", "country of final destination",
            "marks & nos", "description of goods", "quantity", "vessel name", "terms of delivery",
            "buyer", "consignee", "notify", "beneficiary", "account no", "bank name", "swift code",
            "intermediary", "gstin", "pan", "iec", "eori");

    // Address lines that are metadata, not part of a postal address — dropped
    // from a formatted party block, though still used to know where the
    // block ends while scanning.
    private static final Pattern METADATA_LINE = Pattern.compile("(?i)^(EORI|GSTIN|PAN|IEC|TAX ID|VAT)\\b");

    private static final Pattern MT_QUANTITY = Pattern.compile("(?i)\\d+\\.?\\d*\\s*MT\\b");
    private static final Pattern TOTAL_NETT = Pattern.compile("(?i)Total\\s+Nett\\s+Wt\\D*?(\\d+(?:\\.\\d+)?)\\s*KGS");
    private static final Pattern TOTAL_GROSS = Pattern.compile("(?i)Total\\s+Gross\\s+Wt\\D*?(\\d+(?:\\.\\d+)?)\\s*KGS");
    // "NET WEIGHT:60.00KGS" (jute/big-bag invoices), "NETT WT:21600.00KGS"
    // (bulk invoices), or embedded mid-sentence with no "WT"/"WEIGHT" word
    // between "NETT" and the number at all — "PACKED IN JUTE BAG WG
    // NETT60.00KGS" (invoice 397) — and sometimes without the "KGS" suffix
    // either (invoice 402's own "NETT WT:21600.00" line has none, confirmed
    // by reading the actual file). Matched per-line, excluding lines
    // starting with "Total" (see firstDecimalPerUnit) — matching this
    // anywhere in the whole sheet text blob previously also matched inside
    // "Total Nett Wt :...", silently treating the SHIPMENT'S total as if
    // it were the PER-BAG figure.
    private static final Pattern NET_WEIGHT_EACH = Pattern.compile("(?i)NETT?\\s*(?:W(?:EIGH)?T)?\\s*:?\\s*(\\d+(?:\\.\\d+)?)\\s*(?:KGS)?");
    private static final Pattern GROSS_WEIGHT_EACH = Pattern.compile("(?i)GROSS\\s*(?:W(?:EIGH)?T)?\\s*:?\\s*(\\d+(?:\\.\\d+)?)\\s*(?:KGS)?");
    private static final Pattern BAG_RANGE = Pattern.compile("^1/(\\d+)$");
    private static final Pattern BULK = Pattern.compile("(?i)\\bBULK\\b");
    private static final Pattern BIG_BAG_OR_PALLET = Pattern.compile("(?i)\\bBIG\\s*BAGS?\\b|\\bPALLETS?\\b");
    private static final Pattern BULK_BAG_COUNT = Pattern.compile("(?i)(\\d+)\\s*BULK\\s*BAGS?\\b");
    private static final Pattern BIG_BAG_COUNT = Pattern.compile("(?i)PACKING\\s+IN\\s*(\\d+)\\s*BIG\\s*BAGS?\\b");
    private static final Pattern PALLETS_LINE = Pattern.compile("(?i)Total\\s+Wt\\s+of\\s+(\\d+)\\s+Pallets?\\D*?(\\d+(?:\\.\\d+)?)\\s*KGS");
    // Lines in the marks column that are administrative rather than
    // grade/origin description — excluded when composing the QC quality
    // statement suggestion, which should read like "INDIA / ROBUSTA COFFEE
    // CHERRY AA," not "FRONT SIDE: LOGO ICO:14/1850/2026/358 ...". Extended
    // after checking the pattern against several packaging types (bulk,
    // big-bag) which print their own weight/reference lines in this column
    // too — REF NO., CROP YEAR, CONTAINER NO., and NETT/GROSS WT lines are
    // never quality-relevant regardless of packaging.
    private static final Pattern ADMIN_MARKS_LINE = Pattern.compile(
            "(?i)^(FRONT SIDE|BACK SIDE|LOGO|PRODUCTION|EXPIRY|ICO|NET(T)?\\s*W(EIGH)?T|GROSS\\s*WT|"
            + "BL NO|CROP YEAR|CONTAINER NO|REF NO|HANGAL COFFEE)\\b");

    public ExtractedInvoiceData extract(MultipartFile file) throws IOException {
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename() : "uploaded invoice";
        String lower = name.toLowerCase();

        log.info("Reading uploaded invoice file={}", name);
        ExtractedInvoiceData result;
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            result = extractFromExcel(name, file);
        } else if (lower.endsWith(".pdf")) {
            result = extractFromPdf(name, file);
        } else {
            throw new IllegalArgumentException("Only PDF or Excel invoices can be uploaded (got: " + name + ")");
        }
        if (!result.missingFields().isEmpty()) {
            log.warn("Invoice file={} extraction missing fields: {}", name, result.missingFields());
        }
        return result;
    }

    // ---- Excel -----------------------------------------------------------------

    private ExtractedInvoiceData extractFromExcel(String fileName, MultipartFile file) throws IOException {
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);

            String invoiceNumber = null;
            LocalDate invoiceDate = null;
            String marks = null;
            for (Row row : sheet) {
                for (Cell cell : row) {
                    String v = cellText(cell);
                    if (v == null || v.isBlank()) continue;
                    if (invoiceNumber == null) {
                        Matcher m = INVOICE_NO_DATE_PATTERN.matcher(v);
                        if (m.find()) {
                            invoiceNumber = m.group(1);
                            invoiceDate = parseDate(m.group(2));
                        }
                    }
                    if (marks == null) {
                        Matcher m = MARKS_PATTERN.matcher(v);
                        if (m.find()) marks = m.group();
                    }
                }
            }

            String preCarriageBy = findLabelledValue(sheet, L_PRE_CARRIAGE);
            String placeOfReceipt = findLabelledValue(sheet, L_PLACE_OF_RECEIPT);
            String countryOfOrigin = findLabelledValue(sheet, L_COUNTRY_ORIGIN);
            String portOfLoading = findLabelledValue(sheet, L_PORT_OF_LOADING);
            String portOfDischarge = findLabelledValue(sheet, L_PORT_OF_DISCHARGE);
            String finalDestination = findLabelledValue(sheet, L_FINAL_DEST);
            String countryOfFinalDest = findLabelledValue(sheet, L_COUNTRY_FINAL_DEST);
            // Final Destination is now taken verbatim from the invoice's own
            // "Final Destination" field, whatever it says — including when
            // it happens to match Port of Discharge word for word. An
            // earlier version shortened it to just the city in that case,
            // based on one historical certificate; that was overridden on
            // direct instruction after review, since the invoice's own
            // stated value is the correct source of truth here, not an
            // inference from a single past example. The operator can still
            // shorten it by hand for any certificate where that's wanted.

            List<String> notifyLines = extractParty(sheet, L_NOTIFY, null);
            List<String> consigneeLines = extractParty(sheet, L_CONSIGNEE, L_NOTIFY);
            List<String> buyerLinesRaw = extractParty(sheet, L_BUYER, null);
            List<String> buyerLines = buyerLinesRaw.stream()
                    .filter(l -> !METADATA_LINE.matcher(l).lookingAt()).toList();

            String notifyBlock = formatAddressBlock(notifyLines);
            String consigneeBlock = formatAddressBlock(consigneeLines);
            // Primary default for the certificate's single CONSIGNEE/NOTIFY
            // field: a distinct NOTIFY party when the invoice states one;
            // otherwise the invoice's own CONSIGNEE block. That "otherwise"
            // matters for the "CONSGINEE& NOTIFY :" combined-label case
            // (invoice 402) — there IS no separate notify line to find
            // there because consignee and notify are explicitly the same
            // party, so falling back to null (rather than to the consignee
            // block) would wrongly mark the certificate's main address
            // field as missing when a perfectly good address was right
            // there under the combined label.
            String primaryBlock = notifyBlock != null ? notifyBlock : consigneeBlock;
            // Prefer the Buyer block over the raw Consignee line as the
            // ALTERNATE option when both exist and describe the same party:
            // the Buyer block is typically the fuller, cleanly-separated
            // address, while the Consignee line is often a compressed "To
            // order of ..." form sharing the same company. If Consignee was
            // already used as the primary (no separate Notify found), it's
            // not offered again as its own alternate.
            List<String> alternateSource = !buyerLines.isEmpty() ? buyerLines
                    : (notifyBlock != null ? consigneeLines : List.of());
            String alternateBlock = formatAddressBlock(alternateSource);
            String alternateLabel = alternateSource.isEmpty() ? null : alternateSource.get(0);

            BodyBlock body = extractBody(sheet);
            String summarySuggestion = suggestWeightSummary(sheet);
            String qualitySuggestion = suggestQualityStatement(body);

            return build(fileName, marks, invoiceNumber, invoiceDate,
                    primaryBlock, alternateBlock, alternateLabel,
                    preCarriageBy, placeOfReceipt, countryOfOrigin, portOfLoading,
                    portOfDischarge, finalDestination, countryOfFinalDest,
                    body == null ? null : body.marks(), body == null ? null : body.description(),
                    body == null ? null : body.quantity(), summarySuggestion, qualitySuggestion);
        }
    }

    /**
     * Finds a labelled value wherever it sits: same cell after the label,
     * the cell below (same column), or the next non-blank cell to the right
     * (same row) — in that order, skipping anything that looks like it's
     * actually the NEXT field's label rather than this field's value.
     */
    private String findLabelledValue(Sheet sheet, Pattern labelPrefix) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                String text = cellText(cell);
                if (text == null) continue;
                Matcher m = labelPrefix.matcher(text);
                if (!m.lookingAt()) continue;

                String inline = text.substring(m.end()).replaceFirst("^[:\\s]+", "").trim();
                if (!inline.isBlank() && !looksLikeLabel(inline)) return inline;

                String below = cellAt(sheet, cell.getRowIndex() + 1, cell.getColumnIndex());
                if (below != null && !below.isBlank() && !looksLikeLabel(below)) return below.trim();

                for (int c = cell.getColumnIndex() + 1; c <= cell.getColumnIndex() + 5; c++) {
                    String right = cellAt(sheet, cell.getRowIndex(), c);
                    if (right == null || right.isBlank()) continue;
                    return looksLikeLabel(right) ? null : right.trim();
                }
                return null;
            }
        }
        return null;
    }

    private boolean looksLikeLabel(String text) {
        String norm = text.toLowerCase().replaceAll("[:.]+$", "").trim();
        // Strip a leading ordinal ("2ND notify..." -> "notify...") so a
        // second/third notify party's own label is still recognised as a
        // label boundary — without this, scanning the first notify party's
        // continuation ran straight through "2ND NOTIFY:TOUTON SAS" and
        // absorbed it (and everything after it) as if it were more of the
        // first notify's address. Confirmed against invoice 369.
        norm = norm.replaceFirst("^\\d(st|nd|rd|th)\\s+", "");
        return LABEL_STARTS.stream().anyMatch(norm::startsWith);
    }

    /**
     * Collects a party's address lines: the text after the label on its own
     * cell, any further non-blank cells on the SAME row after it (only on
     * the anchor row — some invoices continue the first line into a
     * different column, e.g. "P.O. Box 5425, 1211 Geneva 11," sitting in
     * column C on the same row as the "CONSIGNEE :" cell in column A), then
     * subsequent rows in the SAME column as the label until a blank cell,
     * another label, or {@code stopAt} (the label that starts the NEXT
     * block, so NOTIFY's scan doesn't run into CONSIGNEE and vice versa).
     */
    private List<String> extractParty(Sheet sheet, Pattern labelPrefix, Pattern stopAt) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                String text = cellText(cell);
                if (text == null) continue;
                Matcher m = labelPrefix.matcher(text);
                if (!m.lookingAt()) continue;

                int anchorRow = cell.getRowIndex();
                int anchorCol = cell.getColumnIndex();
                List<String> lines = new ArrayList<>();

                String inline = text.substring(m.end()).replaceFirst("^[:\\s]+", "").trim();
                if (!inline.isBlank()) lines.add(inline);

                // Narrower than findLabelledValue's right-scan: a real
                // same-row continuation (e.g. "P.O. Box 5425..." sharing
                // CONSIGNEE's row in a later column) sits immediately next
                // to the anchor. Scanning further right risks picking up an
                // unrelated block that happens to share the same row (this
                // invoice's NOTIFY row also carries the exporter's own
                // STATE/GSTIN cells a few columns over) — confirmed by
                // testing against HC_358-INV-_SUCAFINA-JULY_2026.xlsx, where
                // a wider scan pulled "STATE"/"KARNATAKA" into the Notify
                // block.
                for (int c = anchorCol + 1; c <= anchorCol + 2; c++) {
                    String v = cellAt(sheet, anchorRow, c);
                    if (v == null || v.isBlank()) continue;
                    if (looksLikeLabel(v)) break;
                    lines.add(v.trim());
                }

                for (int r = anchorRow + 1; r <= anchorRow + 10; r++) {
                    String v = cellAt(sheet, r, anchorCol);
                    if (v == null || v.isBlank()) break;
                    if (looksLikeLabel(v)) break;
                    if (stopAt != null && stopAt.matcher(v).lookingAt()) break;
                    lines.add(v.trim());
                    // Some invoices continue a party's address into the
                    // immediate next column on a row that ISN'T the anchor
                    // row — e.g. invoice 397's "NOTIFY 1" block has its
                    // actual street address in column B on the row right
                    // after the ID code in column A. Checking only the
                    // anchor row (as the same-row scan above does) missed
                    // this. Restricted to exactly one column over, same as
                    // the anchor-row scan, for the same reason: SUCAFINA's
                    // NOTIFY block has unrelated STATE/GSTIN cells sitting
                    // further right (columns D/E) that must NOT be picked
                    // up — confirmed those columns are blank at this
                    // one-column distance for that invoice, so this is safe.
                    String neighbor = cellAt(sheet, r, anchorCol + 1);
                    if (neighbor != null && !neighbor.isBlank() && !looksLikeLabel(neighbor)) {
                        lines.add(neighbor.trim());
                    }
                }
                return lines;
            }
        }
        return List.of();
    }

    /**
     * Joins the party's lines into one block. If the last line has a comma
     * followed by a short, non-numeric trailing word (the country, in every
     * sample checked — e.g. "1211, GENEVA, SWITZERLAND"), that trailing word
     * is split onto its own line, matching the format ASE's certificates
     * actually use. Never applied to a line without a clear short trailing
     * segment, so it doesn't misfire on things like a unit or suite number.
     */
    private String formatAddressBlock(List<String> lines) {
        if (lines.isEmpty()) return null;
        List<String> out = new ArrayList<>(lines);
        String last = out.get(out.size() - 1);
        int idx = last.lastIndexOf(',');
        if (idx > 0 && idx < last.length() - 1) {
            String tail = last.substring(idx + 1).trim();
            if (!tail.isBlank() && tail.length() <= 20 && !tail.matches(".*\\d.*")) {
                out.set(out.size() - 1, last.substring(0, idx + 1).trim());
                out.add(tail);
            }
        }
        return String.join("\n", out);
    }

    private boolean looksLikeHeaderContinuationFragment(String s) {
        String t = s.trim();
        // Short, only letters/spaces/basic punctuation, and entirely
        // lowercase — a real content line always has at least a capital
        // letter, a digit, or a quote mark ("640 BAGS...", "Container
        // No." itself, "14/1850/2026/397"); a wrapped header fragment like
        // "of pkges" has none of those.
        return t.length() <= 20 && t.matches("[a-z .&]+");
    }

    /**
     * The marks-side equivalent of {@link #looksLikeHeaderContinuationFragment}
     * — that one requires all-lowercase text, which "Container No." isn't.
     * What distinguishes it from real marks-column content (grade lines,
     * the shipment mark, bag counts) is that it's short AND ends with "No."
     * — a real content line essentially never does (checked against every
     * sample: "BL NO.CMD0180331" ends with a reference number, not "No."
     * itself).
     */
    private boolean looksLikeMarksHeaderContinuation(String s) {
        String t = s.trim();
        return t.length() <= 20 && t.matches("(?i).*\\bNo\\.\\s*");
    }

    private record BodyBlock(String marks, String description, String quantity) {}

    /**
     * The "Marks & Nos" / "Description of Goods" body: column A holds the
     * marks/origin/grade lines, column B (one to the right, regardless of
     * which column the "Description of Goods" HEADER itself sits in — it's
     * often printed a column further right than its own content) holds the
     * description lines. Runs until two consecutive blank rows or a
     * recognisable end-of-body marker (Declaration, totals line).
     */
    private BodyBlock extractBody(Sheet sheet) {
        int headerRow = -1, headerCol = -1;
        search:
        for (Row row : sheet) {
            for (Cell cell : row) {
                String t = cellText(cell);
                if (t != null && L_MARKS_HEADER.matcher(t).find()) {
                    headerRow = row.getRowNum();
                    headerCol = cell.getColumnIndex();
                    break search;
                }
            }
        }
        if (headerRow < 0) return null;

        // The "Marks & Nos.../Description of Goods..." header sometimes
        // wraps onto a second row before real content starts — e.g. "Marks
        // & Nos/ No. " on one row, "Container No." directly below it (with
        // "Description of Goods and kind" / "of pkges" wrapping the same
        // way one column over) — confirmed on invoice 369. Reading content
        // from headerRow+1 unconditionally picked up "Container No." as if
        // it were the first marks-column data line.
        //
        // The fix must be conservative: an earlier version treated ANY
        // non-blank text in the "Description of Goods" header's own column
        // as "still wrapping" and kept advancing past it — which silently
        // ate the first real content row on invoice 397, where that same
        // column is where the real description content starts too (no
        // offset into the next column over, unlike invoice 369). So this
        // only skips a row when its text in that column looks like an
        // actual short label fragment — short, letters/spaces/punctuation
        // only, no digits, no uppercase (a real content line always has
        // at least a capital letter, a digit, or a quote mark; "of pkges"
        // has none of those) — and only the ONE row directly below the
        // header, never further, since every confirmed real case wraps by
        // exactly one line.
        int descHeaderCol = -1;
        for (int c = headerCol + 1; c <= headerCol + 5; c++) {
            String v = cellAt(sheet, headerRow, c);
            if (v != null && v.toLowerCase().contains("description of goods")) { descHeaderCol = c; break; }
        }
        int contentStartRow = headerRow + 1;
        {
            String descCandidate = descHeaderCol >= 0 ? cellAt(sheet, contentStartRow, descHeaderCol) : null;
            String marksCandidate = cellAt(sheet, contentStartRow, headerCol);
            // Two independent signals, because the two sides don't always
            // wrap together: invoices 369/405 wrap BOTH ("Marks & Nos/ No."
            // + "Container No." alongside "...and kind" + "of pkges"), but
            // 403/416/395-396/423-424 wrap ONLY the marks side — their
            // "Description of Goods" header never wraps at all, so relying
            // solely on the description-side signal (as an earlier version
            // did) missed this group of invoices entirely and left their
            // description column empty.
            boolean descSideWraps = descCandidate != null && looksLikeHeaderContinuationFragment(descCandidate);
            boolean marksSideWraps = marksCandidate != null && looksLikeMarksHeaderContinuation(marksCandidate);
            if (descSideWraps || marksSideWraps) {
                contentStartRow++;
            }
        }

        // Description content normally sits one column right of the marks
        // column (confirmed on every WC/QC master template and most
        // invoices) — but invoice 397 puts it directly under its own
        // "Description of Goods" header instead, with the column in
        // between left entirely empty. Probing the first content row tells
        // which layout this invoice uses, rather than assuming.
        int descContentCol = headerCol + 1;
        String probe = cellAt(sheet, contentStartRow, descContentCol);
        if ((probe == null || probe.isBlank()) && descHeaderCol >= 0) {
            String altProbe = cellAt(sheet, contentStartRow, descHeaderCol);
            if (altProbe != null && !altProbe.isBlank()) descContentCol = descHeaderCol;
        }

        List<String> marks = new ArrayList<>();
        List<String> desc = new ArrayList<>();
        int blankStreak = 0;
        for (int r = contentStartRow; r <= contentStartRow + 25; r++) {
            String a = cellAt(sheet, r, headerCol);
            String b = cellAt(sheet, r, descContentCol);
            if (a != null && L_STOP_BODY.matcher(a.trim()).find()) break;
            boolean blank = (a == null || a.isBlank()) && (b == null || b.isBlank());
            if (blank) {
                blankStreak++;
                if (blankStreak >= 2 && (!marks.isEmpty() || !desc.isEmpty())) break;
                continue;
            }
            blankStreak = 0;
            if (a != null && !a.isBlank()) marks.add(a.trim());
            if (b != null && !b.isBlank()) desc.add(b.trim());
        }

        // The quantity column sometimes holds a plain Excel number (e.g.
        // 38.4) rather than text — the "MT" is only in the cell's display
        // format, which isn't something a data-only read exposes as text.
        // Text matching alone then skipped straight past the real figure
        // and picked up a later "TOTAL QTY:38.4MT" fallback line instead
        // (confirmed on invoice 397). A bare positive number in this
        // column, within the body area, is treated as the MT figure
        // directly rather than requiring the unit to be spelled out.
        String quantity = null;
        for (int r = headerRow; r <= headerRow + 25; r++) {
            Row row = sheet.getRow(r);
            Cell cell = row == null ? null : row.getCell(headerCol + 4);
            String e = cellText(cell);
            if (e != null && MT_QUANTITY.matcher(e).find()) { quantity = e.trim(); break; }
            if (cell != null && cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC
                    && cell.getNumericCellValue() > 0) {
                quantity = plain(BigDecimal.valueOf(cell.getNumericCellValue())) + "MT";
                break;
            }
        }

        return new BodyBlock(
                marks.isEmpty() ? null : String.join("\n", marks),
                desc.isEmpty() ? null : String.join("\n", desc),
                quantity);
    }

    /**
     * Composes the weight-breakdown sentences (WC only) purely from figures
     * the invoice itself states — never a guessed number. Which template
     * applies depends on packaging, confirmed against real certificates for
     * each type (two independent samples per type, cross-checked against
     * each other, not assumed from one file):
     *
     * <ul>
     *   <li><b>Jute bags</b> (the default — e.g. 320/640 bag shipments):
     *       three lines, "...jute bag...".</li>
     *   <li><b>Bulk bags</b> (marks/description mention "BULK"): three
     *       lines, same shape as jute but "...BULK BAGS...". An earlier
     *       version of this method treated "mentions BULK" as a reason to
     *       skip the suggestion entirely — that was backwards. Bulk
     *       shipments get a breakdown too, just different wording.</li>
     *   <li><b>Big bags on pallets</b> (marks/description mention "BIG BAG"
     *       and/or "PALLET"): FOUR lines — gross, nett, a pallets line, then
     *       tare — because the invoice gives pallet count/weight as its own
     *       figure, not derivable from bag count. Confirmed against two
     *       independent real certificates with matching wording; a third
     *       real certificate for a different big-bag shipment (369) turned
     *       out to have plain jute-bag wording left over from a previous
     *       certificate — a copy-paste error in that one historical
     *       document, not a valid third pattern, so it was not used as
     *       evidence here.</li>
     * </ul>
     *
     * <p>Per-bag tare and gross are always arithmetic (total gross minus
     * total nett, divided by bag count) — never assumed. Returns null
     * (leaving it for manual entry) whenever the figures a template needs
     * can't all be found, since a partially-filled sentence is worse than
     * none.
     */
    private String suggestWeightSummary(Sheet sheet) {
        StringBuilder all = new StringBuilder();
        for (Row row : sheet) for (Cell cell : row) {
            String t = cellText(cell);
            if (t != null) all.append(t).append('\n');
        }
        String text = all.toString();

        boolean isBulk = BULK.matcher(text).find();
        boolean isBigBagPallet = !isBulk && BIG_BAG_OR_PALLET.matcher(text).find();

        Integer bagCount = findBagCount(text, isBulk, isBigBagPallet);
        if (bagCount == null || bagCount == 0) return null;

        BigDecimal perBagNett = firstDecimalPerUnit(NET_WEIGHT_EACH, text);
        BigDecimal perBagGrossRaw = firstDecimalPerUnit(GROSS_WEIGHT_EACH, text);
        if (perBagNett == null) return null;

        // A shipment of just one or a few units (typical for bulk bags)
        // often has no separate "Total Nett/Gross Wt" line at all, since
        // the per-unit figure already IS the total — confirmed against
        // invoice 417 (1 bulk bag). Fall back to per-unit × bag count in
        // that case, never to a guess.
        BigDecimal totalNett = firstDecimal(TOTAL_NETT, text);
        if (totalNett == null) totalNett = perBagNett.multiply(BigDecimal.valueOf(bagCount));
        BigDecimal totalGross = firstDecimal(TOTAL_GROSS, text);
        if (totalGross == null && perBagGrossRaw != null) {
            totalGross = perBagGrossRaw.multiply(BigDecimal.valueOf(bagCount));
        }
        if (totalGross == null) return null;

        BigDecimal totalTare = totalGross.subtract(totalNett);
        BigDecimal tarePerBag = totalTare.divide(BigDecimal.valueOf(bagCount), 2, RoundingMode.HALF_UP);
        BigDecimal perBagGross = perBagNett.add(tarePerBag);

        if (isBigBagPallet) {
            Matcher palletsM = PALLETS_LINE.matcher(text);
            if (!palletsM.find()) return null; // pallet count/weight is stated by the invoice, not derivable — don't guess it
            String palletCount = palletsM.group(1);
            String palletsWeight = palletsM.group(2);
            return String.join("\n",
                    "Total Gross Weight of  %d BIG BAGS COFFEE each Wg.%skgs:=%sKGS"
                            .formatted(bagCount, plain(perBagGross), plain(totalGross)),
                    "Total NETT  Weight of  %d BIG BAGS COFFEE each Wg.%skgs:=%sKGS"
                            .formatted(bagCount, plain(perBagNett), plain(totalNett)),
                    "TOTAL  Weight OF %s PALLETS =%sKGS".formatted(palletCount, palletsWeight),
                    "Total Tare Wight of %d bags each Wg.%s Kgs:=%skgs"
                            .formatted(bagCount, plain(tarePerBag), plain(totalTare)));
        }

        String bagWord = isBulk ? "BULK BAGS" : "jute bag";
        String bagWordNett = isBulk ? "BULK BAGS" : "jute  bag"; // the jute-bag Nett line has always printed a double space here, confirmed across two samples
        return String.join("\n",
                "Total Gross Weight of %d %s each Wg.%skgs:=%sKGS"
                        .formatted(bagCount, bagWord, plain(perBagGross), plain(totalGross)),
                "Total Nett Weight of %d %s each Wg %skgs:=%sKGS"
                        .formatted(bagCount, bagWordNett, plain(perBagNett), plain(totalNett)),
                "Total Tare Wight of %d bags each Wg.%sKgs:=%skgs"
                        .formatted(bagCount, plain(tarePerBag), plain(totalTare)));
    }

    /** Like firstDecimal, but only considers lines that don't start with "Total" — see NET_WEIGHT_EACH's doc for why that distinction matters. */
    private BigDecimal firstDecimalPerUnit(Pattern p, String text) {
        for (String line : text.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.toLowerCase().startsWith("total")) continue;
            Matcher m = p.matcher(trimmed);
            if (m.find()) return new BigDecimal(m.group(1));
        }
        return null;
    }

    /**
     * Bag count for the weight-breakdown suggestion. Tries, in order: the
     * "1/N" bag-range marker (works for jute and some bulk/big-bag
     * invoices), then a packaging-specific phrase ("N BULK BAG(S)" or
     * "PACKING IN N BIG BAGS") for invoices that don't print a bag-range
     * marker at all — confirmed necessary because the Touton 369 invoice
     * (big bags on pallets) has no "1/N" line anywhere.
     */
    private Integer findBagCount(String text, boolean isBulk, boolean isBigBagPallet) {
        for (String line : text.split("\\r?\\n")) {
            Matcher m = BAG_RANGE.matcher(line.trim());
            if (m.matches()) return Integer.parseInt(m.group(1));
        }
        if (isBulk) {
            Matcher m = BULK_BAG_COUNT.matcher(text);
            if (m.find()) return Integer.parseInt(m.group(1));
        }
        if (isBigBagPallet) {
            Matcher m = BIG_BAG_COUNT.matcher(text);
            if (m.find()) return Integer.parseInt(m.group(1));
        }
        return null;
    }

    private BigDecimal firstDecimal(Pattern p, String text) {
        Matcher m = p.matcher(text);
        return m.find() ? new BigDecimal(m.group(1)) : null;
    }

    /**
     * Composes a QC quality-statement suggestion from the marks column's own
     * grade/origin lines (e.g. "INDIA / ROBUSTA COFFEE" / "CHERRY AA,"),
     * dropping administrative lines (FRONT SIDE, ICO number, net weight,
     * bag range, BL number). Reuses text the invoice already states rather
     * than inventing wording — this is why QC's quality statement was
     * printing blank: nothing was ever suggesting one, so it stayed empty
     * unless someone typed it in by hand.
     */
    private String suggestQualityStatement(BodyBlock body) {
        if (body == null || body.marks() == null) return null;
        List<String> kept = new ArrayList<>();
        for (String line : body.marks().split("\\r?\\n")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            if (ADMIN_MARKS_LINE.matcher(t).find()) continue;
            if (BAG_RANGE.matcher(t).matches()) continue;
            // Some invoices lead the marks column with the bare shipment
            // mark ("14/1850/2026/408") instead of a grade/origin line —
            // confirmed across several big-bag samples. Not quality text.
            if (MARKS_PATTERN.matcher(t).matches()) continue;
            kept.add(t);
        }
        return kept.isEmpty() ? null : "QUALITY:" + String.join(" ", kept);
    }

    private String plain(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String cellAt(Sheet sheet, int rowIdx, int colIdx) {
        Row row = sheet.getRow(rowIdx);
        if (row == null) return null;
        return cellText(row.getCell(colIdx));
    }

    private String cellText(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf(cell.getNumericCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> null;
        };
    }

    // ---- PDF ---------------------------------------------------------------------

    private ExtractedInvoiceData extractFromPdf(String fileName, MultipartFile file) throws IOException {
        StringBuilder all = new StringBuilder();
        try (PdfReader reader = new PdfReader(file.getInputStream())) {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            for (int p = 1; p <= reader.getNumberOfPages(); p++) {
                all.append(extractor.getTextFromPage(p)).append('\n');
            }
        }
        String text = all.toString();

        String invoiceNumber = null;
        LocalDate invoiceDate = null;
        Matcher m = INVOICE_NO_DATE_PATTERN.matcher(text);
        if (m.find()) {
            invoiceNumber = m.group(1);
            invoiceDate = parseDate(m.group(2));
        }

        String marks = null;
        Matcher marksM = MARKS_PATTERN.matcher(text);
        if (marksM.find()) marks = marksM.group();

        String notifyBlock = blockAfterLabel(text, "NOTIFY");
        String consigneeBlock = blockAfterLabel(text, "CONSIGNEE");

        // Ports/destination and the marks/description body have no reliable
        // position in free-flowing PDF text, so those are left for manual
        // entry rather than guessed — the Excel path (which most of ASE's
        // own invoices are) covers them properly.
        return build(fileName, marks, invoiceNumber, invoiceDate, notifyBlock, consigneeBlock,
                consigneeBlock == null ? null : "Consignee",
                null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private String blockAfterLabel(String text, String label) {
        String[] lines = text.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].toUpperCase().contains(label)) {
                List<String> block = new ArrayList<>();
                String first = lines[i].replaceFirst("(?i).*" + label + "\\s*:?\\s*", "").trim();
                if (!first.isBlank()) block.add(first);
                for (int j = i + 1; j < lines.length && j < i + 6; j++) {
                    String l = lines[j].trim();
                    if (l.isEmpty() || looksLikeLabel(l)) break;
                    block.add(l);
                }
                return block.isEmpty() ? null : String.join("\n", block);
            }
        }
        return null;
    }

    private LocalDate parseDate(String rawDate) {
        try {
            // Normalise dot-separated dates ("25.08.2026", invoice 397) to
            // the slash-separated format INVOICE_DATE_FMT expects.
            return LocalDate.parse(rawDate.replace('.', '/'), INVOICE_DATE_FMT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private ExtractedInvoiceData build(String fileName, String marks, String invoiceNumber,
                                        LocalDate invoiceDate, String consigneeNotifyBlock,
                                        String alternatePartyBlock, String alternatePartyLabel,
                                        String preCarriageBy, String placeOfReceipt, String countryOfOrigin,
                                        String portOfLoading, String portOfDischarge, String finalDestination,
                                        String countryOfFinalDest, String marksColumnText,
                                        String descriptionColumnText, String quantityColumnText,
                                        String summaryStatementLines, String qcQualityStatementLines) {
        Set<String> missing = new LinkedHashSet<>();
        if (marks == null) missing.add("marksAndNos");
        if (invoiceNumber == null) missing.add("invoiceNumber");
        if (invoiceDate == null) missing.add("invoiceDate");
        if (consigneeNotifyBlock == null) missing.add("consigneeNotifyBlock");
        if (portOfDischarge == null) missing.add("portOfDischarge");
        if (finalDestination == null) missing.add("finalDestination");
        if (countryOfFinalDest == null) missing.add("countryOfFinalDestination");
        if (marksColumnText == null) missing.add("marksColumnText");
        if (descriptionColumnText == null) missing.add("descriptionColumnText");
        if (quantityColumnText == null) missing.add("quantityColumnText");
        if (summaryStatementLines == null) missing.add("summaryStatementLines");
        if (qcQualityStatementLines == null) missing.add("qcQualityStatementLines");

        return new ExtractedInvoiceData(fileName, marks, invoiceNumber, invoiceDate,
                consigneeNotifyBlock, alternatePartyBlock, alternatePartyLabel,
                preCarriageBy, placeOfReceipt, countryOfOrigin, portOfLoading, portOfDischarge,
                finalDestination, countryOfFinalDest, marksColumnText, descriptionColumnText,
                quantityColumnText, summaryStatementLines, qcQualityStatementLines, List.copyOf(missing));
    }
}
