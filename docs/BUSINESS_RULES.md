# ASE billing — verified business rules

Every rule below is derived from the two bill PDFs (invoices 370–434 and 437–480) and the August 2026 SOA workbook, cross-checked against ASE's answers. Where a rule contradicts the original development prompt, the documents win.

---

## Invoice number

```
ASE / {customer code} / {category} / {running number} / {financial year}
ASE / HC        / CNF      / 439             / 2026-27
```

The running number is **shared across categories** within a customer and financial year. Every shipment bills as a consecutive CNF + T pair (CNF/439 + T/440, CNF/441 + T/442). Splitting the sequence per category would break this.

Financial year is derived from the invoice date, April to March. It is never typed. Four bills across the two PDFs carry a financial year that contradicts their own date.

## Categories

| Code | Meaning | Shipment | PDF layout |
|---|---|---|---|
| CNF | Clearing & forwarding | required | itemised |
| T | Transportation | required | transport |
| TA | Taxi hire | required | itemised |
| S | Service (PSC sets) | none | statement + annexure |
| CB | Export incentive claim | none | statement |

Per ASE: mainly CNF, T, S and TA. Every shipment gets a CNF and a T; some also get a TA.

## GST

Intra-state throughout: CGST 9% + SGST 9%. Computed on the taxable total and rounded once, not per line.

```
line amount     2 dp    (Seal Charges bill at 1,106.50 per TEU)
CGST, SGST      2 dp
grand total     0 dp
```

HSN `996713` on every invoice, including T and S. Eight of the historical bills omit it; that was an oversight, not a rule.

## Transportation

Priced per container, not per trip. Verified against every T bill in both PDFs.

| Route | Rate per container |
|---|---|
| Mangalore ↔ Kushalnagar | 27,000 |
| Mangalore ↔ Somwarpet | 29,750 |
| Mangalore ↔ Baikampady | 12,000 |
| NMPA ↔ Baikampady | 12,000 |
| Additional movement via Hassan | 4,000 per TEU |

Scaling confirmed: Kushalnagar at 27,000 / 54,000 / 81,000 / 108,000 / 162,000 for 1, 2, 3, 4 and 6 containers. Somwarpet at 29,750 / 59,500 / 89,250 for 1, 2 and 3. A T invoice can carry more than one route — bill T/476 bills 1 container to Somwarpet and 6 to Kushalnagar on one document, with each leg annotated by its own container split.

Bill T/448 charges 6,000 to Baikampady where T/446 charges 12,000. Treat 12,000 as the route rate and override on the line when it differs.

## Rates

Master data suggests, the invoice records. The rate in use is copied into `invoice_items.rate` at creation and a later master change never alters an issued bill. Bills CNF/400 and CNF/430 bill VGM at 750 and 500 per TEU in the same month.

Current VGM rate per ASE: **750 per TEU**.

Resolution order: manual override on the line, then customer rate, then global rate, most recent effective date wins.

## Calculation types

```
SIMPLE            quantity x rate
PER_TEU           TEU x rate                    fumigation, VGM, LO/LO, CHA, seal, dry air bags
PER_TEU_PER_DAY   TEU x days x rate             halting charges
PER_SET           sets x rate                   PSC sets, export incentive applications
MANUAL            typed amount                  flat documentation charges
```

## What prints

The paper bill has two body columns: **Particulars** and **Amount In Rs.** Quantity and rate are written inside the description text:

```
VGM expenses 3@RS750/TEU                                  2250/-
Expenses on Phytosanitary certificate addl @Rs.750        4500/-
CHA Service charges 3X20'                                 7500/-
```

So quantity, unit and rate are stored as structured fields for calculation, and a separate `printed_description` holds the line as it appears. The service master prefills it from a template; the user can edit it.

Unpriced sub-lines print beneath an item: trailer and container numbers on T bills, container-split annotations on multi-route T bills.

## Amount in words

Canonical style, sentence case with Indian numbering:

```
Amount in words: Sixty-two thousand four hundred and eighty-one only
```

57 of the historical bills use this style; 53 use an uppercase "AMOUNT IN WORDS RUPEES. … ONLY" variant, mostly on T bills. New bills will not match the uppercase ones character for character. ASE has accepted this.

## Post-tax deduction

Bill S/438 carries `LESS DHL CHARGES A/C ASE-ACC RS 3758/-` applied **after** GST:

```
taxable        51,000.00
CGST 9%         4,590.00
SGST 9%         4,590.00
grand total    60,180
less DHL        3,758
net payable    56,422
```

The SOA reports **60,180**, not 56,422. Kept as a single adjustment field rather than a deductions subsystem.

## SOA

Generated from the database, never maintained by hand. One customer, one layout.

Columns, in the order the supplied workbook uses:

```
A  DATE
B  INVOICE NO.
C  Total          <- invoice grand total
D  Taxable
E  CGST           <- note CGST precedes SGST
F  SGST
G  HC MARK NO     <- shipment ICO mark tail; blank for S and CB
H  (empty spacer)
I  CNF/DC/T/S     <- category code, derived from the invoice
```

Only `FINALIZED` invoices appear. A draft must never reach a GST return.

ICO marks can be ranges (`14/1850/2026/292-295`); the SOA carries the first value.

## Errors found in the source documents

Kept here so they aren't mistaken for rules, and so nobody reconciles against them.

**Bills:** nine carry the year 2025 instead of 2026; four carry the wrong financial year; CNF/372 and T/373 print different HC invoice numbers for the same shipment; four bills print `GST` where `SGST` is meant; CNF/400 and CNF/426 skip item 5 and CNF/459 numbers two items 8; CNF/459 prints a subtotal of 54,850 against items summing to 55,650, with GST correctly computed on 55,650.

**SOA:** row 11 (CNF/445) has CGST 2,736 against SGST 2,763; row 13 (CNF/447) has CGST 2,009.50 against SGST 2,209.50; row 37 (CNF/471) is 0.17 out; row 71 (RI/500) has a 90-rupee GST error; eleven RI rows fail total = taxable + CGST + SGST.

Seeding August 2026 from the old workbook would import all of these. Enter the invoices instead and let the system compute the totals; expect the new figures not to match the old file.
