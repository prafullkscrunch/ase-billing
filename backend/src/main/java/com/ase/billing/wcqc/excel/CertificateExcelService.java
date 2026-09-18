package com.ase.billing.wcqc.excel;

import com.ase.billing.wcqc.domain.Certificate;
import com.ase.billing.wcqc.domain.enums.CertificateType;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * Fills the ACTUAL historical WC / QC workbook (wcqc-templates/wc-master.xlsx,
 * qc-master.xlsx — real ASE files, copied in as-is) with this certificate's
 * values, rather than building a new sheet from scratch. That is what keeps
 * the Excel export pixel-identical to what ASE has always produced: same
 * fonts, same column widths, same borders, same fixed wording, because it
 * IS that file, with only the cells below marked as dynamic overwritten.
 *
 * <p>Row 1-20 (exporter block, consignee/notify, pre-carriage/ports/
 * destination) sit at fixed coordinates in every sample checked — those are
 * simple single-cell writes. Rows 22 downward held one particular historical
 * shipment's free-typed lines; those rows are cleared first and then
 * rewritten from this certificate's line blocks, leaving row 45 on (the
 * fixed certification sentence and signature block) completely untouched.
 */
@Service
public class CertificateExcelService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // 0-based row indexes matching the coordinates confirmed across the
    // sampled WC and QC master files.
    private static final int R_CONSIGNEE_START = 8;   // row 9
    private static final int R_CONSIGNEE_END = 13;     // row 14
    private static final int R_PRECARRIAGE = 16;       // row 17 (label on row 16)
    private static final int R_PLACE_OF_RECEIPT = 16;  // row 17, col C
    private static final int R_COUNTRY_ORIGIN = 15;    // row 16, col F
    private static final int R_PORT_OF_LOADING = 18;   // row 19, col A
    private static final int R_PORT_OF_DISCHARGE = 18; // row 19, col C
    private static final int R_FINAL_DEST = 17;        // row 18, col F
    private static final int R_COUNTRY_FINAL_DEST = 19;// row 20, col F
    private static final int R_BODY_START = 21;        // row 22
    // The QUANTITY/IN MT figure sits well below its own header in the real
    // templates — checked against a genuine historical QC sample: header at
    // row 22-23, figure at row 28, bold and centred. Writing it immediately
    // below the header (as an earlier version did) doesn't match what ASE
    // actually produces.
    private static final int R_QUANTITY_VALUE = R_BODY_START + 6; // row 28
    private static final int R_BODY_CLEAR_END = 36;    // clear through row 37, leave 38 on
    private static final int R_DECLARATION = 37;       // row 38
    private static final int R_SUMMARY_LABEL = 38;     // row 39
    private static final int R_SUMMARY_LINES_START = 39; // row 40
    private static final int R_SUMMARY_LINES_MAX_END = 43; // row 44 — row 45 (index 44) is the fixed quote, never touched

    private static final int C_A = 0, C_B = 1, C_C = 2, C_E = 4, C_F = 5;

    public byte[] generate(Certificate c) throws IOException {
        String templatePath = c.getType() == CertificateType.WC
                ? "wcqc-templates/wc-master.xlsx" : "wcqc-templates/qc-master.xlsx";

        try (InputStream in = new ClassPathResource(templatePath).getInputStream();
             Workbook wb = new XSSFWorkbook(in);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.getSheetAt(0);
            int descriptionCol = c.getType() == CertificateType.WC ? C_B : C_C;

            writeConsignee(sheet, c);
            setCell(sheet, R_PRECARRIAGE, C_A, c.getPreCarriageBy());
            setCell(sheet, R_PLACE_OF_RECEIPT, C_C, c.getPlaceOfReceipt());
            setCell(sheet, R_COUNTRY_ORIGIN, C_F, c.getCountryOfOrigin());
            setCell(sheet, R_PORT_OF_LOADING, C_A, c.getPortOfLoading());
            setCell(sheet, R_PORT_OF_DISCHARGE, C_C, c.getPortOfDischarge());
            setCell(sheet, R_FINAL_DEST, C_F, c.getFinalDestination());
            setCell(sheet, R_COUNTRY_FINAL_DEST, C_F, c.getCountryOfFinalDestination());

            clearBody(sheet, descriptionCol);
            writeColumn(sheet, R_BODY_START, C_A, linesOf(c.getMarksColumnText()));
            writeColumn(sheet, R_BODY_START, descriptionCol, linesOf(c.getDescriptionColumnText()));
            writeQuantityValue(sheet, wb, linesOf(c.getQuantityColumnText())); // leaves the fixed "QUANTITY"/"IN MT" header on rows 22-23

            setCell(sheet, R_DECLARATION, C_A, c.getDeclarationLine());
            writeSummary(sheet, c);

            wb.write(out);
            return out.toByteArray();
        }
    }

    /** The quantity figure ("38.40MT"), positioned below its own header and styled bold + centred — checked against a real historical QC sample rather than assumed. */
    private void writeQuantityValue(Sheet sheet, Workbook wb, List<String> lines) {
        if (lines.isEmpty()) return;
        CellStyle style = wb.createCellStyle();
        Font bold = wb.createFont();
        bold.setBold(true);
        style.setFont(bold);
        style.setAlignment(HorizontalAlignment.CENTER);
        for (int i = 0; i < lines.size(); i++) {
            Row row = sheet.getRow(R_QUANTITY_VALUE + i);
            if (row == null) row = sheet.createRow(R_QUANTITY_VALUE + i);
            Cell cell = row.getCell(C_E);
            if (cell == null) cell = row.createCell(C_E);
            cell.setCellValue(lines.get(i));
            cell.setCellStyle(style);
        }
    }

    /**
     * WC and QC differ here: WC's real templates put the "WEIGHT:-" label
     * alone on its own row, with the weight-breakdown sentences on separate
     * rows below it. QC's real templates put the label and its (usually
     * one-line) quality statement together in ONE cell — "QUALITY:UNWASHED
     * COFFEE ROBUSTA, FROM INDIA, CHERRY AA" — confirmed on a real sample,
     * not the label-then-separate-line shape this used to write for both
     * types alike.
     */
    private void writeSummary(Sheet sheet, Certificate c) {
        String label = c.getSummaryStatementLabel() != null ? c.getSummaryStatementLabel() : "";
        List<String> lines = linesOf(c.getSummaryStatementLines());

        if (c.getType() == CertificateType.QC) {
            String firstLine = lines.isEmpty() ? "" : lines.get(0);
            setCell(sheet, R_SUMMARY_LABEL, C_A, label + firstLine);
            if (lines.size() > 1) {
                writeColumnCapped(sheet, R_SUMMARY_LINES_START, C_A, lines.subList(1, lines.size()),
                        R_SUMMARY_LINES_MAX_END);
            }
        } else {
            setCell(sheet, R_SUMMARY_LABEL, C_A, label);
            writeColumnCapped(sheet, R_SUMMARY_LINES_START, C_A, lines, R_SUMMARY_LINES_MAX_END);
        }
    }

    private void writeConsignee(Sheet sheet, Certificate c) {
        List<String> lines = linesOf(c.getConsigneeNotifyBlock());
        for (int r = R_CONSIGNEE_START; r <= R_CONSIGNEE_END; r++) {
            int idx = r - R_CONSIGNEE_START;
            setCell(sheet, r, C_A, idx < lines.size() ? lines.get(idx) : "");
        }
    }

    private void clearBody(Sheet sheet, int descriptionCol) {
        for (int r = R_BODY_START; r <= R_BODY_CLEAR_END; r++) {
            setCell(sheet, r, C_A, "");
            setCell(sheet, r, descriptionCol, "");
            if (r >= R_BODY_START + 2) setCell(sheet, r, C_E, "");
        }
    }

    private void writeColumn(Sheet sheet, int startRow, int col, List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            setCell(sheet, startRow + i, col, lines.get(i));
        }
    }

    private void writeColumnCapped(Sheet sheet, int startRow, int col, List<String> lines, int maxEndRowInclusive) {
        int max = maxEndRowInclusive - startRow + 1;
        for (int i = 0; i < lines.size() && i < max; i++) {
            setCell(sheet, startRow + i, col, lines.get(i));
        }
    }

    private void setCell(Sheet sheet, int rowIdx, int colIdx, String value) {
        Row row = sheet.getRow(rowIdx);
        if (row == null) row = sheet.createRow(rowIdx);
        Cell cell = row.getCell(colIdx);
        if (cell == null) cell = row.createCell(colIdx);
        cell.setCellValue(value != null ? value : "");
    }

    private List<String> linesOf(String block) {
        if (block == null || block.isBlank()) return List.of();
        return Arrays.stream(block.split("\\r?\\n")).toList();
    }
}
