package com.ase.billing.domain.enums;

/**
 * Which body layout a category prints with. The bills use three:
 *   ITEMISED  numbered particulars, totals block to the right of the bank details (CNF, TA)
 *   TRANSPORT route lines with unpriced sub-lines, totals stacked below the amount (T)
 *   STATEMENT header note, few lines, totals block above the bank details (S, CB)
 */
public enum PdfLayout { ITEMISED, TRANSPORT, STATEMENT }
