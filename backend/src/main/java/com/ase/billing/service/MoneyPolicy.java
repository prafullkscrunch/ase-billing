package com.ase.billing.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The single rounding policy for the whole application. Nothing else may call
 * setScale on a monetary value, and the frontend must never compute a total it
 * then sends back — the backend is authoritative.
 *
 * Precision, fixed by what the bills and the SOA actually do:
 *   line amount   2dp   (Seal Charges bill at 1106.50/TEU, so paise are real)
 *   CGST, SGST    2dp   (4765.50 appears throughout)
 *   grand total   0dp   (every bill prints a whole-rupee grand total)
 *
 * Order matters. Tax is computed on the taxable total and rounded once, not
 * per line. Rounding each line's tax and summing gives a different answer.
 */
public final class MoneyPolicy {

    public static final int MONEY_SCALE = 2;
    public static final int TOTAL_SCALE = 0;
    public static final RoundingMode MODE = RoundingMode.HALF_UP;

    private MoneyPolicy() {}

    public static BigDecimal money(BigDecimal v) {
        return v == null ? BigDecimal.ZERO.setScale(MONEY_SCALE) : v.setScale(MONEY_SCALE, MODE);
    }

    public static BigDecimal total(BigDecimal v) {
        return v == null ? BigDecimal.ZERO.setScale(TOTAL_SCALE) : v.setScale(TOTAL_SCALE, MODE);
    }

    /** taxableAmount x ratePercent / 100, rounded to 2dp once. */
    public static BigDecimal taxOf(BigDecimal taxableAmount, BigDecimal ratePercent) {
        return money(taxableAmount.multiply(ratePercent)
                                  .divide(BigDecimal.valueOf(100), 6, MODE));
    }
}
