package com.ase.billing.excel;

import com.ase.billing.domain.Invoice;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates the GST sales detail workbook from invoices in the database.
 *
 * Deliberately NOT a copy of the supplied hc_august_SOA_2026.xlsx. That file has
 * no merged cells, no bold, no fills, column widths on two columns only, ragged
 * borders, General number format nearly throughout, and dates stored as text in
 * one block and as real dates in another. Reproducing it faithfully would mean
 * reproducing all of that. This keeps its column order and block shape and adds
 * real formatting.
 *
 * Column order follows the HCEPL block of the supplied file, including its quirks:
 * CGST sits before SGST, and the category lands in column I with column H empty.
 * Those are kept so the output drops into ASE's existing routine unchanged.
 */
@Service
public class SoaExcelService {

    private static final DateTimeFormatter MONTH_TITLE = DateTimeFormatter.ofPattern("MMMM yyyy");

    // A=date B=invoice C=total D=taxable E=CGST F=SGST G=mark H=(spacer) I=category
    private static final int C_DATE = 0, C_INVOICE = 1, C_TOTAL = 2, C_TAXABLE = 3,
                             C_CGST = 4, C_SGST = 5, C_MARK = 6, C_CATEGORY = 8;

    public byte[] generate(String customerCode, String customerName, String customerGstin,
                           LocalDate from, LocalDate to,
                           List<Invoice> invoices) throws IOException {

        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet("SOA");
            Styles st = new Styles(wb);
            int r = 0;

            // Title row: 'ASE GST SALES DETAILS FOR THE MONTH OF AUGUST 2026-HCEPL'
            Row title = sheet.createRow(r++);
            cell(title, 0, st.title).setCellValue(
                    "ASE GST SALES DETAILS FOR THE MONTH OF %s-%s"
                            .formatted(periodTitle(from, to), titleSuffix(customerCode, customerName)));
            cell(title, C_SGST, st.title).setCellValue(customerGstin);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, C_CGST));

            // Header row
            Row head = sheet.createRow(r++);
            cell(head, C_DATE, st.header).setCellValue("DATE");
            cell(head, C_INVOICE, st.header).setCellValue("INVOICE NO.");
            cell(head, C_TOTAL, st.header).setCellValue("Total");
            cell(head, C_TAXABLE, st.header).setCellValue("Taxable");
            cell(head, C_CGST, st.header).setCellValue("CGST");
            cell(head, C_SGST, st.header).setCellValue("SGST");
            cell(head, C_MARK, st.header).setCellValue("HC MARK NO");
            cell(head, C_CATEGORY, st.header).setCellValue("CNF/DC/T/S");

            int firstDataRow = r;
            BigDecimal sumTotal = BigDecimal.ZERO, sumTaxable = BigDecimal.ZERO,
                       sumCgst = BigDecimal.ZERO, sumSgst = BigDecimal.ZERO;

            for (Invoice inv : invoices) {
                Row row = sheet.createRow(r++);

                cell(row, C_DATE, st.date).setCellValue(
                        java.sql.Date.valueOf(inv.getInvoiceDate()));
                cell(row, C_INVOICE, st.text).setCellValue(inv.getInvoiceNumber());
                cell(row, C_TOTAL, st.whole).setCellValue(inv.getGrandTotal().doubleValue());
                cell(row, C_TAXABLE, st.money).setCellValue(inv.getTaxableAmount().doubleValue());
                cell(row, C_CGST, st.money).setCellValue(inv.getCgstAmount().doubleValue());
                cell(row, C_SGST, st.money).setCellValue(inv.getSgstAmount().doubleValue());

                Cell mark = cell(row, C_MARK, st.text);
                if (inv.getShipment() != null) {
                    mark.setCellValue(inv.getShipment().getSoaMarkNo());
                }
                cell(row, C_CATEGORY, st.text).setCellValue(inv.getCategory().getCode());

                sumTotal   = sumTotal.add(inv.getGrandTotal());
                sumTaxable = sumTaxable.add(inv.getTaxableAmount());
                sumCgst    = sumCgst.add(inv.getCgstAmount());
                sumSgst    = sumSgst.add(inv.getSgstAmount());
            }

            // Totals row. The supplied workbook has none for HCEPL; this adds one,
            // as a live SUM so ASE can see it recalculate.
            if (!invoices.isEmpty()) {
                Row totals = sheet.createRow(r);
                cell(totals, C_INVOICE, st.totalLabel).setCellValue("TOTAL");
                sumFormula(totals, C_TOTAL,   st.totalWhole, "C", firstDataRow, r);
                sumFormula(totals, C_TAXABLE, st.totalMoney, "D", firstDataRow, r);
                sumFormula(totals, C_CGST,    st.totalMoney, "E", firstDataRow, r);
                sumFormula(totals, C_SGST,    st.totalMoney, "F", firstDataRow, r);
            }

            sheet.setColumnWidth(C_DATE, 12 * 256);
            sheet.setColumnWidth(C_INVOICE, 26 * 256);
            sheet.setColumnWidth(C_TOTAL, 13 * 256);
            sheet.setColumnWidth(C_TAXABLE, 13 * 256);
            sheet.setColumnWidth(C_CGST, 12 * 256);
            sheet.setColumnWidth(C_SGST, 12 * 256);
            sheet.setColumnWidth(C_MARK, 13 * 256);
            sheet.setColumnWidth(7, 3 * 256);
            sheet.setColumnWidth(C_CATEGORY, 13 * 256);
            sheet.createFreezePane(0, 2);

            wb.write(out);
            return out.toByteArray();
        }
    }

    private void sumFormula(Row row, int col, CellStyle style, String letter, int first, int last) {
        Cell c = cell(row, col, style);
        c.setCellFormula("SUM(%s%d:%s%d)".formatted(letter, first + 1, letter, last));
    }

    private Cell cell(Row row, int idx, CellStyle style) {
        Cell c = row.createCell(idx);
        c.setCellStyle(style);
        return c;
    }

    /**
     * The supplied workbook titles the HCEPL block "…-HCEPL". That abbreviation
     * is the customer's own short code, so take it from the customer record
     * rather than trying to derive it from the full legal name.
     */
    private String titleSuffix(String customerCode, String customerName) {
        if (customerCode != null && !customerCode.isBlank()) {
            return customerCode.toUpperCase();
        }
        return customerName == null ? "" : customerName.toUpperCase();
    }

    /** A range inside one month titles as that month; a wider range shows both ends. */
    private String periodTitle(LocalDate from, LocalDate to) {
        String start = from.format(MONTH_TITLE).toUpperCase();
        String end = to.format(MONTH_TITLE).toUpperCase();
        return start.equals(end) ? start : start + " TO " + end;
    }

    /** All cell styles in one place so the look is consistent and easy to change. */
    private static final class Styles {
        final CellStyle title, header, text, date, money, whole, totalLabel, totalMoney, totalWhole;

        Styles(Workbook wb) {
            DataFormat fmt = wb.createDataFormat();

            Font boldFont = wb.createFont();
            boldFont.setBold(true);

            title = wb.createCellStyle();
            title.setFont(boldFont);

            header = base(wb);
            header.setFont(boldFont);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            text = base(wb);

            date = base(wb);
            date.setDataFormat(fmt.getFormat("dd-mm-yyyy"));

            money = base(wb);
            money.setDataFormat(fmt.getFormat("#,##0.00"));

            whole = base(wb);
            whole.setDataFormat(fmt.getFormat("#,##0"));

            totalLabel = base(wb);
            totalLabel.setFont(boldFont);

            totalMoney = base(wb);
            totalMoney.setFont(boldFont);
            totalMoney.setDataFormat(fmt.getFormat("#,##0.00"));
            totalMoney.setBorderTop(BorderStyle.DOUBLE);

            totalWhole = base(wb);
            totalWhole.setFont(boldFont);
            totalWhole.setDataFormat(fmt.getFormat("#,##0"));
            totalWhole.setBorderTop(BorderStyle.DOUBLE);
        }

        private static CellStyle base(Workbook wb) {
            CellStyle s = wb.createCellStyle();
            s.setBorderTop(BorderStyle.THIN);
            s.setBorderBottom(BorderStyle.THIN);
            s.setBorderLeft(BorderStyle.THIN);
            s.setBorderRight(BorderStyle.THIN);
            return s;
        }
    }
}
