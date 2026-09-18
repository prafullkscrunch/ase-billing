# WC & QC Certificate Module — Build Notes & Integration Guide

Written after studying ~500 real files (`wc.zip`, `qc.zip`, `wtqt.zip`, `wtqt2707.zip`)
and the existing ASE Billing source (`ase-billing.zip`) plus
`ASE_BILLING_SESSION_SUMMARY.md`. Read this before touching anything —
it explains *why* the module is shaped the way it is, which matters more
here than in most features because several design choices only make sense
once you've seen what the historical documents actually look like.

---

## 1. What was actually found in the historical files (read this first)

These findings drove every non-obvious design decision below.

1. **There is no WC/QC-specific serial number anywhere on the documents
   themselves.** The only identifying number on a WC or QC certificate is
   the shipment's own mark, e.g. `14/1850/2026/377` — the same mark printed
   on the matching invoice. Filenames like `HC-WC 320 B 377.xlsx` just
   reuse that number for filing. So this module does **not** mint a new
   sequence (per requirement #11 — "unless the historical certificate
   documents clearly require it" — they clearly require reusing the mark,
   not inventing a new one). `Certificate.marksAndNos` is that field, and
   it's what the History screen shows as "WC/QC number".

2. **Rows 1–20 of every sampled sheet sit at identical cell coordinates** —
   exporter block, consignee/notify, pre-carriage, port of loading/
   discharge, final destination, country of origin/final destination. This
   is true across WC, QC, and even the invoice files themselves (all three
   document types are literally the same Excel sheet named `ic-028`). Those
   became real, individually editable fields on `Certificate`.

3. **Rows ~21–31 (marks, packaging, grade, weights, HS code, totals) do
   NOT sit at consistent coordinates.** Different clerks typed a different
   number of lines in a different order depending on how long the
   description ran — confirmed by diffing a dozen+ samples of each type
   (see the three QC dumps in the build transcript: the same logical
   content — marks number, grade, weight — appears at row 22 in one file,
   row 24 in another, row 28 in a third). Modelling this as small atomic
   fields ("bag count", "unit", etc.) would mean the system silently
   recomposes wording that was typed free-hand — exactly what requirement
   #8 ("never paraphrase, never guess") rules out. Instead these are three
   ordered, multi-line **text blocks** (marks/grade column, description-of-
   goods column, quantity column) that the operator reviews and edits as
   whole lines.

4. **The certification sentence at the foot of both templates is
   byte-for-byte identical, typos included, across every sample of its
   type** — including a real typo ("paticulars") and a formatting
   inconsistency between WC and QC (single space vs. double space before
   "are") that is preserved deliberately, not fixed. These live in
   `CertificateWording.java` as constants with no form field pointing at
   them — they cannot be edited through the UI at all.

5. **The "Declaration: -INVOICE NO. ... DT:..." line's exact punctuation
   varies by clerk** (period after invoice number or not, "DT:" vs "DT.",
   space or not) — checked across 35+ WC files. This is human inconsistency,
   not a rule, so it's pre-filled in the dominant format and left editable.

6. **COPROCAFE QC's Word template has its dynamic values split across
   oddly-broken runs** (the mark number is three separate runs:
   `"...202"`, `"6"`, `"/407"`, apparently from a previous hand-edit).
   `CoprocafeWordService` does not try to patch those exact run boundaries
   (fragile, and breaks for a different digit count) — it matches each
   dynamic paragraph by its fixed leading text, clears all its runs, and
   writes one new run with the complete sentence.

---

## 2. Architecture

Entirely new Java package, entirely new frontend folder, one new DB table.
**Zero changes to any existing billing service, entity, controller, or
route.** The only touches to existing files are additive-only:

- `App.tsx` — one nav entry + nested routes under `/wcqc` (navigation, as
  explicitly permitted by the brief).
- `application.yml` — added `spring.servlet.multipart.*` size limits
  (needed for the invoice-upload endpoint; billing has no multipart routes
  so this key did not exist and touches nothing else) and one logging
  namespace line.

**Package note:** the module lives at `com.ase.billing.wcqc`, not a
sibling `com.ase.wcqc`. This is a Spring Boot wiring requirement, not a
business-logic one — `AseBillingApplication`'s default component scan only
covers `com.ase.billing` and its sub-packages, so a sibling package
wouldn't be picked up without extra `@ComponentScan` config. Nothing in
`com.ase.billing.wcqc` imports or depends on anything in
`com.ase.billing.domain/service/web/repo` (the actual billing code) — the
separation the brief asked for is about business logic and workflow, and
that's fully honoured; only the Java package prefix is shared for Spring's
sake.

```
backend/src/main/java/com/ase/billing/wcqc/
  domain/                  Certificate, CertificateWording (fixed text), enums/
  repo/                    CertificateRepository
  service/                 InvoiceUploadService, CertificateService,
                           WcCertificateService, QcCertificateService,
                           CertificateHistoryService, CertificateGenerationService
  pdf/                     CertificatePdfService      (OpenPDF, hand-laid-out)
  excel/                   CertificateExcelService    (fills the real master .xlsx)
  word/                    CoprocafeWordService       (fills the real master .docx)
  web/                     CertificateController, CertificateMapper, dto/CertificateDtos

backend/src/main/resources/
  db/migration/V16__wc_qc_certificates.sql
  wcqc-templates/wc-master.xlsx           (real historical file, HC-WC 320 B 377)
  wcqc-templates/qc-master.xlsx           (real historical file, HC QC 318)
  wcqc-templates/coprocafe-qc-master.docx (real historical file, HC 407-2026 COPROCAFE)

frontend/src/
  types/wcqc.ts
  api/wcqcApi.ts                          (self-contained; does not touch api/client.ts)
  components/wcqc/UploadInvoice.tsx
  components/wcqc/CertificateFieldForm.tsx
  pages/wcqc/WcQcLayout.tsx               (own sub-nav: Generate WC / Generate QC / History)
  pages/wcqc/GenerateWc.tsx
  pages/wcqc/GenerateQc.tsx               (includes the COPROCAFE variant toggle)
  pages/wcqc/CertificateHistory.tsx
```

All API routes live under `/api/wcqc/**`, which `SecurityConfig`'s existing
`.requestMatchers("/api/**").authenticated()` already covers — no security
config change was needed.

---

## 3. How to install this

1. Drop `backend/src/main/java/com/ase/billing/wcqc/**` into the existing
   repo at the same path.
2. Drop `backend/src/main/resources/db/migration/V16__wc_qc_certificates.sql`
   and `backend/src/main/resources/wcqc-templates/**` in.
3. Replace `backend/src/main/resources/application.yml` with the one in
   this bundle (it's the existing file plus the multipart block and one
   logging line — diff it against your live copy before overwriting, since
   the session summary notes some files were hand-patched mid-session).
4. Drop `frontend/src/types/wcqc.ts`, `frontend/src/api/wcqcApi.ts`,
   `frontend/src/components/wcqc/**`, `frontend/src/pages/wcqc/**` in.
5. Replace `frontend/src/App.tsx` with the one in this bundle (again: diff
   first, it's the existing file plus the nav entry and nested routes).
6. Replace `backend/src/main/java/com/ase/billing/web/SpaController.java`
   with the one in this bundle (existing file plus the `/wcqc/**` and
   `/quotation` deep-link patterns — see §9).
7. `mvn generate-resources` (or your normal build) picks up the new
   migration automatically via Flyway.
8. Drop `backend/src/test/java/com/ase/billing/wcqc/**` and
   `backend/src/test/resources/wcqc-fixtures/**` in if you want the unit
   tests (§10) — optional, but `mvn test` will pick them up automatically
   once they're there.

No changes needed to `SecurityConfig`, `pom.xml` (OpenPDF and POI are
already dependencies and already cover PDF text extraction and XWPF Word
support at the pinned versions), or any billing file beyond the three noted
above.

---

## 4. Scope decisions and what's a first cut

- **PDF export** is a hand-laid-out OpenPDF rendering (same approach
  `InvoicePdfService` already uses for the billing PDF) — faithful to the
  wording and section order, not a pixel copy of any one historical file's
  row layout. **Excel and Word exports are pixel-faithful**, because they
  fill the actual historical files rather than building a sheet/document
  from scratch.
- **Templates covered:** Standard WC, Standard QC, COPROCAFE QC (Word).
  Every other customer name seen in `qc.zip`/`wc.zip` (EFICO, NKG, BIJDENDIJK,
  TOUTON, NV GROUP SOPEX, etc.) uses the same Standard template with
  different consignee/route/body text — no second real template family
  turned up in repeated, consistent use. If one shows up later, add a new
  `CertificateVariant` rather than bending STANDARD.
- **Invoice upload extraction** is best-effort: confident on invoice
  number/date and the shipment mark (pattern-matched, works for both Excel
  and PDF), reasonable on the consignee block, honest about what it can't
  find (ports/destination from a PDF, in particular) — those show up as
  "Required information missing — please enter manually" rather than a
  guess. This is a first cut; if a particular customer's invoices have a
  very consistent PDF layout, `InvoiceUploadService` is the one place to
  make extraction more specific for that layout.
- **Regenerate** (History screen) re-runs the PDF export to refresh
  `generatedAt`/`generatedBy`, then the operator downloads whichever format
  they need. It does not fork a new certificate row.
- **Not built:** an admin screen for editing `CertificateWording`'s fixed
  text (deliberately code-only, per requirement #8's insistence on
  controlled wording) — changing it means a deliberate code change and
  redeploy, not a form.

---

## 5. Cross-checked against ASE_BILLING_SESSION_SUMMARY.md

After the first cut was built, it was checked line-by-line against the
session summary's described stack, conventions, and file layout. Result:

**Confirmed consistent, no action needed:**
- Stack: Spring Boot 3.3.4 / Java 21 / MySQL / Flyway / OpenPDF / Apache POI
  — all reused at the pinned `pom.xml` versions, nothing added. Apache
  POI's Word support (`XWPFDocument`/`XWPFParagraph`/`XWPFRun`, used by
  `CoprocafeWordService`) is covered by `poi-ooxml`'s existing transitive
  `poi-ooxml-lite` — no new dependency needed.
- `RequestLoggingFilter` (bug #7) matches on any path starting with
  `/api/`, so `/api/wcqc/**` is already logged to `logs/ase-billing.log`
  with no changes to that filter.
- Migration numbering continues cleanly at `V16` after the existing `V15`.
- DTO/record naming (`...View`, `...Request`) and entity style
  (`@Getter @Setter` at class level) match the existing convention.
- No PowerShell-unfriendly instructions in this guide's install steps.

**Two real gaps found and fixed:**
- `ApiExceptionHandler` (billing's global `@RestControllerAdvice`) maps
  `IllegalStateException` to a clean 422, but has no handler for
  `NoSuchElementException` or `IllegalArgumentException` — both would have
  fallen through to its catch-all and returned a bare 500 ("Something went
  wrong on the server") for what are actually ordinary outcomes here (a bad
  certificate id, an unsupported upload file type). Fixed by adding
  `web/CertificateExceptionHandler.java`, a `@RestControllerAdvice` scoped
  to `com.ase.billing.wcqc.web` only — it doesn't duplicate or touch
  anything the global handler already does correctly, and doesn't import
  billing's own `NotFoundException`/`ValidationException` classes, so the
  module's independence from billing's code is unaffected.
- `application.yml` had a redundant explicit logging line for
  `com.ase.billing.wcqc` — removed, since it's a sub-package of
  `com.ase.billing` and inherits that level already.

**Two deliberate divergences, disclosed rather than silently left:**
- The WC/QC "Exporter" block is hardcoded in `CertificateWording` as
  `HANGAL COFFEE EXPORTING PRIVATE LIMITED` with one specific address
  spelling — copied character-for-character from the real WC/QC historical
  files. Billing's own `customers` seed row for the same company (code
  `HC`) spells it slightly differently (`PVT LTD`, `N0` vs `NO`, `AND` vs
  `&`, `KUVEMPUNAGARA` vs `KUVEMPUNAGAR`). This is not a bug — the two
  documents are historically inconsistent with each other, and the
  certificate must match the certificate's own historical wording, not the
  invoice's customer master record. Worth a human sanity check regardless.
- The exporter block is a hardcoded constant, not pulled from billing's
  `CompanySettings` (which is Aprameya Shipping Enterprises, the *agent* —
  not Hangal Coffee, the *exporter* — so it wouldn't have been the right
  source anyway; noted here so nobody wonders why
  `CompanySettingsRepository` isn't used).

## 6. Startup bug found on first real run — fixed

First boot against a real MySQL database failed at Hibernate schema
validation:

```
Schema-validation: wrong column type encountered in column [consignee_notify_block]
in table [certificates]; found [text (Types#LONGVARCHAR)], but expecting [tinytext (Types#CLOB)]
```

Cause: `Certificate`'s five multi-line text fields (`consigneeNotifyBlock`,
`marksColumnText`, `descriptionColumnText`, `quantityColumnText`,
`summaryStatementLines`) were annotated `@Lob` with no explicit `length`.
Hibernate 6 infers a MySQL column's expected DDL type from the mapped
`length` when `@Lob` is used without `columnDefinition` — with no length
given, it defaults to JPA's standard 255 and expects `TINYTEXT`, which
doesn't match the `TEXT` columns the Flyway migration actually creates.
`ddl-auto: validate` (never `update`) means this mismatch fails hard at
startup instead of silently doing the wrong thing, which is exactly what
that setting is for — but it should never have mismatched in the first
place.

The fix was already sitting in the existing codebase's own convention:
`CompanySettings.bankBranchLines` (also a free-form multi-line text column)
uses `@Column(columnDefinition = "TEXT")` with **no** `@Lob` at all. All
five fields now follow that same pattern. No migration change was needed —
the DB schema was already correct; only the entity mapping was wrong.

## 7. Real invoice feedback (round 2) — extraction rewrite + combo workflow

A real invoice (`HC_358-INV-_SUCAFINA-JULY_2026.xlsx`) plus its actual issued
WC certificate (`WC-14_1850_2026_358.xlsx`, used as the correctness
reference) surfaced two kinds of gaps: fixed-coordinate extraction breaking
on a real layout variant, and a request to generate WC+QC together instead
of one at a time.

**Root cause of the extraction failures:** this invoice has an extra
"Vessel Name & Voy no." row that doesn't exist in the first sample studied,
shifting every row below it down by one. That silently broke the
fixed-coordinate reads for Port of Discharge, Final Destination and Country
of Final Destination — which is exactly why those had to be typed in by
hand. `InvoiceUploadService` no longer reads fixed coordinates for any
route field: it searches for each field's own printed label wherever it
sits, then checks (in order) the same cell after the label, the cell
directly below, then the next non-blank cell to the right. Verified against
the real file — every one of the seven route fields now resolves correctly,
including Port of Discharge and Final Destination, which the invoice prints
as the *identical* full string; ASE's own historical certificate shortens
Final Destination to just the city in that situation, so that specific
case (raw values equal → keep the full text for Port of Discharge, shorten
Final Destination to the part before the first comma) is handled
explicitly, not guessed.

**Consignee/Notify/Buyer:** the invoice's three party blocks (a compressed
"CONSIGNEE :" line, a fuller "NOTIFY :" block, and a separate "Buyer (if
other than consignee)" block) are now each parsed with their label
stripped, metadata lines (EORI, GSTIN, etc.) dropped, and — when the last
address line has a short trailing segment after a comma (the country) —
that segment split onto its own line, matching the exact format given as
an example (`SUCAFINA SA` / `1 PLACE DE ST GERVAIS, CP 5425` / `1211,
GENEVA,` / `SWITZERLAND`). Verified character-for-character against this
invoice. Since which party actually belongs on the certificate varies by
shipment (this shipment's real certificate used the Notify party; another
might use the Buyer/Consignee party instead), the upload result returns
both, and the operator picks with one click rather than the system
guessing.

**Marks/description/quantity body + weight breakdown:** now extracted from
the invoice's own "Marks & Nos" / "Description of Goods" section (the
FRONT SIDE/BACK SIDE bag-marking block, HS code, contract number, etc.),
and — for WC — the three "Total Gross/Nett/Tare Weight..." sentences are
composed from the invoice's own stated totals and bag count (tare and
per-bag gross are arithmetic from those figures, never assumed), skipped
entirely for bulk shipments or when a needed figure is missing. Checked
against the real invoice: the composed sentences matched the actual issued
certificate's wording exactly.

**Combo workflow:** a new "Generate Certificates" page (now the default
landing for WC & QC) uploads one invoice, shows one shared review form, and
creates a WC and a QC certificate together via a new `POST
/api/wcqc/certificates/combo` endpoint — two ordinary `Certificate` rows
sharing the same input, not a merged entity, so History/editing/exports all
keep working exactly as before. "Generate WC only" / "Generate QC only"
remain available (e.g. from History's Preview action, for touching up one
certificate on its own).

## 8. Deep-link routing bug found from the server log — fixed

The log showed:

```
org.springframework.web.servlet.resource.NoResourceFoundException: No static resource wcqc/generate-wc.
```

Cause: the SPA's deep-link forwarding (`SpaController`) uses a hand-maintained
allowlist of paths that get forwarded to `index.html` so React Router can
take over — anything not on that list falls through to Spring's static
resource handler and 404s. The list had `/invoices`, `/shipments/**`,
`/soa`, but not `/wcqc/**`, so refreshing or deep-linking into any WC & QC
page (rather than navigating there by clicking, which never leaves the
already-loaded SPA) hit exactly this. Nothing was actually broken in the
module — the page works fine on a normal click-through — but a bookmark, a
shared link, or a plain refresh on `/wcqc/generate-wc?id=1` would 404.

Fixed by adding `"/wcqc", "/wcqc/{path:[^.]*}"` to `SpaController`'s
mapping, matching the same pattern already used for `/invoices/{path:...}`.
While in this file: **`/quotation` was also missing from the same
allowlist**, a pre-existing gap unrelated to this module (the Quotation
page has existed since before WC & QC) — refreshing on `/quotation` would
have hit the same 404. Added it alongside the WC & QC fix since it's the
same one-line pattern in the same file, but flagging it separately here
since it isn't a WC & QC bug.

The repeated `No static resource favicon.ico` entries in the same log are
unrelated and harmless (the browser auto-requesting a favicon that isn't
registered) — pre-existing, not a WC & QC concern, not touched.

## 9. Real PDF output feedback (round 3) — layout bug, missing quality statement, Final Destination correction

A generated WC PDF, compared against the actual issued certificate and
against printing the Excel export directly to PDF (which was confirmed
correct), turned up a genuine rendering bug plus two data gaps.

**The PDF's content was appearing in the wrong order** — title, then the
declaration line, the certification quote and the signature, THEN the
exporter/consignee/route/body content that should come first. Root cause:
`CertificatePdfService` mixed plain `Paragraph` elements (title,
declaration, quote, signature) with `PdfPTable` elements (everything else)
in one document. OpenPDF/iText writes these two element types to the page's
content stream through different internal code paths, so mixing them does
not reliably preserve the order they were added in — the Paragraphs ended
up grouped together ahead of every table, regardless of call order. The
existing `InvoicePdfService` (the working billing PDF) never hits this
because it's built entirely out of tables and never adds a bare Paragraph.
`CertificatePdfService` now follows the same rule: title, declaration line,
the certification quote and the signature are each wrapped in a one-cell
table instead of added as raw Paragraphs, so every piece of content is the
same element type throughout.

**QC's quality statement was printing blank** because nothing ever
suggested one — the extractor only ever composed a suggestion for WC's
weight breakdown. Added `suggestQualityStatement()`, which composes a
suggestion from the marks column's own grade/origin lines (e.g. "INDIA /
ROBUSTA COFFEE" / "CHERRY AA,"), dropping administrative lines (FRONT SIDE,
ICO number, net weight, bag range, BL number) — reusing text the invoice
already states, not inventing wording. Wired into both the combo page and
the standalone Generate QC page, which previously didn't map this field
from the upload result at all.

**Final Destination correction:** the previous version shortened Final
Destination to just the city when it exactly matched Port of Discharge's
full text, based on one historical certificate. On direct instruction,
that shortening is removed — Final Destination is now always the invoice's
own stated value, verbatim. The operator can still shorten it by hand for
a specific certificate if wanted.

**Also fixed while rewriting the PDF layout:** the quantity column was
showing Country of Final Destination stuffed awkwardly above the quantity
figure — leftover from before Country of Final Destination had its own
field. The real templates print that country on the SAME row as the "Marks
& Nos"/"Description of Goods" column headers, not down in the content
area, so it now sits in the header row alongside its own label, and the
quantity cell shows only "QUANTITY"/"IN MT" plus the actual figure,
matching the template. Font sizes were also bumped for readability (body
text 9pt → 10pt) per the request to take font/size into account — though
note the PDF remains a hand-built approximation of the layout, while the
Excel export stays the pixel-exact one (it fills the real historical
file).

**Also brought up to date while in here — the standalone Generate WC/
Generate QC pages predated the label-scanning extraction rewrite** and
were still only mapping a handful of fields from an upload (marks, invoice
number/date, consignee, ports, declaration) — missing preCarriageBy,
placeOfReceipt, countryOfOrigin, portOfLoading, and the marks/description/
quantity body entirely. Both now map everything the combo page does.

## 10. Real invoice feedback (round 4) — packaging-specific wording, header-wrap bug, unit tests

A ~50-file matched set of real invoice/WC/QC triples (every packaging type
ASE ships: jute bags, big bags on pallets, bulk bags, single- and
multi-unit) made it possible to actually test the extraction logic
properly instead of against one or two samples, and turned up a real bug
plus a genuine template gap.

**"Container No." was leaking into the marks column as if it were body
content.** Some invoices' "Marks & Nos" column header wraps onto a second
row ("Marks & Nos/ No. " then "Container No." directly below, in the same
column real content also uses — confirmed on invoice 369). Reading content
from headerRow+1 unconditionally treated that second header line as the
first data line. Fixed by tracking the *"Description of Goods" header's
own column* — which is never reused for real content, only for the header
itself — and skipping rows for as long as that column keeps producing
text; once it's blank, real content begins. Verified this doesn't regress
the earlier (non-wrapping) invoices, where the check is simply a no-op.

**The weight-breakdown suggestion only ever knew jute-bag wording**, and
would try to apply it to big-bag and bulk shipments too, rather than
skipping (bulk) or producing wrong wording (big-bag). Cross-checking
multiple real WC certificates per packaging type turned up three genuinely
different templates:

| Packaging | Lines | Distinguishing wording |
|---|---|---|
| Jute bags (320/640/960 B) | 3 | "...jute bag..." |
| Bulk bags (1/2/3/6/7 BULK) | 3 | "...BULK BAGS..." |
| Big bags on pallets (20-100 BB) | 4 | "...BIG BAGS COFFEE...", plus a pallets line the other two don't have |

Per-bag tare and gross stay arithmetic (never assumed) in all three; only
the wording and line count differ. One of the uploaded "real" WC
references for a big-bag shipment (369) turned out to still have jute-bag
wording left over from a previous certificate — a copy-paste error in that
one document, not a valid fourth pattern, so it wasn't used as evidence
for the template (this is called out explicitly in
`InvoiceUploadService`'s own doc comment, so a future reader doesn't
reintroduce it thinking it's a real historical variant).

**Also found while re-deriving these templates against real bytes:**
invoice 402's own per-unit weight line ("NETT WT:21600.00") has no "KGS"
unit suffix at all — a stricter regex requiring one silently failed to
read it, so the whole weight suggestion would come back empty for that
invoice. Both `NET_WEIGHT_EACH` and `GROSS_WEIGHT_EACH` now treat the unit
suffix as optional.

**Font/size uniformity** (also raised this round) was already addressed in
round 3 — labels, body text and the certification quote are now all the
same 10pt Helvetica family, only the title is larger. Re-confirmed here
rather than re-done.

**Signature/underscore lines shown in a screenshot this round** — not yet
implemented. The reference image showed blank underscore lines near a
signature area; without a clearer view of exactly which document and
position that referred to, adding something in the wrong place would be
worse than leaving it for a follow-up with a specific example to match
against.

**Unit tests, as requested:** `InvoiceUploadServiceTest` (under
`backend/src/test/java/com/ase/billing/wcqc/service/`) exercises all three
packaging templates, the header-wrap regression, the missing-KGS-suffix
case, and the QC quality-statement suggestion — each against a real
invoice fixture (`backend/src/test/resources/wcqc-fixtures/`), not a
synthetic file built to make the test pass. `InvoiceUploadService` takes
no collaborators, so these run as plain JUnit 5 tests with no Spring
context. Run with the project's normal `mvn test`.

## 11. Real invoice feedback (round 5) — the header-wrap fix from round 4 was itself too eager, plus three more real-world formats

Two more invoices (EFICO/KAREN TRADE and NKG COPROCAFE) surfaced a real
regression in round 4's own header-wrap fix, plus three unrelated format
variants. All confirmed by faithfully simulating the exact Java logic in
Python against the actual files before touching code — not by inspection
alone.

**The header-wrap fix from round 4 was too eager and broke a different
invoice.** That fix treated "the Description of Goods header's own column
stays non-blank" as "still wrapping, keep skipping" — which assumed real
content always lives one column over from the header. On invoice 397, real
description content sits directly under its own header column instead
(no offset column at all), so the old check kept advancing straight past
the entire body and picked up unrelated freight/payment text from much
further down the sheet. Replaced with a far more conservative rule: skip
at most ONE row, and only when that row's text is short, lowercase-only,
letters/spaces/punctuation only — "of pkges" and "kind of pkges" both
qualify; "640 BAGS OF ..." (real content, has digits/uppercase/quotes)
does not. Verified this doesn't regress invoice 369 (the case it was
originally written for) or introduce a new failure on 397 or 405 (which
has both its marks header AND its description header wrap the same way —
the case that would have most exposed a fragile fix).

**Description content column now detected per invoice, not assumed.**
Some invoices put it one column over from marks (most samples); invoice
397 puts it directly under its own header column, with the column in
between left entirely empty. The extractor now probes the first content
row to see which column actually has text, rather than assuming.

**Quantity sometimes stored as a raw Excel number, not text.** Invoice
397's quantity cell holds a bare number (38.4) with "MT" only in its
display format — invisible to a data-only read. Text-only matching skipped
past it and picked up a "TOTAL QTY:38.4MT" fallback line much further down
the sheet. A positive number found in the quantity column within the body
area is now used directly, formatted as "{value}MT".

**NOTIFY's hyphen-after-colon wasn't stripped.** "NOTIFY 1:- KAREN TRADE
LTD" — CONSIGNEE's pattern already accounted for a hyphen appearing after
the colon; NOTIFY's didn't, leaving a stray leading "-" on the extracted
block. Fixed to match.

**A party's address can continue into an adjacent column on a row that
isn't the anchor row.** Invoice 397's NOTIFY-1 has an ID code in column A
on one row and its actual street address in column B on that same row —
not the anchor row, which is the only place the existing same-row scan
looked. Extended to check one column over on every continuation row, not
just the first — confirmed safe against invoice 358 (where the risk of a
wider per-row scan was originally found: unrelated STATE/GSTIN cells sit
three columns over there, safely out of reach of a one-column check).

**Invoice number/date label variant.** "ES/HC/2026/199. DATE.25.08.2026"
spells the label "DATE" instead of "DT", and separates the date with dots
instead of slashes. Both accepted now.

**Per-bag net weight embedded mid-sentence with no label word at all.**
"PACKED IN JUTE BAG WG NETT60.00KGS" — no "WEIGHT" or "WT" between "NETT"
and the number. The middle word is now optional in both the net- and
gross-per-unit patterns.

Both invoices are now permanent test fixtures
(`invoice-397-efico-karentrade-640-jutebag.xlsx`,
`invoice-405-nkg-coprocafe-320-jutebag.xlsx`), with six new tests covering
every fix above plus the specific regression this round found.

## 12. Full 24-invoice validation pass (round 6) — every sample invoice checked, not just the ones that were reported

Rather than continuing to fix invoices one screenshot at a time, this round
ran the exact extraction logic (faithfully ported to a throwaway Python
harness, checked line-by-line against the real Java before trusting its
output) against **all 24 invoices** in the sample set at once, checking
every field on every file. Starting point: 5 of 24 had at least one missing
field, and 2 more had a silent formatting artifact that wasn't being
flagged as missing at all. Four more real bugs came out of this, one of
which was a regression in round 5's own fix:

**The header-wrap detector still had a blind spot.** Round 5 made it
conservative by requiring the *description* side to show a short,
lowercase continuation fragment before skipping a row. But four invoices
(403, 416, and both GROUP SOPEX pairs) wrap *only* the marks side
("Container No.") — their description header never wraps at all, so the
description-side signal never fires and the check never triggers,
leaving "Container No." in the marks column and the description column
completely empty. Added a second, independent signal for the marks side:
short text ending in "No." (distinguishes "Container No." from real
content like "INDIA ARABICA COFFEE" or "BL NO.CMD0180331", neither of
which ends that way). The row is now skipped if *either* side's own
signal fires — verified this doesn't reopen the failure mode round 5 fixed
(invoice 397, where description content sits directly under its own
header and must not be skipped).

**CONSIGNEE/NOTIFY combined with "/" instead of "&".** "CONSGINEE
/NOTIFY:-" and "CONSGINEE/NOTIFY :" (both real, on different invoices) —
the pattern only recognized "&" as the combiner, so "/NOTIFY:-" or
similar leaked onto the front of the extracted address. Both separators
accepted now.

**Comma instead of period before the date label.** "ES/HC/2027/202 ,
DT:25/08/2026" — the gap between the invoice number and "DT"/"DATE" is
now any mix of comma, period, and whitespace, not just an optional single
period.

**One known, documented gap, not fixed:** the D.R. Wakefield-brokered
invoice (413-414, a combined two-shipment invoice) puts its quantity
figures in a completely different column position than every other
sample, and is structured as two shipments side by side ("SIDE-1"/
"SIDE-2") rather than one — genuinely different enough that forcing a fix
risked being confidently wrong rather than usefully right. Quantity is
left for manual entry on this one; everything else on it (mark, invoice
number/date, consignee, route) resolves correctly.

**Result: 23 of 24 invoices now resolve every field**, the one exception
being the single documented quantity-column gap above. Three of the newly
fixed invoices are now permanent test fixtures (invoice-403, -407, -400),
with three new tests — 20 total.

## 13. Testing checklist

**Billing** (verify nothing changed): existing invoice CRUD, PDF, PSC
statement, Quotation page, GST sales export, invoice numbering — all
untouched, since no billing file's logic changed.

**WC & QC:**
- [ ] Upload an invoice (xlsx) from `wtqt.zip`/`wtqt2707.zip` → confirms
      mark number, invoice number/date extract; missing fields show the
      manual-entry warning.
- [ ] Upload a PDF invoice → same, with ports/destination expected to need
      manual entry.
- [ ] Generate WC (Standard) → PDF, Excel.
- [ ] Generate QC (Standard) → PDF, Excel.
- [ ] Generate QC (COPROCAFE) → PDF, Excel, **Word** — verify the Word
      export's fixed sentences read back exactly as in
      `HC 407-2026 COPROCAFE - QC.docx` / `HC 225 - APR 2026 COPROCAFE - QC.docx`,
      with only bag count / total kg / variety / marks / moisture changed.
- [ ] Leave a COPROCAFE field blank → Word export refuses with the missing
      field named, not a guess.
- [ ] Certificate History: filter by type, search by mark/invoice number,
      Preview (reopens the Generate page pre-filled), Regenerate.
- [ ] Confirm `logs/ase-billing.log` shows `com.ase.billing.wcqc` entries
      for upload/create/update/generate.
