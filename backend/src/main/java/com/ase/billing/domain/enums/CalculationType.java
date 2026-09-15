package com.ase.billing.domain.enums;

public enum CalculationType {
    /** amount = quantity x rate */
    SIMPLE,
    /** amount = teu x rate */
    PER_TEU,
    /** amount = teu x days x rate  (halting charges) */
    PER_TEU_PER_DAY,
    /** amount = sets x rate  (PSC sets, export incentive applications) */
    PER_SET,
    /** user types the amount directly */
    MANUAL
}
