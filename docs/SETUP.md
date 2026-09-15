# ASE Billing & SOA — setup and build guide

Everything here assumes you are working alone on your own machine, with MySQL running locally.

---

## 1. What is already built

The scaffold is not a placeholder. These parts are written, compile, and have tests that pass against figures taken from the real bills:

| Area | File | State |
|---|---|---|
| Schema | `backend/src/main/resources/db/migration/V1__schema.sql` | Complete |
| Seed data | `.../V2__seed_masters.sql` | Complete — company, Hangal, 5 categories, 37 services, 4 routes, 20 rates |
| Financial year | `service/FinancialYear.java` | Complete |
| Amount in words | `service/AmountInWords.java` | Complete, Indian numbering |
| Rounding policy | `service/MoneyPolicy.java` | Complete |
| Calculation engine | `service/InvoiceCalculationService.java` | Complete |
| Invoice numbering | `service/InvoiceNumberService.java` | Complete, pessimistic lock |
| Rate resolution | `service/RateResolver.java` | Complete |
| Shipment pair billing | `service/ShipmentBillingService.java` | Complete |
| Invoice PDF | `pdf/InvoicePdfService.java` | Renders; needs visual tuning against the paper bills |
| SOA workbook | `excel/SoaExcelService.java` | Complete |
| Entities and repos | `domain/`, `repo/` | Complete |
| Tests | `src/test/` | 27 passing |
| REST API | `web/` | Complete — 18 endpoints |
| Frontend | `frontend/src/` | Complete — 6 screens |
| Auth | `config/`, `service/DbUserDetailsService` | Complete, database-backed |

---

## 2. Prerequisites

```
JDK 21 (LTS)     java -version      <- not 24; see docs/INSTALL.md
MySQL Server 8   mysql --version    <- the server, not just Workbench
Node 20+         node -v            <- frontend only, can wait
Maven            NOT needed - use the bundled wrapper, ./mvnw or .\mvnw.cmd
```

**For IntelliJ setup, JDK choice and the Windows install steps, read `docs/INSTALL.md` first.**
It covers the two settings that otherwise waste an afternoon: IntelliJ's separate
annotation-processing switch, and the fact that MySQL Workbench is not a server.

The project builds on JDK 21 and JDK 24 — both are tested — but 21 is what
Spring Boot 3.3 is certified against and is the one to use.

---

## 3. Database

```sql
CREATE DATABASE ase_billing CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'ase'@'localhost' IDENTIFIED BY 'your-password';
GRANT ALL PRIVILEGES ON ase_billing.* TO 'ase'@'localhost';
FLUSH PRIVILEGES;
```

Flyway runs all four migrations on first boot. Hibernate is set to `ddl-auto: validate`, so if an entity and the schema ever disagree the application refuses to start rather than quietly altering a table. Keep it that way.

---

## 4. Backend

```bash
cd backend

export DB_HOST=localhost DB_PORT=3306 DB_NAME=ase_billing
export DB_USER=ase DB_PASSWORD='your-password'

# Windows PowerShell:  .\mvnw.cmd test   /   .\mvnw.cmd spring-boot:run
./mvnw test              # 27 tests
./mvnw spring-boot:run
```

Runs on `http://localhost:8080`. Seeded login is `admin` / `changeme` — change it before this touches a real bill.

---

## 5. Frontend

```bash
cd frontend
npm install
npm run dev
```

Runs on `http://localhost:5173` and proxies `/api` to port 8080, so the Spring Security session cookie is same-origin and just works. Don't switch to a cross-origin setup; you'll spend an afternoon on CORS and cookies for nothing.

---

## 6. File structure

```
ase-billing/
├── backend/
│   ├── mvnw, mvnw.cmd    Maven wrapper - no Maven install needed
│   ├── .mvn/wrapper/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/ase/billing/
│       │   │   ├── AseBillingApplication.java
│       │   │   ├── config/          SecurityConfig
│       │   │   ├── domain/          JPA entities
│       │   │   │   └── enums/       CalculationType, InvoiceStatus, GstTreatment, PdfLayout
│       │   │   ├── repo/            Spring Data repositories
│       │   │   ├── service/         business logic — the part that matters
│       │   │   ├── pdf/             InvoicePdfService (OpenPDF)
│       │   │   ├── excel/           SoaExcelService (Apache POI)
│       │   │   ├── web/             REST controllers, DTOs, mapper
│       │   │   └── exception/       validation + error handling
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── db/migration/    V1 schema, V2 seed, V3 admin, V4 annexure key
│       │       └── static/          the built frontend, bundled into the jar
│       └── test/java/com/ase/billing/
│           ├── ContextLoadsTest.java       Spring boots against H2
│           ├── AuthenticationTest.java     login works end to end
│           ├── WebEndpointsTest.java       every endpoint returns 200, not 500
│           └── service/
│               ├── BillFiguresTest.java            pinned to the real bills
│               └── CalculationRegressionTest.java  the fixed defects
├── .run/                 IntelliJ run configuration
├── docker-compose.yml    MySQL 8.4, if you'd rather not install the server
├── frontend/
│   └── src/
│       ├── api/          client.ts, endpoints.ts
│       ├── hooks/        useAuth.tsx
│       ├── components/   ParticularsTable, TotalsPanel, Alert, Money
│       ├── pages/        Dashboard, ShipmentBilling, InvoiceList, InvoiceEditor,
│       │                 SoaPage, LoginPage
│       └── types/        index.ts
├── database/             backup scripts
└── docs/                 INSTALL.md, SETUP.md, BUSINESS_RULES.md
```

### Why the layers are split this way

`service/` holds every rule that could produce a wrong number. Controllers do nothing but map HTTP to a service call. `pdf/` and `excel/` are output adapters — they read a fully computed invoice and never calculate anything themselves. If you ever find yourself adding a multiplication inside `InvoicePdfService`, it belongs in `InvoiceCalculationService`.

---

## 7. Build order

Steps 1 to 4 are already done. Pick up at 5.

1. ~~Schema and seed~~
2. ~~Entities and repositories~~
3. ~~Calculation engine, numbering, financial year, amount in words~~
4. ~~Tests against real bill figures~~

5. ~~REST controllers~~ — done. The endpoints are:

   ```
   POST   /api/shipments/bill-pair     shipment + CNF lines + transport legs -> CNF and T invoice
   GET    /api/invoices                list, filter by date/category/status
   POST   /api/invoices                create draft (standalone TA, S, CB)
   PUT    /api/invoices/{id}           update draft, returns recalculated totals
   POST   /api/invoices/{id}/finalize  validate, lock
   GET    /api/invoices/{id}/pdf       application/pdf
   GET    /api/soa/excel               the workbook
   ```

   Both rules are enforced in `InvoiceService`: every write calls
   `InvoiceCalculationService.recalculate` and returns what it produced, and any
   write to a non-DRAFT invoice is refused.

   One thing to keep in mind when adding endpoints: `open-in-view` is off, so any
   handler that reads a lazy association must be `@Transactional` or work from a
   `join fetch` query. Three endpoints shipped with that fault and returned 500s.
   `WebEndpointsTest` now walks every read endpoint to catch it.

6. ~~ShipmentBilling, SoaPage, InvoiceList, InvoiceEditor~~ — done.

7. **PDF tuning — the remaining work.** Generate CNF/370 and CNF/439 and hold them
   against the scans. The renderer produces the right figures and the right
   structure; what it has never been checked against is the paper. Expect several
   rounds. This is what ASE will judge the system by.

8. **Back-entry mode** — a flag on the create form that lets you type a historical invoice number, date and financial year. `InvoiceNumberService.reserveHistorical` already keeps the sequence ahead. This replaces the OCR import idea, which is not worth building.

---

## 8. Finalisation checks

Enforce all of these before an invoice can leave `DRAFT`. Each one corresponds to an error that exists in the supplied documents.

```
at least one item
every item has a printed description
subtotal equals the sum of item amounts       <- CNF/459 prints 54,850 against items summing to 55,650
CGST equals SGST for an INTRA customer        <- SOA rows 11 and 13 have them unequal
CGST equals taxable x rate, to the paisa      <- SOA row 37 is 0.17 out
grand total equals taxable + CGST + SGST      <- 11 rows of the RI block fail this
financial year matches the invoice date       <- four bills carry the wrong year
HSN code present                              <- eight bills omit it
shipment present when the category needs one
```

---

## 9. Backup

```bash
mysqldump --single-transaction --routines --triggers \
  -u ase -p ase_billing | gzip > ase_billing_$(date +%F).sql.gz
```

Run it nightly. Keep 30 days. Restore with `gunzip -c file.sql.gz | mysql -u ase -p ase_billing`.

Test the restore once, into a scratch database, before you rely on it.

---

## 10. Things not to do

- **Don't split the running number per category.** CNF/439 and T/440 are consecutive because the number is shared within a customer and financial year. A per-category sequence breaks the shipment pairing.
- **Don't compute GST in the browser.** Two rounding implementations will drift apart and you will not notice until a GST return is wrong.
- **Don't let a rate change touch an old invoice.** The rate is copied into `invoice_items.rate` at creation. Bills CNF/400 and CNF/430 bill VGM at 750 and 500 per TEU in the same month, so this genuinely happens.
- **Don't rebuild the printed line from quantity and rate at render time.** Print `invoice_items.printed_description`. The paper bill has two columns and the quantity lives inside the text.
- **Don't reproduce the old SOA workbook's formatting.** It has none worth keeping, and its column quirks (CGST before SGST, category in column I) are preserved deliberately in `SoaExcelService` while the styling is not.
- **Don't seed August 2026 from the old SOA file.** It contains three arithmetic errors. Enter the invoices and let the system compute.
- **Don't swap OpenPDF for iText 5+.** iText is AGPL; OpenPDF is LGPL/MPL and safe for a commercial deliverable.
