import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { dashboardApi } from '../api/endpoints';
import { ErrorAlert, Spinner } from '../components/Alert';
import { money } from '../components/Money';
import type { Dashboard } from '../types';

export function DashboardPage() {
  const [data, setData] = useState<Dashboard | null>(null);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => { dashboardApi.summary().then(setData).catch(setError); }, []);

  if (error) return <ErrorAlert error={error} />;
  if (!data) return <Spinner />;

  return (
    <div>
      <h1 className="h4 mb-1">{data.month}</h1>
      <p className="text-secondary">Finalised invoices this month.</p>

      <div className="row g-3 mb-4">
        <Tile label="Invoices" value={String(data.invoiceCount)} />
        <Tile label="Billed" value={money(data.totalBilled)} />
        <Tile label="Taxable" value={money(data.totalTaxable)} />
        <Tile label="GST" value={money(data.totalGst)} />
      </div>

      {data.draftCount > 0 && (
        <div className="alert alert-warning py-2">
          {data.draftCount} draft{data.draftCount === 1 ? '' : 's'} not yet finalised.{' '}
          <Link to="/invoices?status=DRAFT">Review them</Link> — drafts never reach the SOA.
        </div>
      )}

      {data.byCategory.length > 0 && (
        <div className="card" style={{ maxWidth: 520 }}>
          <div className="card-header"><strong>By category</strong></div>
          <table className="table table-sm mb-0">
            <tbody>
              {data.byCategory.map((c) => (
                <tr key={c.categoryCode}>
                  <td>{c.categoryCode}</td>
                  <td className="text-secondary">{c.count}</td>
                  <td className="text-end font-monospace">{money(c.grandTotal)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function Tile({ label, value }: { label: string; value: string }) {
  return (
    <div className="col-6 col-md-3">
      <div className="card h-100">
        <div className="card-body">
          <div className="text-secondary text-uppercase small">{label}</div>
          <div className="fs-4 font-monospace">{value}</div>
        </div>
      </div>
    </div>
  );
}
