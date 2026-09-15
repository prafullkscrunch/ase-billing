package com.ase.billing.service;

import com.ase.billing.domain.*;
import com.ase.billing.repo.ServiceRateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Makes a rate typed on an invoice line stick for next time.
 *
 * ASE's rates move between shipments and the new figure is usually the one that
 * applies from then on, so retyping it on every subsequent bill is wasted work.
 * When a line carries a service and its rate differs from the master, the master
 * is moved to the new figure from the invoice date onward.
 *
 * The old rate is CLOSED, never overwritten:
 *
 *   VGM expenses   01-04-2026 → 30-06-2026   750
 *   VGM expenses   01-07-2026 → (open)       500
 *
 * and every invoice keeps its own copy in invoice_items.rate, so a bill issued
 * in June still reads 750 no matter what the master says later. Changing a rate
 * can never reach backwards into an issued tax document.
 */
@Service
public class RateUpdateService {

    private static final Logger log = LoggerFactory.getLogger(RateUpdateService.class);

    private final ServiceRateRepository rates;

    public RateUpdateService(ServiceRateRepository rates) {
        this.rates = rates;
    }

    /**
     * Applies every rate change carried by this invoice's lines.
     * Call after the invoice is saved, so the new rate can point back at it.
     */
    @Transactional
    public int applyFrom(Invoice invoice, String username) {
        int changed = 0;
        for (InvoiceItem item : invoice.getItems()) {
            if (item.getService() == null || item.getRate() == null) continue;
            if (!item.isUpdateMasterRate()) continue;
            if (updateRate(item.getService(), invoice.getCustomer(), item.getRate(),
                           invoice.getInvoiceDate(), invoice, username)) {
                changed++;
            }
        }
        return changed;
    }

    /**
     * Closes the rate in force on {@code from} and opens a new one, unless the
     * rate is already what it is being set to.
     *
     * @return true when the master actually moved
     */
    @Transactional
    public boolean updateRate(ServiceItem service, Customer customer, BigDecimal newRate,
                              LocalDate from, Invoice source, String username) {

        Optional<ServiceRate> current = rates.resolve(service.getId(), customer.getId(), from)
                .stream().findFirst();

        if (current.isPresent() && current.get().getRate().compareTo(newRate) == 0) {
            log.debug("Rate for service {} already {} from {}; nothing to update.",
                    service.getId(), newRate, from);
            return false;   // nothing to do
        }

        current.ifPresent(existing -> {
            // Only close a global rate here. A customer-specific rate is a
            // deliberate arrangement and is left alone.
            if (existing.getCustomer() == null && existing.getEffectiveTo() == null) {
                existing.setEffectiveTo(from.minusDays(1));
                rates.save(existing);
            }
        });

        ServiceRate fresh = new ServiceRate();
        fresh.setService(service);
        fresh.setCustomer(null);                 // the new house rate
        fresh.setRate(newRate);
        fresh.setGstRate(current.map(ServiceRate::getGstRate).orElse(new BigDecimal("18.00")));
        fresh.setEffectiveFrom(from);
        fresh.setSource("INVOICE");
        fresh.setSetFromInvoice(source == null ? null : source.getId());
        fresh.setSetBy(username);
        rates.save(fresh);
        log.info("Service {} rate moved to {} from {} by {} (invoice {}).",
                service.getId(), newRate, from, username,
                source == null ? null : source.getInvoiceNumber());
        return true;
    }

    /**
     * Called when an invoice that set a rate is deleted. Reverts the master
     * rate back to whatever it was before — but only in the one case that's
     * unambiguous: the rate this invoice set is still the current, untouched,
     * global rate for that service. If anything has changed the rate again
     * since, or it was a customer-specific arrangement, reverting could
     * silently undo someone else's later, deliberate change — so instead this
     * only clears the now-dangling reference to the deleted invoice and
     * reports plainly that the rate itself was left as is.
     *
     * @return one human-readable line per rate this invoice had touched, for
     *         the delete confirmation to show the operator.
     */
    @Transactional
    public List<String> revertRatesSetBy(Long invoiceId) {
        List<ServiceRate> caused = rates.findBySetFromInvoice(invoiceId);
        List<String> notes = new ArrayList<>();

        for (ServiceRate row : caused) {
            String serviceName = row.getService().getName();

            boolean stillTheActiveGlobalRate = row.getCustomer() == null && row.getEffectiveTo() == null;
            if (!stillTheActiveGlobalRate) {
                row.setSetFromInvoice(null);
                rates.save(row);
                notes.add(serviceName + "'s rate has changed again since this invoice set it to "
                        + row.getRate() + " — left as is.");
                log.info("Invoice {} deleted, but service {} rate has since moved on; left at current value.",
                        invoiceId, row.getService().getId());
                continue;
            }

            Optional<ServiceRate> prior = rates.findByServiceIdAndCustomerIsNullAndEffectiveTo(
                    row.getService().getId(), row.getEffectiveFrom().minusDays(1));

            if (prior.isEmpty()) {
                row.setSetFromInvoice(null);
                rates.save(row);
                notes.add(serviceName + " was set to " + row.getRate()
                        + " by this invoice, with no earlier rate to revert to — left as is.");
                log.info("Invoice {} deleted, but no prior rate exists for service {} to revert to; left at current value.",
                        invoiceId, row.getService().getId());
                continue;
            }

            ServiceRate reopened = prior.get();
            reopened.setEffectiveTo(null);
            rates.save(reopened);
            rates.delete(row);
            notes.add(serviceName + " reverted to " + reopened.getRate()
                    + " (this invoice had moved it to " + row.getRate() + ").");
            log.info("Invoice {} deleted; reverted service {} rate from {} back to {}.",
                    invoiceId, row.getService().getId(), row.getRate(), reopened.getRate());
        }
        return notes;
    }
}
