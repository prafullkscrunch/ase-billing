import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { wcqcApi } from '../../api/wcqcApi';
import type { CertificateSummaryView, CertificateType } from '../../types/wcqc';

export function CertificateHistory() {
  const [rows, setRows] = useState<CertificateSummaryView[]>([]);
  const [type, setType] = useState<CertificateType | ''>('');
  const [q, setQ] = useState('');
  const [busyId, setBusyId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();

  async function load() {
    setError(null);
    try {
      const data = await wcqcApi.history({ type: type || undefined, q: q || undefined });
      setRows(data);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not load certificate history.');
    }
  }

  useEffect(() => { load(); }, [type]);

  async function act(id: number, action: 'excel' | 'word' | 'regenerate') {
    setBusyId(id);
    setError(null);
    try {
      if (action === 'excel') await wcqcApi.downloadExcel(id);
      else if (action === 'word') await wcqcApi.downloadWord(id);
      else { await wcqcApi.regenerate(id); await load(); }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'That action failed.');
    } finally {
      setBusyId(null);
    }
  }

  return (
    <div>
      <h4 className="mb-3">Certificate History</h4>

      <div className="d-flex gap-2 mb-3">
        <select className="form-select" style={{ maxWidth: 160 }} value={type} onChange={(e) => setType(e.target.value as CertificateType | '')}>
          <option value="">All types</option>
          <option value="WC">WC</option>
          <option value="QC">QC</option>
        </select>
        <input
          className="form-control"
          style={{ maxWidth: 260 }}
          placeholder="Search mark or invoice no."
          value={q}
          onChange={(e) => setQ(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter') load(); }}
        />
        <button className="btn btn-outline-secondary" onClick={load}>Search</button>
      </div>

      {error && <div className="alert alert-danger">{error}</div>}

      <table className="table table-sm align-middle">
        <thead>
          <tr>
            <th>Type</th>
            <th>WC/QC number</th>
            <th>Invoice ref</th>
            <th>Customer</th>
            <th>Date</th>
            <th>Status</th>
            <th>Generated</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.id}>
              <td>{r.type}{r.variant === 'COPROCAFE' ? ' (COPROCAFE)' : ''}</td>
              <td>{r.marksAndNos}</td>
              <td>{r.invoiceNumber ?? '—'}</td>
              <td>{r.consigneeName ?? '—'}</td>
              <td>{r.invoiceDate ?? '—'}</td>
              <td><span className={`badge text-bg-${r.status === 'GENERATED' ? 'success' : 'secondary'}`}>{r.status}</span></td>
              <td>{r.generatedAt ?? '—'}</td>
              <td>
                <div className="btn-group btn-group-sm">
                  <button className="btn btn-outline-secondary" onClick={() => navigate(`/wcqc/${r.type === 'WC' ? 'generate-wc' : 'generate-qc'}?id=${r.id}`)}>
                    Preview
                  </button>
                  <button className="btn btn-outline-secondary" disabled={busyId === r.id} onClick={() => act(r.id, 'excel')}>Excel</button>
                  {r.wordAvailable && (
                    <button className="btn btn-outline-secondary" disabled={busyId === r.id} onClick={() => act(r.id, 'word')}>Word</button>
                  )}
                  <button className="btn btn-outline-secondary" disabled={busyId === r.id} onClick={() => act(r.id, 'regenerate')}>Regenerate</button>
                </div>
              </td>
            </tr>
          ))}
          {rows.length === 0 && (
            <tr><td colSpan={8} className="text-muted text-center py-4">No certificates yet.</td></tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
