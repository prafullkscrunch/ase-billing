import { useRef, useState } from 'react';
import { wcqcApi } from '../../api/wcqcApi';
import type { ExtractedInvoiceData } from '../../types/wcqc';

/**
 * Upload an invoice (PDF or Excel) for field extraction only. This never
 * creates or edits a billing invoice — the file is read once, server-side,
 * and its fields come back here to pre-fill the certificate form.
 */
export function UploadInvoice({ onExtracted }: { onExtracted: (data: ExtractedInvoiceData) => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  async function handleFile(file: File) {
    setBusy(true);
    setError(null);
    try {
      const data = await wcqcApi.uploadInvoice(file);
      onExtracted(data);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not read that invoice.');
    } finally {
      setBusy(false);
      if (inputRef.current) inputRef.current.value = '';
    }
  }

  return (
    <div className="card mb-3">
      <div className="card-body">
        <h6 className="card-title mb-2">Upload invoice</h6>
        <p className="text-muted small mb-2">
          PDF or Excel. Used only to pre-fill the fields below — nothing is saved until you generate the certificate.
        </p>
        <input
          ref={inputRef}
          type="file"
          accept=".pdf,.xlsx,.xls"
          className="form-control"
          disabled={busy}
          onChange={(e) => {
            const file = e.target.files?.[0];
            if (file) handleFile(file);
          }}
        />
        {busy && <div className="small text-muted mt-2">Reading invoice…</div>}
        {error && <div className="alert alert-danger mt-2 mb-0 py-2">{error}</div>}
      </div>
    </div>
  );
}
