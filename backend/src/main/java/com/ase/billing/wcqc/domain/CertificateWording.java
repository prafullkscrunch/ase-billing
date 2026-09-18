package com.ase.billing.wcqc.domain;

/**
 * Text that is NEVER a form field. Every string here was copied character for
 * character (typos included) from the historical WC/QC files and confirmed
 * identical across multiple samples of that type. Requirement: "Do not use AI
 * to rewrite or paraphrase certificate text" / "preserve exactly: spelling,
 * punctuation...". The safest way to honour that is to make these Java
 * constants that no controller, service or form can touch — not fields on
 * the Certificate entity, which could be edited.
 *
 * If ASE ever changes this wording, it must be changed here, deliberately, in
 * one place — never by a value coming from a request body.
 *
 * <p>The exporter block, column headings, route labels, and the WC/QC
 * certification statements used to live here too, for the PDF renderer to
 * type out by hand. That renderer has been removed — Excel is the only
 * export format now — and {@link com.ase.billing.wcqc.excel.CertificateExcelService}
 * fills the actual historical xlsx templates directly, so that fixed
 * wording already lives in exactly one place: the template files
 * themselves (wcqc-templates/wc-master.xlsx, qc-master.xlsx). Duplicating
 * it here as unused constants would only invite someone to "fix" a typo in
 * a copy that no longer controls anything real.
 */
public final class CertificateWording {

    private CertificateWording() {}

    // ---- COPROCAFE QC (Word template) --------------------------------------
    // These are the fixed sentence fragments the Word generator stitches the
    // dynamic values into. See CoprocafeWordService for where each one is used.

    public static final String COPROCAFE_TITLE_1 = "QUALITY CERTIFICATE";
    public static final String COPROCAFE_TITLE_2 = "(SHIPMENT)";
    public static final String COPROCAFE_TITLE_3 = "TO WHOM IT MAY CONCERN";

    public static final String COPROCAFE_BODY_TEMPLATE =
        "WE CERTIFY THAT THE %s BAGS OF %s KGS OF GREEN COFFEE BEANS FROM HANGAL COFFEE EXPORTING "
        + "PVT LTD OF THE %s VARIETY, PROCESSED THROUGH THE DRY PROCEDURE, AND BEARING THE MARKS %s "
        + "HAVE BEEN SHIPPED IN A CLEAN CONTAINER AND IN PERFECT CONDITIONS FOR ITS TRANSPORT, "
        + "ACCORDING TO THE QUALITY STADARDS DEMAND BY NKG COPROCAFE IBERICA S.A. ";

    public static final String COPROCAFE_HUMIDITY_LINE_1 =
        "SAID COFFEE WAS LOADED IN THE CONTAINER BEARING A MEASURED ISO 1446:20001(E) HUMIDITY OF ";

    public static final String COPROCAFE_HUMIDITY_LINE_2_TEMPLATE =
        "%s%%, AFTER THE WALLS OF THE CONTAINER WERE LINED WITH KRAFT PAPER AND DRY BAGS WERE "
        + "INSERTED TO ENSURE THE QUALITY OF THE GOODS DURING SHIPMENT.";

    public static final String COPROCAFE_ISO_LINE = "ISO 1446:2001(E)";
    public static final String COPROCAFE_SIGNATURE = "SIGNATURE AND STAMP";
}
