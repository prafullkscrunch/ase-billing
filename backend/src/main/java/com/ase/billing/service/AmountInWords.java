package com.ase.billing.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Indian-numbering amount in words (thousand / lakh / crore).
 *
 * The bills use two styles: sentence case "Amount in words: Sixty-two thousand ... only"
 * (57 occurrences, mostly CNF) and uppercase "AMOUNT IN WORDS RUPEES.SIXTY TWO ... ONLY"
 * (53 occurrences, mostly T). Sentence case is the more common of the two and is the
 * canonical style here. New bills will not match old T bills character for character.
 */
public final class AmountInWords {

    private static final String[] ONES = {
        "", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
        "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
        "seventeen", "eighteen", "nineteen"
    };

    private static final String[] TENS = {
        "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"
    };

    private AmountInWords() {}

    /** Returns e.g. "Sixty-two thousand four hundred and eighty-one only". */
    public static String of(BigDecimal amount) {
        BigDecimal rounded = amount.setScale(2, RoundingMode.HALF_UP);

        // A negative total is a data fault (an adjustment larger than the bill).
        // Without this the digit loop produces an empty string and the invoice
        // prints " only" as its amount in words.
        boolean negative = rounded.signum() < 0;
        if (negative) {
            rounded = rounded.negate();
        }

        long rupees = rounded.longValue();
        int paise = rounded.subtract(BigDecimal.valueOf(rupees))
                           .movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValue();

        if (rupees == 0 && paise == 0) return "Zero only";

        StringBuilder sb = new StringBuilder(indianWords(rupees));
        if (paise > 0) {
            sb.append(" and ").append(twoDigits(paise)).append(" paise");
        }
        sb.append(" only");
        String words = capitaliseFirst(sb.toString());
        return negative ? "Minus " + words.substring(0, 1).toLowerCase() + words.substring(1) : words;
    }

    private static String indianWords(long n) {
        if (n == 0) return "zero";
        StringBuilder sb = new StringBuilder();
        long crore    = n / 10_000_000L;
        long lakh     = (n / 100_000L) % 100;
        long thousand = (n / 1_000L) % 100;
        long hundred  = (n / 100L) % 10;
        long rest     = n % 100;

        if (crore > 0)    sb.append(indianWords(crore)).append(" crore ");
        if (lakh > 0)     sb.append(twoDigits((int) lakh)).append(" lakh ");
        if (thousand > 0) sb.append(twoDigits((int) thousand)).append(" thousand ");
        if (hundred > 0)  sb.append(ONES[(int) hundred]).append(" hundred ");
        if (rest > 0) {
            // "four hundred and eighty-one", but "eighty-one" with nothing before it.
            if (sb.length() > 0) sb.append("and ");
            sb.append(twoDigits((int) rest));
        }
        return sb.toString().trim().replaceAll("\\s+", " ");
    }

    private static String twoDigits(int n) {
        if (n < 20) return ONES[n];
        String t = TENS[n / 10];
        return n % 10 == 0 ? t : t + "-" + ONES[n % 10];
    }

    private static String capitaliseFirst(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
