package com.ase.billing.service;

import com.ase.billing.domain.Customer;
import com.ase.billing.domain.ServiceItem;
import com.ase.billing.domain.ServiceRate;
import com.ase.billing.repo.ServiceRateRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Resolves the rate to suggest for a line.
 *
 *   manual override  (handled by the caller — if the user typed a rate, use it)
 *        v
 *   customer rate
 *        v
 *   global rate
 *
 * Whatever comes back is copied into invoice_items.rate at creation. Changing a
 * master rate afterwards must never alter an existing invoice: bills CNF/400 and
 * CNF/430 bill VGM at 750 and 500 per TEU in the same month, so rates genuinely
 * move and old bills have to keep what they were issued with.
 */
@Service
public class RateResolver {

    private final ServiceRateRepository rates;

    public RateResolver(ServiceRateRepository rates) {
        this.rates = rates;
    }

    public Optional<ServiceRate> resolve(ServiceItem service, Customer customer, LocalDate onDate) {
        List<ServiceRate> found = rates.resolve(service.getId(), customer.getId(), onDate);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    public BigDecimal rateOrZero(ServiceItem service, Customer customer, LocalDate onDate) {
        return resolve(service, customer, onDate).map(ServiceRate::getRate).orElse(BigDecimal.ZERO);
    }

    /**
     * Fills {qty} {rate} {days} {containers} in a service print template.
     * 'VGM expenses {qty}@RS{rate}/TEU' + qty 3, rate 750 -> 'VGM expenses 3@RS750/TEU'
     */
    public static String renderDescription(String template, BigDecimal qty, BigDecimal rate,
                                           Integer days, String containerNotation) {
        if (template == null) return "";
        return template
                .replace("{qty}", plain(qty))
                .replace("{rate}", plain(rate))
                .replace("{days}", days == null ? "" : String.valueOf(days))
                .replace("{containers}", containerNotation == null ? "" : containerNotation);
    }

    private static String plain(BigDecimal v) {
        if (v == null) return "";
        return v.stripTrailingZeros().toPlainString();
    }
}
