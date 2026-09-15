# The standard CNF charge sheet

Every CNF bill starts with these fourteen charges already filled in, in this
order. Remove any that don't apply to a shipment; add anything extra below them.

Taken from `ASE HC 344-CNF BILL 516-2026 1X20 wqo.docx`, which totals **19,950**
before GST for a 1X20 shipment.

---

## What a new CNF bill looks like before you touch it

```
Particulars                                                      Amount
HC INV NO: -ES/HC/2027/149 & ICO mark NO.14/1850/2026/344 - 1X20  In Rs.
Hsn code.996713

 1. Pre shipment export documentation charges                    3000/-
 2. ICO/Permit, ROC Submission & Self-sealing Documentation       1000/-
 3. Expenses on Phytosanitary certificate                         3000/-
 4. Certificate of weight & quality                               1500/-
 5. Certificate of origin                                         1750/-
 6. JSW containers and Seal Scanning Charges                       800/-
 7. EDI charges                                                   1000/-
 8. Empty Container survey fee                                     400/-
 9. Customs Clearance charges                                     1750/-
10. Port Expenses                                                 1250/-
11. VGM expenses                                                   750/-
12. LO/LO                                                          750/-
13. CHA Service charges                                           2500/-
14. Post Shipment Document charges                                 500/-

BANK DETAILS: APRAMEYA SHIPPING ENTERPRISES              TOTAL   Rs 19950.00
STATE BANK OF INDIA                                      SGST 9% Rs.1795.50
CA. A/C.No.40705094297                                   CGST9% Rs. 1795.50
IFSC CODE: SBIN0018237                                          RS.23541.00
YELAHANKA 4TH PHASE BRANCH
#722, CHS 707 SCHEME
4TH PHASE MAIN ROAD
YELAHANKA NEW TOWN
BANGALORE-560064

Amount in words: Twenty-three thousand          G- Total.RS.23541/-
five hundred and forty-one only
```

---

## How each line is priced

Amounts are never typed. Each line is **quantity × rate**, and the rate comes
from the rate master.

| # | Charge | Type | Rate | 1X20 | 3X20 |
|---|---|---|---|---:|---:|
| 1 | Pre shipment export documentation charges | flat | 3,000 | 3,000 | 3,000 |
| 2 | ICO/Permit, ROC Submission & Self-sealing Documentation | per TEU + base | 500 + base 1,000 † | 1,500 | 2,500 |
| 3 | Expenses on Phytosanitary certificate | per TEU + base | 750 + base 2,250 | 3,000 | 4,500 |
| 4 | Certificate of weight & quality | per TEU + base | 750 + base 750 | 1,500 | 3,000 |
| 5 | Certificate of origin | flat | 1,750 | 1,750 | 1,750 |
| 6 | JSW containers and Seal Scanning Charges | per TEU | 800 | 800 | 2,400 |
| 7 | EDI charges | flat | 1,000 | 1,000 | 1,000 |
| 8 | Empty Container survey fee | flat | 400 | 400 | 400 |
| 9 | Customs Clearance charges | per TEU | 1,750 | 1,750 | 5,250 |
| 10 | Port Expenses | per TEU | 1,250 | 1,250 | 3,750 |
| 11 | VGM expenses | per TEU | 750 | 750 | 2,250 |
| 12 | LO/LO | per TEU | 750 | 750 | 2,250 |
| 13 | CHA Service charges | per TEU | 2,500 | 2,500 | 7,500 |
| 14 | Post Shipment Document charges | flat | 500 | 500 | 500 |
|  | **Taxable** |  |  | **19,950** | **39,550** |

**Flat** charges bill once per shipment whatever the container count.
**Per TEU** charges multiply by the shipment's TEU, so changing 1X20 to 3X20
re-prices lines 6, 9, 10, 11, 12 and 13 on their own. A 40ft container counts as
2 TEU.

**Per TEU + base** is the same, plus a fixed amount added once. Lines 2-4 are
not pure rate × TEU on the real bills — ICO/Permit runs 1,000 / 1,500 / 2,000 /
4,000 for 1 / 2 / 3 / 7 containers (`500 + 500 × TEU`, not `500 × TEU`, which
would under-bill every shipment past one container). Read straight off
`437-480 ASE HC bills.pdf`:

| Containers | ICO/Permit | Phytosanitary cert | Weight & quality cert |
|---:|---:|---:|---:|
| 1 | 1,000 | 3,000 | 1,500 |
| 2 | 1,500 | 3,750 | 2,250 |
| 3 | 2,000 | 4,500 | — |
| 7 | 4,000 | 7,500 | 6,000 |

A few 2-container bills show a higher phytosanitary figure (4,500 or 5,250)
when the line reads "...with addl. declaration" — that's an extra document
needed for that particular shipment, not a change to the formula. Bill it with
the separate **Phytosanitary additional declaration** line under "Charges in
the master but not on the standard sheet" below.

† **ICO/Permit's base changed from 500 to 1,000** (customer confirmed: "the
first TEU is 1500 and after that it is 500 more each container", effective
the date of V14). Every bill before that date — including bill 516 above and
the 1,000/1,500/2,000/4,000 figures in the table above it — was correctly
billed at the old 500 base and keeps that figure; only invoices created after
V14 use the new 1,000 base. Update this table's worked example, not the
migration, if the rate moves again.

---

## Changing things

**Remove a line that doesn't apply** — the ✕ on its row. Line numbers close up,
so the printed bill always runs 1..n with no gaps.

**Change a rate** — type over it. A changed rate on a service line is kept as the
new default from that invoice date on, so the next bill already has it. A green
**keep for next time** tick appears under the box; untick it to make the change
apply to this bill only.

The old rate is closed off with an end date rather than replaced:

```
VGM expenses   01-04-2026 → 12-09-2026   750    (master)
VGM expenses   13-09-2026 → open         500    (set on ASE/HC/CNF/518 by admin)
```

Every invoice also keeps its own copy of what it was billed at, so a bill issued
in August still reads 750 whatever the master says afterwards. Changing a rate
can never reach back into a bill that has already gone out.

**Add a charge that isn't standard** — *+ Add particular*, then pick from the
service list. The full CNF list has 31 charges; these fourteen are the ones
marked standard.

**Start over** — *Reset to standard 14 charges* puts the sheet back. *Clear all*
empties it if a bill is nothing like the usual.

---

## Charges in the master but not on the standard sheet

These are on the list, priced, and one click away — they just aren't on every
bill:

```
Fumigation charges              per TEU   6,000
Halting charges                 per TEU per day  2,000
Phytosanitary additional declaration
Health Certificate
Reissue of Health Certificate as per INSTR.
Moisture Certificate
NON-GMO Certificate
REX/GSP
Seal Charges                    per TEU   1,106.50
Dry Air Bags                    per TEU     700
Handling Charges
BlueDart Charges
Survey fee
Open Examination Charges
Coffee Stuffing Supervision and Photograph charges
BL Charges
BL Release at BLR
```

---

## The transport bill alongside it

The same shipment produces a T bill with the next number. Its charges come from
the route table, priced per container:

```
Particulars                                                      Amount
HC INV NO: -ES/HC/2027/149 & ICO mark NO.14/1850/2026/344 - 1X20  in Rs.
Hsn code.996713

1. Transportation Charges from Mangalore to KUSHALNAGAR and back  27000/-
2. Addnl. Charges for Transportation via Hassan                    4000/-

BANK DETAILS: APRAMEYA SHIPPING ENTERPRISES              TOTAL   Rs 31000.00
STATE BANK OF INDIA                                      SGST 9% Rs.2790.00
...                                                      CGST9% Rs. 2790.00
                                                                RS.36580.00

Amount in words: Thirty-six thousand             G- Total.RS.36580/-
five hundred and eighty only
```

| Route | Per container |
|---|---:|
| Mangalore ↔ Kushalnagar | 27,000 |
| Mangalore ↔ Somwarpet | 29,750 |
| Mangalore ↔ Baikampady | 12,000 |
| NMPA ↔ Baikampady | 12,000 |
| Movement via Hassan | 4,000 per TEU |

A T bill can carry more than one route — bill T/476 sends one container to
Somwarpet and six to Kushalnagar on one document, each leg annotated with its own
container split.

---

## The invoice number

The next number is shown before the bill is created, top right of the shipment
card:

```
Next number  ASE/HC/CNF/518/2026-27   change      (highest issued: 517)
```

It carries on from the last bill on its own. **change** lets you skip ahead — for
a number already written in the paper book, or a block reserved for something
else. It only moves forward: a number that has been issued is never handed out
twice, and the server refuses to go back.

The number is per customer and per financial year, and is shared across
categories — which is why CNF/516 and T/517 come out consecutive.

---

## The sample (S) bill

A sample bill carries a **PSC statement** that prints as page 2, exactly like
ASE's "HC SAMPLE PSC DETAILS" sheets.

```
HC SAMPLE PSC DETAILS-AUG -2026 ASE bill no 438

CONSIGNEE                PSC  DT
Nuova Cipam srl          22-07-2026
Group Sopex              22-07-2026
Ken Gabbay Coffee        22-07-2026
...                      (17 rows)
```

**The row count is the set count.** Seventeen consignee rows means the bill reads
"17 SET PSC @RS2500/-" and "SERVICE CHARGES 17 SETS@RS.500/-". Add or remove a
consignee and the bill re-prices itself on save, so the statement and the invoice
can never disagree about how many sets were done.

**Consignee is a dropdown**, seeded from the Jul-26 and Aug-26 sheets:

```
Bernhard Rothfos     EFICO NV             Novadelta
Briz Coffee          Equatorial Traders   Nuova Cipam srl
DRWakefield          Frey Commodities     Sucafina UK Ltd.
Group Sopex          Hamburg Coffee       TORREFACÇÃO
Ken Gabbay Coffee    NKG Bero Italia      Touton SA
NV Group Sopex
```

*Group Sopex* and *NV Group Sopex* are both in ASE's own sheets, so both are
kept. Picking from the list is what stops one buyer being spelled three ways
across three months.

**A new buyer** — pick *+ Add a new consignee…* at the bottom of the dropdown,
type the name, and it is saved to the list and selected on that row. It is there
for every later month. Adding a name that already exists, in any capitalisation,
selects the existing one rather than creating a second entry.

### Taking an amount off

A sample bill can carry a deduction applied **after** GST, as bill ASE/HC/S/438
does with the DHL charges:

```
taxable        51,000.00
SGST 9%         4,590.00
CGST 9%         4,590.00
grand total    60,180
LESS DHL CHARGES A/C ASE-ACC   3,758
payable        56,422
```

The GST return still reports 60,180 — the deduction reduces what the customer
pays, not what was taxed.

---

## Deleting and reissuing a number

A draft deletes with one click and its number frees up straight away.

A **finalised** bill can also be deleted, but it asks for a reason first, and the
deletion is written to the audit log with the invoice number, the amounts and who
did it. Reusing an issued GST number is a real compliance risk, so the record is
what makes the decision defensible if anyone asks.

The number comes back only when the deleted bill held the highest number. Delete
from the middle of the run and it leaves a gap — set the number explicitly on the
replacement to fill it.

---

## Working in batches

**Download every draft as one PDF** — the invoice list has a button. One file, the
bills in number order, the shape of ASE's own "437-480 ASE HC bills.pdf". Print
it, read through it, then come back and approve.

**Download issued bills by number** — type a from and to, e.g. 437 to 480.

**Approve several at once** — tick the drafts and press Approve. Each is checked
on its own, so one bad bill does not stop the rest; you get told which were
refused and why. Approving one at a time still works exactly as before.

---

## The signature

The proprietor's signature block from ASE's Word template is printed on every
generated bill, bottom right, exactly as it appears on the originals. It lives at
`backend/src/main/resources/signature/ase-signature.png` — replace that file to
change it, or turn it off with `show_signature` in company settings.
