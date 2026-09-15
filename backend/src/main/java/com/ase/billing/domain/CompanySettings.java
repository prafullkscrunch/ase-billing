package com.ase.billing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Editable from Settings. Nothing here is hard-coded in Java. */
@Entity
@Table(name = "company_settings")
@Getter
@Setter
public class CompanySettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120) private String name;
    @Column(name = "address_line1", nullable = false, length = 160) private String addressLine1;
    @Column(name = "address_line2", length = 160) private String addressLine2;
    @Column(name = "address_line3", length = 160) private String addressLine3;
    @Column(nullable = false, length = 80) private String city;
    @Column(nullable = false, length = 12) private String pin;
    @Column(nullable = false, length = 20) private String gstin;

    @Column(name = "bank_name", nullable = false, length = 120) private String bankName;
    @Column(name = "bank_account_no", nullable = false, length = 40) private String bankAccountNo;
    @Column(name = "bank_ifsc", nullable = false, length = 20) private String bankIfsc;

    /** Newline-separated branch address block, printed verbatim under the bank details. */
    @Column(name = "bank_branch_lines", nullable = false, columnDefinition = "TEXT")
    private String bankBranchLines;

    @Column(name = "default_hsn_code", nullable = false, length = 12)
    private String defaultHsnCode = "996713";

    @Column(name = "signatory_line", nullable = false, length = 120)
    private String signatoryLine = "For Aprameya Shipping Enterprises";

    /** Draw the proprietor's signature block on generated bills. */
    @Column(name = "show_signature", nullable = false)
    private boolean showSignature = true;
}
