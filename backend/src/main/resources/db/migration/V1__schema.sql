-- ASE Billing & SOA — base schema
-- MySQL 8.0+. All money as DECIMAL(14,2). No FLOAT/DOUBLE anywhere.

CREATE TABLE company_settings (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  name              VARCHAR(120)  NOT NULL,
  address_line1     VARCHAR(160)  NOT NULL,
  address_line2     VARCHAR(160),
  address_line3     VARCHAR(160),
  city              VARCHAR(80)   NOT NULL,
  pin               VARCHAR(12)   NOT NULL,
  gstin             VARCHAR(20)   NOT NULL,
  bank_name         VARCHAR(120)  NOT NULL,
  bank_account_no   VARCHAR(40)   NOT NULL,
  bank_ifsc         VARCHAR(20)   NOT NULL,
  bank_branch_lines TEXT          NOT NULL,   -- newline-separated, printed verbatim on the bill
  default_hsn_code  VARCHAR(12)   NOT NULL DEFAULT '996713',
  signatory_line    VARCHAR(120)  NOT NULL DEFAULT 'For Aprameya Shipping Enterprises',
  updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE customers (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  code           VARCHAR(16)  NOT NULL,        -- 'HC' -> ASE/HC/CNF/439/2026-27
  name           VARCHAR(160) NOT NULL,
  address_line1  VARCHAR(160),
  address_line2  VARCHAR(160),
  address_line3  VARCHAR(160),
  city           VARCHAR(80),
  state          VARCHAR(80),
  pin            VARCHAR(12),
  gstin          VARCHAR(20),
  contact_person VARCHAR(120),
  phone          VARCHAR(40),
  email          VARCHAR(120),
  -- Intra-state (CGST+SGST) vs inter-state (IGST). Karnataka customer => INTRA.
  gst_treatment  ENUM('INTRA','INTER') NOT NULL DEFAULT 'INTRA',
  active         BOOLEAN NOT NULL DEFAULT TRUE,
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_customers_code (code)
) ENGINE=InnoDB;

CREATE TABLE service_categories (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  code         VARCHAR(8)   NOT NULL,          -- CNF, T, S, TA, CB
  description  VARCHAR(120) NOT NULL,
  -- Which PDF body layout this category prints with. See pdf/InvoiceLayout.
  pdf_layout   ENUM('ITEMISED','TRANSPORT','STATEMENT') NOT NULL DEFAULT 'ITEMISED',
  requires_shipment BOOLEAN NOT NULL DEFAULT TRUE,
  sort_order   INT NOT NULL DEFAULT 0,
  active       BOOLEAN NOT NULL DEFAULT TRUE,
  UNIQUE KEY uk_service_categories_code (code)
) ENGINE=InnoDB;

-- The canonical charge vocabulary. Seeded from the 370-434 and 437-480 bills.
CREATE TABLE services (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  category_id      BIGINT NOT NULL,
  name             VARCHAR(160) NOT NULL,      -- canonical, e.g. 'Certificate of origin'
  -- Template for the line printed on the PDF. {qty} {rate} {containers} are substituted.
  -- e.g. 'VGM expenses {qty}@RS{rate}/TEU'  ->  'VGM expenses 3@RS750/TEU'
  print_template   VARCHAR(240),
  calculation_type ENUM('SIMPLE','PER_TEU','PER_TEU_PER_DAY','PER_SET','MANUAL') NOT NULL DEFAULT 'MANUAL',
  default_unit     VARCHAR(24),
  sort_order       INT NOT NULL DEFAULT 0,
  active           BOOLEAN NOT NULL DEFAULT TRUE,
  UNIQUE KEY uk_services_cat_name (category_id, name),
  CONSTRAINT fk_services_category FOREIGN KEY (category_id) REFERENCES service_categories(id)
) ENGINE=InnoDB;

-- Rate history. Never updated in place; a rate change closes the old row and inserts a new one.
CREATE TABLE service_rates (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  service_id     BIGINT NOT NULL,
  customer_id    BIGINT NULL,                  -- NULL = global rate; set = customer-specific
  rate           DECIMAL(14,2) NOT NULL,
  gst_rate       DECIMAL(5,2)  NOT NULL DEFAULT 18.00,
  effective_from DATE NOT NULL,
  effective_to   DATE NULL,
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY ix_service_rates_lookup (service_id, customer_id, effective_from),
  CONSTRAINT fk_service_rates_service  FOREIGN KEY (service_id)  REFERENCES services(id),
  CONSTRAINT fk_service_rates_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
) ENGINE=InnoDB;

-- Transportation routes are priced per container, not per trip.
CREATE TABLE transport_routes (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  name               VARCHAR(120) NOT NULL,
  origin             VARCHAR(80)  NOT NULL,
  destination        VARCHAR(80)  NOT NULL,
  print_template     VARCHAR(240) NOT NULL,    -- 'Transportation Charges from {origin} to {destination} and back'
  rate_per_container DECIMAL(14,2) NOT NULL,
  active             BOOLEAN NOT NULL DEFAULT TRUE,
  UNIQUE KEY uk_transport_routes_name (name)
) ENGINE=InnoDB;

CREATE TABLE shipments (
  id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_id        BIGINT NOT NULL,
  hc_invoice_number  VARCHAR(60)  NOT NULL,    -- 'ES/HC/2027/094'
  ico_mark_full      VARCHAR(80)  NOT NULL,    -- '14/1850/2026/292-295' (ranges occur)
  soa_mark_no        VARCHAR(24)  NOT NULL,    -- derived tail: '292-295' -> '292'; shown in SOA
  container_count    INT NOT NULL,
  container_size     VARCHAR(8) NOT NULL DEFAULT '20',  -- '20' or '40'
  teu                DECIMAL(6,2) NOT NULL,    -- 20ft = 1.0, 40ft = 2.0
  destination_country VARCHAR(80),
  notes              VARCHAR(255),
  created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY ix_shipments_customer (customer_id),
  KEY ix_shipments_hc (hc_invoice_number),
  CONSTRAINT fk_shipments_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
) ENGINE=InnoDB;

-- Transaction-safe running number. One row per (customer, financial_year).
-- The running number is SHARED across categories: CNF/439 and T/440 are consecutive.
CREATE TABLE invoice_sequences (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_id    BIGINT NOT NULL,
  financial_year VARCHAR(9) NOT NULL,          -- '2026-27'
  next_number    INT NOT NULL,
  UNIQUE KEY uk_invoice_sequences (customer_id, financial_year),
  CONSTRAINT fk_invoice_sequences_customer FOREIGN KEY (customer_id) REFERENCES customers(id)
) ENGINE=InnoDB;

CREATE TABLE invoices (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  invoice_number   VARCHAR(60) NOT NULL,
  running_number   INT NOT NULL,
  invoice_date     DATE NOT NULL,
  financial_year   VARCHAR(9) NOT NULL,        -- derived from invoice_date, never typed
  customer_id      BIGINT NOT NULL,
  category_id      BIGINT NOT NULL,
  shipment_id      BIGINT NULL,                -- NULL for CB and S (no shipment)
  hsn_code         VARCHAR(12) NOT NULL DEFAULT '996713',
  header_note      VARCHAR(255),               -- 'EXPORT INCENTIVE CLAIM FOR THE YEAR 2025-2026'

  subtotal         DECIMAL(14,2) NOT NULL DEFAULT 0,
  taxable_amount   DECIMAL(14,2) NOT NULL DEFAULT 0,
  cgst_rate        DECIMAL(5,2)  NOT NULL DEFAULT 9.00,
  cgst_amount      DECIMAL(14,2) NOT NULL DEFAULT 0,
  sgst_rate        DECIMAL(5,2)  NOT NULL DEFAULT 9.00,
  sgst_amount      DECIMAL(14,2) NOT NULL DEFAULT 0,
  igst_rate        DECIMAL(5,2)  NOT NULL DEFAULT 0,
  igst_amount      DECIMAL(14,2) NOT NULL DEFAULT 0,
  grand_total      DECIMAL(14,2) NOT NULL DEFAULT 0,

  -- Applied AFTER GST. Bill ASE/HC/S/438: 60180.00 - 3758.00 = 56422.00.
  -- The SOA still reports grand_total (60180), not net_payable.
  post_tax_adjustment_label  VARCHAR(160),
  post_tax_adjustment_amount DECIMAL(14,2) NOT NULL DEFAULT 0,
  net_payable      DECIMAL(14,2) NOT NULL DEFAULT 0,

  amount_in_words  VARCHAR(400) NOT NULL,
  status           ENUM('DRAFT','FINALIZED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
  -- Back-entered historical bills keep whatever FY was printed on paper.
  back_entered     BOOLEAN NOT NULL DEFAULT FALSE,
  printed_fy       VARCHAR(9) NULL,

  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  created_by VARCHAR(60),
  updated_by VARCHAR(60),
  finalized_at DATETIME NULL,
  finalized_by VARCHAR(60),

  UNIQUE KEY uk_invoices_number (invoice_number),
  KEY ix_invoices_soa (customer_id, invoice_date, status),
  CONSTRAINT fk_invoices_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
  CONSTRAINT fk_invoices_category FOREIGN KEY (category_id) REFERENCES service_categories(id),
  CONSTRAINT fk_invoices_shipment FOREIGN KEY (shipment_id) REFERENCES shipments(id)
) ENGINE=InnoDB;

CREATE TABLE invoice_items (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  invoice_id     BIGINT NOT NULL,
  sequence_no    INT NOT NULL,
  service_id     BIGINT NULL,                  -- NULL for ad-hoc lines
  route_id       BIGINT NULL,                  -- set for transport lines
  -- What actually prints. Prefilled from the service template, then editable.
  printed_description VARCHAR(400) NOT NULL,
  quantity       DECIMAL(12,3) NULL,
  unit           VARCHAR(24)   NULL,
  rate           DECIMAL(14,2) NULL,           -- the rate AS BILLED, copied at creation
  days           INT NULL,
  teu            DECIMAL(6,2) NULL,
  calculation_type ENUM('SIMPLE','PER_TEU','PER_TEU_PER_DAY','PER_SET','MANUAL') NOT NULL DEFAULT 'MANUAL',
  amount         DECIMAL(14,2) NOT NULL,
  taxable        BOOLEAN NOT NULL DEFAULT TRUE,
  notes          VARCHAR(255),
  KEY ix_invoice_items_invoice (invoice_id, sequence_no),
  CONSTRAINT fk_invoice_items_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE CASCADE,
  CONSTRAINT fk_invoice_items_service FOREIGN KEY (service_id) REFERENCES services(id),
  CONSTRAINT fk_invoice_items_route   FOREIGN KEY (route_id)   REFERENCES transport_routes(id)
) ENGINE=InnoDB;

-- Unpriced detail printed under an item. Trailer/container numbers on T bills,
-- container split annotations ('1x20', '6x20') on multi-route T bills.
CREATE TABLE invoice_item_sublines (
  id              BIGINT AUTO_INCREMENT PRIMARY KEY,
  invoice_item_id BIGINT NOT NULL,
  sequence_no     INT NOT NULL,
  text            VARCHAR(300) NOT NULL,
  KEY ix_sublines_item (invoice_item_id, sequence_no),
  CONSTRAINT fk_sublines_item FOREIGN KEY (invoice_item_id) REFERENCES invoice_items(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- The PSC statement printed as page 2 of S invoices.
CREATE TABLE invoice_annexures (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  invoice_id  BIGINT NOT NULL,
  title       VARCHAR(200) NOT NULL,           -- 'HC SAMPLE PSC DETAILS-AUG -2026 ASE bill no 438'
  col1_header VARCHAR(80) NOT NULL DEFAULT 'CONSIGNEE',
  col2_header VARCHAR(80) NOT NULL DEFAULT 'PSC DT',
  footer_text TEXT,
  CONSTRAINT fk_annexure_invoice FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE invoice_annexure_rows (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  annexure_id BIGINT NOT NULL,
  sequence_no INT NOT NULL,
  col1        VARCHAR(200) NOT NULL,
  col2        VARCHAR(200),
  KEY ix_annexure_rows (annexure_id, sequence_no),
  CONSTRAINT fk_annexure_rows FOREIGN KEY (annexure_id) REFERENCES invoice_annexures(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE users (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  username      VARCHAR(60)  NOT NULL,
  password_hash VARCHAR(120) NOT NULL,
  display_name  VARCHAR(120) NOT NULL,
  role          ENUM('ADMIN','USER') NOT NULL DEFAULT 'USER',
  active        BOOLEAN NOT NULL DEFAULT TRUE,
  UNIQUE KEY uk_users_username (username)
) ENGINE=InnoDB;

CREATE TABLE audit_logs (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  entity_type VARCHAR(60) NOT NULL,
  entity_id   BIGINT NOT NULL,
  action      VARCHAR(40) NOT NULL,
  username    VARCHAR(60),
  detail      TEXT,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY ix_audit_entity (entity_type, entity_id)
) ENGINE=InnoDB;
