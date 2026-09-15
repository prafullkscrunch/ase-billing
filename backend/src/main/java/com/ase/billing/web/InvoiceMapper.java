package com.ase.billing.web;

import com.ase.billing.domain.*;
import com.ase.billing.web.dto.Dtos.*;
import org.springframework.stereotype.Component;

import java.util.List;

/** Entity to wire-format. One direction only; requests are applied by InvoiceService. */
@Component
public class InvoiceMapper {

    public InvoiceView toView(Invoice inv) {
        return new InvoiceView(
                inv.getId(),
                inv.getInvoiceNumber(),
                inv.getRunningNumber(),
                inv.getInvoiceDate(),
                inv.getFinancialYear(),
                inv.getCustomer().getId(),
                inv.getCustomer().getName(),
                inv.getCategory().getCode(),
                inv.getCategory().getDescription(),
                inv.getShipment() == null ? null : toView(inv.getShipment()),
                inv.getHsnCode(),
                inv.getHeaderNote(),
                inv.getSubtotal(),
                inv.getTaxableAmount(),
                inv.getCgstRate(), inv.getCgstAmount(),
                inv.getSgstRate(), inv.getSgstAmount(),
                inv.getIgstRate(), inv.getIgstAmount(),
                inv.getGrandTotal(),
                inv.getPostTaxAdjustmentLabel(),
                inv.getPostTaxAdjustmentAmount(),
                inv.getNetPayable(),
                inv.getAmountInWords(),
                inv.getStatus(),
                inv.isEditable(),
                inv.getItems().stream().map(this::toView).toList(),
                inv.getAnnexure() == null ? null : toView(inv.getAnnexure()));
    }

    public InvoiceSummary toSummary(Invoice inv) {
        return new InvoiceSummary(
                inv.getId(),
                inv.getInvoiceNumber(),
                inv.getInvoiceDate(),
                inv.getFinancialYear(),
                inv.getCustomer().getName(),
                inv.getCategory().getCode(),
                inv.getShipment() == null ? null : inv.getShipment().getSoaMarkNo(),
                inv.getTaxableAmount(),
                inv.getCgstAmount(),
                inv.getSgstAmount(),
                inv.getIgstAmount(),
                inv.getGrandTotal(),
                inv.getStatus());
    }

    public ItemView toView(InvoiceItem i) {
        return new ItemView(
                i.getId(), i.getSequenceNo(),
                i.getService() == null ? null : i.getService().getId(),
                i.getRoute() == null ? null : i.getRoute().getId(),
                i.getPrintedDescription(),
                i.getQuantity(), i.getUnit(), i.getRate(), i.getBaseAmount(), i.getDays(), i.getTeu(),
                i.getCalculationType(), i.getAmount(), i.isTaxable(), i.getNotes(),
                i.getSubLines().stream().map(InvoiceItemSubline::getText).toList());
    }

    public ShipmentView toView(Shipment s) {
        return new ShipmentView(
                s.getId(), s.getHcInvoiceNumber(), s.getIcoMarkFull(), s.getSoaMarkNo(),
                s.getContainerCount(), s.getContainerSize(), s.getTeu(),
                s.containerNotation());
    }

    public AnnexureView toView(InvoiceAnnexure a) {
        List<AnnexureRowView> rows = a.getRows().stream()
                .map(r -> new AnnexureRowView(r.getSequenceNo(), r.getCol1(), r.getCol2()))
                .toList();
        return new AnnexureView(a.getId(), a.getTitle(), a.getCol1Header(),
                a.getCol2Header(), a.getFooterText(), a.isDrivesQuantity(), rows);
    }

    public CustomerView toView(Customer c) {
        return new CustomerView(c.getId(), c.getCode(), c.getName(), c.getGstin(),
                c.getCity(), c.getGstTreatment().name());
    }

    public CategoryView toView(ServiceCategory c) {
        return new CategoryView(c.getId(), c.getCode(), c.getDescription(),
                c.getPdfLayout().name(), c.isRequiresShipment());
    }

    public RouteView toView(TransportRoute r) {
        return new RouteView(r.getId(), r.getName(), r.getOrigin(), r.getDestination(),
                r.getPrintTemplate(), r.getRatePerContainer());
    }
}
