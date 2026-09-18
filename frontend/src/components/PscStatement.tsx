import { useEffect, useState } from 'react';
import { masters } from '../api/endpoints';
import type { Annexure, Consignee, Invoice, InvoiceItemLine } from '../types';

/**
 * The PSC statement that prints as page 2 of a sample (S) bill.
 *
 * Reproduces ASE's own "HC SAMPLE PSC DETAILS" sheet: a title, a consignee and
 * PSC-date table, and a footer. The consignee is a dropdown from the master list
 * rather than free text, because the same buyers recur every month and typing
 * them by hand is how "Group Sopex" and "NV Group Sopex" turn into four
 * spellings across four statements.
 *
 * The row count is the set count billed. Seventeen rows means the invoice line
 * reads "17 SET PSC @RS 2500/-" — the server re-prices every per-set line from
 * this table, so the statement and the bill cannot disagree.
 */

interface Props {
  annexure: Annexure | null;
  invoiceNumber?: string;
  /** The bill's own line items — used to compute the footer totals. */
  items?: InvoiceItemLine[];
  /** The saved invoice — used for the tax figures once one exists. */
  invoice?: Invoice | null;
  readOnly?: boolean;
  onChange: (a: Annexure | null) => void;
}

export function blankStatement(invoiceNumber?: string): Annexure {
  const month = new Date().toLocaleString('en-GB', { month: 'short' }).toUpperCase();
  const year = String(new Date().getFullYear());
  return {
    title: `HC SAMPLE PSC DETAILS-${month} -${year} ASE bill no ${invoiceNumber ?? ''}`.trim(),
    col1Header: 'CONSIGNEE',
    col2Header: 'PSC  DT',
    footerText: null,
    drivesQuantity: true,
    rows: [],
  };
}

const ADD_NEW = '__add_new__';

export function PscStatement({ annexure, invoiceNumber, items = [], invoice, readOnly, onChange }: Props) {
  const [consignees, setConsignees] = useState<Consignee[]>([]);
  const [adding, setAdding] = useState(false);

  useEffect(() => {
    masters.consignees().then(setConsignees).catch(() => setConsignees([]));
  }, []);

  /**
   * Adds a buyer that isn't on the list yet and selects it on this row.
   * Saved to the master, so next month it is already in the dropdown.
   */
  async function addConsignee(rowIndex: number) {
    const name = window.prompt('New consignee name');
    if (!name || !name.trim()) return;
    setAdding(true);
    try {
      const created = await masters.addConsignee(name.trim());
      setConsignees((prev) =>
        prev.some((c) => c.id === created.id)
          ? prev
          : [...prev, created].sort((a, b) => a.name.localeCompare(b.name)));
      setRow(rowIndex, created.name, annexure?.rows[rowIndex]?.col2 ?? '');
    } catch {
      window.alert('That consignee could not be saved.');
    } finally {
      setAdding(false);
    }
  }

  if (!annexure) {
    return (
      <div className="card mb-3">
        <div className="card-body d-flex justify-content-between align-items-center flex-wrap gap-2">
          <div>
            <strong>PSC statement</strong>
            <div className="text-secondary small">
              The consignee list that prints as page 2 and sets how many sets are billed.
            </div>
          </div>
          {!readOnly && (
            <button type="button" className="btn btn-sm btn-outline-primary"
                    onClick={() => onChange(blankStatement(invoiceNumber))}>
              + Attach a PSC statement
            </button>
          )}
        </div>
      </div>
    );
  }

  function patch(p: Partial<Annexure>) {
    onChange({ ...annexure!, ...p });
  }

  function setRow(i: number, col1: string, col2: string | null) {
    patch({ rows: annexure!.rows.map((r, k) => (k === i ? { ...r, col1, col2 } : r)) });
  }

  function addRow() {
    patch({ rows: [...annexure!.rows, { col1: consignees[0]?.name ?? '', col2: '' }] });
  }

  // setRow is referenced by addConsignee above, which is declared before the
  // annexure null-guard, so it reads through the optional chain there.

  function removeRow(i: number) {
    patch({ rows: annexure!.rows.filter((_, k) => k !== i) });
  }

  /** 42500.00 -> "42500"; 1106.50 -> "1106.50" — ASE's own notation. */
  function noDp(n: number): string {
    const r = Math.round(n * 100) / 100;
    return Number.isInteger(r) ? String(r) : r.toFixed(2);
  }

  /**
   * Builds the footer block from the bill's own figures — the same "TOTAL 17
   * SET PSC @RS 2500/-=RS 42500" / "SERVICE CHARGES..." / "GST 18%RS.../-"
   * summary ASE always hand-types under the consignee list. Computed from the
   * saved invoice's actual items and tax amounts so it can never disagree with
   * the bill itself, the way a retyped figure could.
   */
  function fillTotals() {
    if (!invoice) return;
    const sets = annexure!.rows.length;
    const psc = items.find((i) => i.printedDescription.toUpperCase().includes('PHYTOSANITARY'));
    const service = items.find((i) => i.printedDescription.toUpperCase().includes('SERVICE CHARGE'));
    const lines: string[] = [];
    if (psc) lines.push(`TOTAL ${sets} SET PSC @RS${noDp(psc.rate ?? 0)}/-=RS${noDp(psc.amount)}`);
    if (service) lines.push(`SERVICE CHARGES ${sets} SETS@RS${noDp(service.rate ?? 0)}=RS${noDp(service.amount)}/-`);
    lines.push(`TOTAL RS${noDp(invoice.subtotal)}/-`);
    const inter = invoice.igstRate > 0;
    const gstRate = inter ? invoice.igstRate : invoice.cgstRate + invoice.sgstRate;
    const gstAmount = inter ? invoice.igstAmount : invoice.cgstAmount + invoice.sgstAmount;
    lines.push(`GST ${noDp(gstRate)}%RS${noDp(gstAmount)}/-`);
    lines.push(`TOTAL Rs${noDp(invoice.grandTotal)}/-`);
    patch({ footerText: lines.join('\n') });
  }

  const setCount = annexure.rows.length;

  return (
    <div className="card mb-3">
      <div className="card-header d-flex justify-content-between align-items-center flex-wrap gap-2">
        <strong>PSC statement — page 2 of the bill</strong>
        <div className="d-flex gap-2 align-items-center">
          <span className="badge text-bg-primary">{setCount} set{setCount === 1 ? '' : 's'}</span>
          {!readOnly && (
            <>
              <button type="button" className="btn btn-sm btn-outline-primary" onClick={addRow}>
                + Add consignee
              </button>
              <button type="button" className="btn btn-sm btn-outline-danger"
                      onClick={() => onChange(null)}>
                Remove statement
              </button>
            </>
          )}
        </div>
      </div>

      <div className="card-body pb-0">
        <div className="mb-3">
          <label className="form-label small" htmlFor="psc-title">Title</label>
          <input id="psc-title" className="form-control form-control-sm" value={annexure.title}
                 readOnly={readOnly}
                 placeholder="HC SAMPLE PSC DETAILS-AUG -2026 ASE bill no 438"
                 onChange={(e) => patch({ title: e.target.value })} />
        </div>

        {setCount > 0 && (
          <p className="small text-secondary">
            Every per-set line on the bill is priced at <strong>{setCount}</strong> sets from
            this table. Add or remove a consignee and the bill re-prices on save.
          </p>
        )}
      </div>

      <div className="table-responsive">
        <table className="table table-sm mb-0 align-middle">
          <thead className="table-light">
            <tr>
              <th style={{ width: '4%' }}>#</th>
              <th>{annexure.col1Header}</th>
              <th style={{ width: '22%' }}>{annexure.col2Header}</th>
              {!readOnly && <th style={{ width: '6%' }} />}
            </tr>
          </thead>
          <tbody>
            {annexure.rows.map((r, i) => (
              <tr key={i}>
                <td className="text-secondary">{i + 1}</td>
                <td>
                  <select
                    id={`psc-consignee-${i}`}
                    className="form-select form-select-sm"
                    value={r.col1}
                    disabled={readOnly || adding}
                    onChange={(e) => {
                      if (e.target.value === ADD_NEW) { addConsignee(i); return; }
                      setRow(i, e.target.value, r.col2);
                    }}
                  >
                    {/* A name already on the bill that is no longer in the master
                        list still has to show, so it is offered explicitly. */}
                    {!consignees.some((c) => c.name === r.col1) && r.col1 && (
                      <option value={r.col1}>{r.col1}</option>
                    )}
                    {consignees.map((c) => (
                      <option key={c.id} value={c.name}>{c.name}</option>
                    ))}
                    <option value={ADD_NEW}>+ Add a new consignee…</option>
                  </select>
                </td>
                <td>
                  <input
                    id={`psc-date-${i}`}
                    type="date"
                    className="form-control form-control-sm"
                    value={r.col2 ?? ''}
                    readOnly={readOnly}
                    onChange={(e) => setRow(i, r.col1, e.target.value)}
                  />
                </td>
                {!readOnly && (
                  <td className="text-end">
                    <button type="button" className="btn btn-sm btn-link text-danger px-1"
                            onClick={() => removeRow(i)} title="Remove">✕</button>
                  </td>
                )}
              </tr>
            ))}
            {setCount === 0 && (
              <tr>
                <td colSpan={4} className="text-center text-secondary py-3">
                  No consignees yet. Each row is one PSC set.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="card-body pt-3">
        <div className="d-flex justify-content-between align-items-center mb-1">
          <label className="form-label small mb-0" htmlFor="psc-footer">
            Footer — the hand-totalled summary printed under the consignee list
          </label>
          {!readOnly && (
            <button type="button" className="btn btn-sm btn-outline-primary" disabled={!invoice}
                    onClick={fillTotals}
                    title={invoice ? undefined : 'Save the draft first — the totals come from the saved bill'}>
              Fill in totals from this bill
            </button>
          )}
        </div>
        <textarea id="psc-footer" className="form-control form-control-sm font-monospace" rows={5}
                  readOnly={readOnly}
                  placeholder={'TOTAL 17 SET PSC @RS 2500/-=RS 42500\nSERVICE CHARGES 17 SETS@RS 500=8500/-\nTOTAL RS 51000/-\nGST 18%RS 9180/-\nTOTAL Rs 60180/-'}
                  value={annexure.footerText ?? ''}
                  onChange={(e) => patch({ footerText: e.target.value || null })} />
      </div>
    </div>
  );
}
