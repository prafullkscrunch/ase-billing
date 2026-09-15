package com.ase.billing.service;

import java.time.LocalDate;
import java.time.Month;

/**
 * Indian financial year, April to March.
 *
 * Derived from the invoice date and never entered by hand. Four bills across the
 * two supplied PDFs carry a financial year that contradicts their own date
 * (CNF/394, CNF/396, CNF/408 and TA/450 are all tagged 2025-26 on 2026-27 dates),
 * which is exactly the class of error this removes.
 */
public final class FinancialYear {

    private FinancialYear() {}

    /** 2026-08-08 -> "2026-27";  2026-03-31 -> "2025-26". */
    public static String of(LocalDate date) {
        int startYear = date.getMonthValue() >= Month.APRIL.getValue()
                ? date.getYear()
                : date.getYear() - 1;
        return startYear + "-" + String.format("%02d", (startYear + 1) % 100);
    }

    public static LocalDate startOf(String financialYear) {
        return LocalDate.of(Integer.parseInt(financialYear.substring(0, 4)), 4, 1);
    }

    public static LocalDate endOf(String financialYear) {
        return startOf(financialYear).plusYears(1).minusDays(1);
    }

    public static boolean contains(String financialYear, LocalDate date) {
        return !date.isBefore(startOf(financialYear)) && !date.isAfter(endOf(financialYear));
    }
}
