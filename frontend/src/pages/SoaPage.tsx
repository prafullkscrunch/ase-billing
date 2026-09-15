import { useEffect, useState } from 'react';
import { masters, soaApi } from '../api/endpoints';
import { ErrorAlert, Spinner } from '../components/Alert';
import { money } from '../components/Money';
import type { Customer, SoaPreview } from '../types';

function monthBounds(offset = 0) {
  const now = new Date();
  const first = new Date(now.getFullYear(), now.getMonth() + offset, 1);
  const last = new Date(now.getFullYear(), now.getMonth() + offset + 1, 0);
  const iso = (d: Date) => d.toISOString().slice(0, 10);
  return { from: iso(first), to: iso(last) };
}

/**
 * GST sales details, generated from the database rather than maintained by hand.
 * Only finalised invoices appear; a draft must never reach a GST return.
 */
export function SoaPage() {
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [customerId, setCustomerId] = useState<number | null>(null);
  const [range, setRange] = useState(monthBounds());
  const [preview, setPreview] = useState<SoaPreview | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    masters.customers()
      .then((cs) => { setCustomers(cs); if (cs.length) setCustomerId(cs[0].id); })
      .catch(setError);
  }, []);

  async function generate() {
    if (customerId == null) return;
    setLoading(true); setError(null);
    try {
      setPreview(await soaApi.preview(customerId, range.from, range.to));
    } catch (e) { setError(e); } finally { setLoading(false); }
  }

  useEffect(() => { if (customerId != null) generate(); /* eslint-disable-next-line */ }, [customerId]);

  return (
    <div>
      <h1 className="h4 mb-1">GST sales details</h1>
      <p className="text-secondary">
        Built from finalised invoices. Drafts and cancelled numbers are excluded.
      </p>

      <ErrorAlert error={error} onDismiss={() => setError(null)} />

      <div className="card mb-3">
        <div className="card-body py-3">
          <div className="row g-2 align-items-end">
            <div className="col-md-4">
              <label className="form-label small" htmlFor="s-cust">Customer</label>
              <select id="s-cust" className="form-select form-select-sm" value={customerId ?? ''}
                      onChange={(e) => setCustomerId(Number(e.target.value))}>
                {customers.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </div>
            <div className="col-md-2">
              <label className="form-label small" htmlFor="s-from">From</label>
              <input id="s-from" type="date" className="form-control form-control-sm"
                     value={range.from} onChange={(e) => setRange({ ...range, from: e.target.value })} />
            </div>
            <div className="col-md-2">
              <label className="form-label small" htmlFor="s-to">To</label>
              <input id="s-to" type="date" className="form-control form-control-sm"
                     value={range.to} onChange={(e) => setRange({ ...range, to: e.target.value })} />
            </div>
            <div className="col-md-4 d-flex gap-2">
              <button className="btn btn-sm btn-primary" onClick={generate} disabled={loading}>
                Generate
              </button>
              <button className="btn btn-sm btn-outline-secondary"
                      onClick={() => setRange(monthBounds(-1))}>Last month</button>
              <button className="btn btn-sm btn-outline-success ms-auto"
                      disabled={!preview || preview.rows.length === 0}
                      onClick={() => customerId && soaApi.excel(customerId, range.from, range.to)}>
                Export Excel
              </button>
            </div>
          </div>
        </div>
      </div>

      {loading && <Spinner label="Building the statement" />}

      {preview && !loading && (
        <>
          <p className="font-monospace small text-secondary">
            ASE GST SALES DETAILS · {preview.customerName} · {preview.customerGstin}
          </p>
          <div className="table-responsive">
            <table className="table table-sm align-middle">
              <thead className="table-light">
                <tr>
                  <th>Date</th><th>Invoice no.</th>
                  <th className="text-end">Total</th>
                  <th className="text-end">Taxable</th>
                  <th className="text-end">CGST</th>
                  <th className="text-end">SGST</th>
                  <th>HC mark</th><th>Category</th>
                </tr>
              </thead>
              <tbody>
                {preview.rows.map((r) => (
                  <tr key={r.id}>
                    <td>{r.invoiceDate}</td>
                    <td className="font-monospace">{r.invoiceNumber}</td>
                    <td className="text-end font-monospace">{money(r.grandTotal)}</td>
                    <td className="text-end font-monospace">{money(r.taxableAmount)}</td>
                    <td className="text-end font-monospace">{money(r.cgstAmount)}</td>
                    <td className="text-end font-monospace">{money(r.sgstAmount)}</td>
                    <td className="font-monospace">{r.soaMarkNo ?? ''}</td>
                    <td>{r.categoryCode}</td>
                  </tr>
                ))}
                {preview.rows.length === 0 && (
                  <tr><td colSpan={8} className="text-center text-secondary py-4">
                    No finalised invoices in this range.
                  </td></tr>
                )}
              </tbody>
              {preview.rows.length > 0 && (
                <tfoot>
                  <tr className="border-top fw-semibold">
                    <td colSpan={2} className="text-end">Total</td>
                    <td className="text-end font-monospace">{money(preview.totalGrand)}</td>
                    <td className="text-end font-monospace">{money(preview.totalTaxable)}</td>
                    <td className="text-end font-monospace">{money(preview.totalCgst)}</td>
                    <td className="text-end font-monospace">{money(preview.totalSgst)}</td>
                    <td colSpan={2} />
                  </tr>
                </tfoot>
              )}
            </table>
          </div>
        </>
      )}
    </div>
  );
}
