package com.ase.billing.wcqc.domain.enums;

/**
 * WC only ever comes in one shape (STANDARD). QC has a second shape,
 * COPROCAFE, which is the only one that also exports as a Word document
 * and which carries a few extra fields (bag count / moisture) that the
 * standard QC template has no place for.
 *
 * Studying wc.zip and qc.zip turned up no other template family in
 * consistent, repeated use — every other customer name is just a
 * consignee value dropped into the STANDARD QC layout. If a genuinely
 * different QC template shows up later, add a variant here rather than
 * bending STANDARD to fit it.
 */
public enum CertificateVariant {
    STANDARD,
    COPROCAFE
}
