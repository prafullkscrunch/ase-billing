package com.ase.billing.web.dto;

import com.ase.billing.domain.enums.CalculationType;
import com.ase.billing.domain.enums.InvoiceStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The wire contract. Entities never cross the controller boundary: a client that
 * can post an Invoice can post a grandTotal, and the whole point of this system
 * is that the server decides what the totals are.
 *
 * Every response carrying money is server-computed. Every request omits money
 * except the raw inputs (rate, quantity, a manual amount).
 */
public final class Dtos {

    private Dtos() {}

    // ---------- masters -------------------------------------------------------

    public record CustomerView(Long id, String code, String name, String gstin,
                               String city, String gstTreatment) {}

    public record CategoryView(Long id, String code, String description,
                               String pdfLayout, boolean requiresShipment) {}

    public record ServiceView(Long id, String categoryCode, String name,
                              String printTemplate, CalculationType calculationType,
                              String defaultUnit, BigDecimal currentRate,
                              boolean standard, BigDecimal defaultQuantity,
                              BigDecimal baseAmount) {}

    public record RouteView(Long id, String name, String origin, String destination,
                            String printTemplate, BigDecimal ratePerContainer) {}

    public record ConsigneeView(Long id, String name, String country) {}

    public record ConsigneeRequest(
            @NotBlank(message = "The consignee needs a name")
            @Size(max = 160, message = "That name is too long")
            String name,
            String country) {}

    // ---------- shipment ------------------------------------------------------

    public record ShipmentView(Long id, String hcInvoiceNumber, String icoMarkFull,
                               String soaMarkNo, Integer containerCount,
                               String containerSize, BigDecimal teu,
                               String containerNotation) {}

    public record ShipmentRequest(
            @NotBlank(message = "HC invoice number is required")
            String hcInvoiceNumber,

            @NotBlank(message = "ICO mark number is required")
            String icoMarkFull,

            @NotNull @Min(value = 1, message = "A shipment needs at least one container")
            Integer containerCount,

            @Pattern(regexp = "20|40", message = "Container size must be 20 or 40")
            String containerSize,

            String destinationCountry,
            String notes) {}

    /**
     * The customer's most recent shipment, so the operator's next HC invoice
     * and ICO mark numbers can be suggested from it rather than retyped.
     * Both fields are null when the customer has no shipment yet.
     */
    public record LastShipmentView(String hcInvoiceNumber, String icoMarkFull) {}

    /**
     * Corrects the container count/size on a shipment whose bill(s) are still
     * drafts. Every PER_TEU line on those drafts is re-priced against the new
     * TEU and the invoice totals recomputed.
     */
    public record ShipmentContainerRequest(
            @NotNull @Min(value = 1, message = "A shipment needs at least one container")
            Integer containerCount,

            @Pattern(regexp = "20|40", message = "Container size must be 20 or 40")
            String containerSize) {}

    // ---------- invoice items -------------------------------------------------

    public record ItemView(Long id, Integer sequenceNo, Long serviceId, Long routeId,
                           String printedDescription, BigDecimal quantity, String unit,
                           BigDecimal rate, BigDecimal baseAmount, Integer days, BigDecimal teu,
                           CalculationType calculationType, BigDecimal amount,
                           boolean taxable, String notes, List<String> subLines) {}

    // ---------- invoice numbering --------------------------------------------

    public record SequenceView(String customerCode, String financialYear,
                               int nextNumber, Integer highestUsed, String previewNumber) {}

    public record SequenceJumpRequest(
            @NotNull(message = "Choose a customer") Long customerId,
            @NotNull(message = "A date is needed to work out the financial year")
            LocalDate date,
            @NotNull @Min(value = 1, message = "An invoice number has to be 1 or more")
            Integer nextNumber,
            String categoryCode) {}

    public record ItemRequest(
            Long serviceId,
            Long routeId,

            @NotBlank(message = "Every line needs a description")
            @Size(max = 400, message = "Description is too long for the bill")
            String printedDescription,

            BigDecimal quantity,
            String unit,

            @DecimalMin(value = "0.00", message = "Rate cannot be negative")
            BigDecimal rate,

            /** Fixed component billed once alongside quantity/TEU x rate. Defaults to zero. */
            BigDecimal baseAmount,

            @Min(value = 0, message = "Days cannot be negative")
            Integer days,

            BigDecimal teu,

            @NotNull(message = "Calculation type is required")
            CalculationType calculationType,

            /** Only read when calculationType is MANUAL; otherwise the server computes it. */
            BigDecimal amount,

            Boolean taxable,
            String notes,
            List<String> subLines,

            /** Keep this rate as the new default for the service from this date on. */
            Boolean updateMasterRate) {}

    // ---------- invoice -------------------------------------------------------

    public record InvoiceView(
            Long id, String invoiceNumber, Integer runningNumber, LocalDate invoiceDate,
            String financialYear, Long customerId, String customerName,
            String categoryCode, String categoryDescription,
            ShipmentView shipment, String hsnCode, String headerNote,
            BigDecimal subtotal, BigDecimal taxableAmount,
            BigDecimal cgstRate, BigDecimal cgstAmount,
            BigDecimal sgstRate, BigDecimal sgstAmount,
            BigDecimal igstRate, BigDecimal igstAmount,
            BigDecimal grandTotal,
            String postTaxAdjustmentLabel, BigDecimal postTaxAdjustmentAmount,
            BigDecimal netPayable, String amountInWords,
            InvoiceStatus status, boolean editable,
            List<ItemView> items, AnnexureView annexure) {}

    /** The trimmed shape the list screen and the SOA preview use. */
    public record InvoiceSummary(
            Long id, String invoiceNumber, LocalDate invoiceDate, String financialYear,
            String customerName, String categoryCode, String soaMarkNo,
            BigDecimal taxableAmount, BigDecimal cgstAmount, BigDecimal sgstAmount,
            BigDecimal igstAmount, BigDecimal grandTotal, InvoiceStatus status) {}

    public record InvoiceRequest(
            @NotNull(message = "Choose a customer")
            Long customerId,

            @NotBlank(message = "Choose a category")
            String categoryCode,

            @NotNull(message = "Invoice date is required")
            @PastOrPresent(message = "An invoice cannot be dated in the future")
            LocalDate invoiceDate,

            /** Existing shipment to attach, or null. Ignored when `shipment` is supplied. */
            Long shipmentId,

            /** A new shipment to create and attach. */
            @Valid ShipmentRequest shipment,

            String hsnCode,
            String headerNote,

            String postTaxAdjustmentLabel,
            @DecimalMin(value = "0.00", message = "A deduction cannot be negative")
            BigDecimal postTaxAdjustmentAmount,

            @NotEmpty(message = "An invoice needs at least one line")
            @Valid List<ItemRequest> items,

            @Valid AnnexureRequest annexure,

            /**
             * Issue this exact running number instead of taking the next one.
             * Used to reissue a number freed by a deletion, or to fill a gap.
             */
            @Min(value = 1, message = "An invoice number has to be 1 or more")
            Integer runningNumber) {}

    // ---------- annexure ------------------------------------------------------

    public record AnnexureView(Long id, String title, String col1Header, String col2Header,
                               String footerText, boolean drivesQuantity,
                               List<AnnexureRowView> rows) {}

    public record AnnexureRowView(Integer sequenceNo, String col1, String col2) {}

    public record AnnexureRequest(
            @NotBlank(message = "The statement needs a title") String title,
            String col1Header, String col2Header, String footerText,
            Boolean drivesQuantity,
            List<AnnexureRowView> rows) {}

    // ---------- shipment billing (the CNF + T pair) ---------------------------

    public record TransportLegRequest(
            @NotNull(message = "Choose a route") Long routeId,
            @NotNull @Min(value = 1, message = "A leg needs at least one container")
            Integer containers,

            /** Bill this leg at a price other than the route's. Null uses the route master. */
            @DecimalMin(value = "0.00", message = "Rate cannot be negative")
            BigDecimal rate,

            /** Keep the typed rate as the route's new price from here on. */
            Boolean updateMasterRate) {}

    public record BillPairRequest(
            @NotNull(message = "Choose a customer") Long customerId,

            @NotNull(message = "Invoice date is required")
            @PastOrPresent(message = "An invoice cannot be dated in the future")
            LocalDate invoiceDate,

            @NotNull @Valid ShipmentRequest shipment,

            /**
             * Required when {@code billMode} includes CNF; checked in the service
             * rather than here, since T-only is allowed to send this empty.
             */
            @Valid List<ItemRequest> cnfItems,

            /**
             * Required when {@code billMode} includes T; checked in the service
             * rather than here, since CNF-only is allowed to send this empty.
             */
            @Valid List<TransportLegRequest> legs,

            /** Per-TEU Hassan surcharge. Null or zero omits the line. */
            BigDecimal hassanRatePerTeu,

            /**
             * How many of the shipment's containers actually moved via Hassan.
             * Null means all of them. A 7-container shipment can route 3 or 4 that
             * way and the rest direct, so the surcharge is not always the whole bill.
             */
            @Min(value = 1, message = "At least one container has to go via Hassan")
            Integer hassanContainers,

            /** BOTH (default when null), CNF or T — which bill(s) to actually create. */
            String billMode,

            /**
             * Issue the CNF bill under this exact number instead of the next one
             * (the T bill, when made, takes the number right after). Used to
             * reissue a number freed by a deletion, or fill a gap.
             */
            @Min(value = 1, message = "An invoice number has to be 1 or more")
            Integer startingRunningNumber) {}

    /** Changes a transport route's standing price. */
    public record RouteRateRequest(
            @NotNull(message = "A price is required")
            @DecimalMin(value = "0.00", message = "A price cannot be negative")
            BigDecimal ratePerContainer) {}

    // ---------- SOA -----------------------------------------------------------

    public record SoaPreview(
            String customerName, String customerGstin,
            LocalDate from, LocalDate to,
            List<InvoiceSummary> rows,
            BigDecimal totalGrand, BigDecimal totalTaxable,
            BigDecimal totalCgst, BigDecimal totalSgst, BigDecimal totalIgst) {}

    // ---------- misc ----------------------------------------------------------

    public record CurrentUser(String username, String displayName, String role) {}

    public record DeleteResult(String invoiceNumber, String wasStatus,
                               boolean numberFreed, Integer freedNumber, String note,
                               List<String> rateNotes) {}

    public record BulkFinalizeRequest(
            @NotEmpty(message = "Select at least one invoice") List<Long> ids) {}

    public record BulkFinalizeResult(List<String> approved, List<BulkProblem> refused,
                                     int approvedCount, int refusedCount) {}

    public record BulkProblem(Long id, String invoiceNumber, String message,
                              List<String> problems) {}

    public record DashboardView(
            String month, long invoiceCount,
            BigDecimal totalBilled, BigDecimal totalTaxable, BigDecimal totalGst,
            List<CategoryTotal> byCategory, long draftCount) {}

    public record CategoryTotal(String categoryCode, long count, BigDecimal grandTotal) {}

    public record ApiError(String message, List<FieldProblem> problems) {}

    public record FieldProblem(String field, String message) {}
}
