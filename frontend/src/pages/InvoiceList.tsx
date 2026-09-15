import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { invoiceApi, masters, type InvoiceFilters } from '../api/endpoints';
import { ErrorAlert, Spinner } from '../components/Alert';
import { StatusBadge, money } from '../components/Money';
import type { Category, Customer, InvoiceSummary } from '../types';

export function InvoiceList() {
  const [rows, setRows] = useState<InvoiceSummary[]>([]);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [filters, setFilters] = useState<InvoiceFilters>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [range, setRange] = useState({ from: '', to: '' });

  const drafts = useMemo(() => rows.filter((r) => r.status === 'DRAFT'), [rows]);
  const selectedDrafts = useMemo(
    () => drafts.filter((r) => selected.has(r.id)), [drafts, selected]);

  function toggle(id: number) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  }

  function reload() {
    setLoading(true);
    setSelected(new Set());
    invoiceApi.list(filters).then(setRows).catch(setError).finally(() => setLoading(false));
  }

  /** Approves the ticked drafts. A refusal on one does not stop the others. */
  async function approveSelected() {
    setBusy(true);
    setError(null);
    try {
      const res = await invoiceApi.bulkFinalize(selectedDrafts.map((r) => r.id));
      setNotice(
        res.refusedCount === 0
          ? `Approved ${res.approvedCount} invoice${res.approvedCount === 1 ? '' : 's'}.`
          : `Approved ${res.approvedCount}. ${res.refusedCount} refused: `
            + res.refused.map((f) => `${f.invoiceNumber ?? f.id} — ${f.problems[0] ?? f.message}`)
                .join('; '));
      reload();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }

  async function remove(r: InvoiceSummary) {
    const issued = r.status !== 'DRAFT';
    const reason = issued
      ? window.prompt(`${r.invoiceNumber} has already been issued. Deleting it and reusing the `
          + 'number is a GST risk, and the deletion is recorded.\n\nType a reason to continue:')
      : 'draft discarded';
    if (issued && !reason) return;
    try {
      const res = await invoiceApi.remove(r.id, reason ?? undefined);
      setNotice(`${res.invoiceNumber} deleted. ${res.note}`);
      reload();
    } catch (e) {
      setError(e);
    }
  }

  useEffect(() => {
    Promise.all([masters.customers(), masters.categories()])
      .then(([cs, cats]) => { setCustomers(cs); setCategories(cats); })
      .catch(setError);
  }, []);

  useEffect(reload, [filters]);

  function set<K extends keyof InvoiceFilters>(key: K, value: InvoiceFilters[K]) {
    setFilters((f) => ({ ...f, [key]: value || undefined }));
  }

  const total = rows.reduce((s, r) => s + r.grandTotal, 0);

  return (
    <div>
      <div className="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-1">
        <h1 className="h4 mb-0">Invoices</h1>
        <Link to="/invoices/new" className="btn btn-sm btn-outline-primary">New invoice</Link>
      </div>
      <p className="text-secondary">Search by invoice number, HC invoice number or ICO mark.</p>

      <ErrorAlert error={error} onDismiss={() => setError(null)} />

      {notice && (
        <div className="alert alert-info d-flex justify-content-between align-items-start gap-3">
          <span>{notice}</span>
          <button type="button" className="btn-close" aria-label="Dismiss"
                  onClick={() => setNotice(null)} />
        </div>
      )}

      <div className="card mb-3">
        <div className="card-body py-3 d-flex flex-wrap align-items-end gap-3">
          <div>
            <div className="text-secondary small mb-1">Draft review pack</div>
            <button className="btn btn-sm btn-outline-primary"
                    disabled={drafts.length === 0}
                    onClick={() => invoiceApi.batchPdf({ status: 'DRAFT' })}>
              Download all {drafts.length} drafts as one PDF
            </button>
          </div>

          <div className="vr d-none d-md-block" />

          <div>
            <div className="text-secondary small mb-1">Issued bills by number</div>
            <div className="d-flex gap-2 align-items-center">
              <input id="bfrom" type="number" placeholder="437" style={{ width: 90 }}
                     className="form-control form-control-sm" value={range.from}
                     onChange={(e) => setRange({ ...range, from: e.target.value })} />
              <span className="text-secondary">to</span>
              <input id="bto" type="number" placeholder="480" style={{ width: 90 }}
                     className="form-control form-control-sm" value={range.to}
                     onChange={(e) => setRange({ ...range, to: e.target.value })} />
              <button className="btn btn-sm btn-outline-primary"
                      disabled={!range.from || !range.to}
                      onClick={() => invoiceApi.batchPdf({
                        status: 'FINALIZED',
                        fromNumber: Number(range.from),
                        toNumber: Number(range.to),
                      })}>
                Download
              </button>
            </div>
          </div>

          <div className="ms-auto">
            <div className="text-secondary small mb-1">
              {selectedDrafts.length} draft{selectedDrafts.length === 1 ? '' : 's'} ticked
            </div>
            <button className="btn btn-sm btn-success" disabled={busy || selectedDrafts.length === 0}
                    onClick={approveSelected}>
              {busy ? 'Approving…' : `Approve ${selectedDrafts.length} selected`}
            </button>
          </div>
        </div>
      </div>

      <div className="card mb-3">
        <div className="card-body py-3">
          <div className="row g-2">
            <div className="col-md-3">
              <label className="form-label small" htmlFor="f-q">Search</label>
              <input id="f-q" className="form-control form-control-sm"
                     placeholder="ASE/HC/CNF/439 or 14/1850/2026/283"
                     onChange={(e) => set('q', e.target.value)} />
            </div>
            <div className="col-md-3">
              <label className="form-label small" htmlFor="f-cust">Customer</label>
              <select id="f-cust" className="form-select form-select-sm"
                      onChange={(e) => set('customerId', e.target.value ? Number(e.target.value) : undefined)}>
                <option value="">All</option>
                {customers.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </div>
            <div className="col-md-2">
              <label className="form-label small" htmlFor="f-cat">Category</label>
              <select id="f-cat" className="form-select form-select-sm"
                      onChange={(e) => set('category', e.target.value)}>
                <option value="">All</option>
                {categories.map((c) => <option key={c.id} value={c.code}>{c.code}</option>)}
              </select>
            </div>
            <div className="col-md-2">
              <label className="form-label small" htmlFor="f-status">Status</label>
              <select id="f-status" className="form-select form-select-sm"
                      onChange={(e) => set('status', (e.target.value || undefined) as never)}>
                <option value="">All</option>
                <option value="DRAFT">Draft</option>
                <option value="FINALIZED">Finalized</option>
                <option value="CANCELLED">Cancelled</option>
              </select>
            </div>
            <div className="col-md-1">
              <label className="form-label small" htmlFor="f-from">From</label>
              <input id="f-from" type="date" className="form-control form-control-sm"
                     onChange={(e) => set('from', e.target.value)} />
            </div>
            <div className="col-md-1">
              <label className="form-label small" htmlFor="f-to">To</label>
              <input id="f-to" type="date" className="form-control form-control-sm"
                     onChange={(e) => set('to', e.target.value)} />
            </div>
          </div>
        </div>
      </div>

      {loading ? <Spinner /> : (
        <div className="table-responsive">
          <table className="table table-sm align-middle">
            <thead className="table-light">
              <tr>
                <th style={{ width: '3%' }}>
                  <input className="form-check-input" type="checkbox"
                         aria-label="Select all drafts"
                         checked={drafts.length > 0 && selectedDrafts.length === drafts.length}
                         onChange={(e) => setSelected(e.target.checked
                           ? new Set(drafts.map((d) => d.id)) : new Set())} />
                </th>
                <th>Invoice</th><th>Date</th><th>Cat</th><th>Mark</th>
                <th className="text-end">Taxable</th>
                <th className="text-end">GST</th>
                <th className="text-end">Total</th>
                <th>Status</th><th />
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id}>
                  <td>
                    {r.status === 'DRAFT' && (
                      <input className="form-check-input" type="checkbox"
                             aria-label={`Select ${r.invoiceNumber}`}
                             checked={selected.has(r.id)}
                             onChange={() => toggle(r.id)} />
                    )}
                  </td>
                  <td className="font-monospace">
                    <Link to={`/invoices/${r.id}`}>{r.invoiceNumber}</Link>
                  </td>
                  <td>{r.invoiceDate}</td>
                  <td>{r.categoryCode}</td>
                  <td className="font-monospace text-secondary">{r.soaMarkNo ?? ''}</td>
                  <td className="text-end font-monospace">{money(r.taxableAmount)}</td>
                  <td className="text-end font-monospace">
                    {money(r.cgstAmount + r.sgstAmount + r.igstAmount)}
                  </td>
                  <td className="text-end font-monospace fw-semibold">{money(r.grandTotal)}</td>
                  <td><StatusBadge status={r.status} /></td>
                  <td className="text-end text-nowrap">
                    <button className="btn btn-sm btn-link px-1"
                            onClick={() => invoiceApi.openPdf(r.id)}>PDF</button>
                    <button className="btn btn-sm btn-link text-danger px-1"
                            onClick={() => remove(r)} title="Delete this invoice">✕</button>
                  </td>
                </tr>
              ))}
              {rows.length === 0 && (
                <tr><td colSpan={10} className="text-center text-secondary py-4">
                  Nothing matches those filters.
                </td></tr>
              )}
            </tbody>
            {rows.length > 0 && (
              <tfoot>
                <tr className="border-top">
                  <td colSpan={7} className="text-end fw-semibold">
                    {rows.length} invoice{rows.length === 1 ? '' : 's'}
                  </td>
                  <td className="text-end font-monospace fw-semibold">{money(total)}</td>
                  <td colSpan={2} />
                </tr>
              </tfoot>
            )}
          </table>
        </div>
      )}
    </div>
  );
}
