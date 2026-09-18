import type {
  CertificateFieldsRequest, CertificateSummaryView, CertificateType,
  CertificateView, ComboCertificateRequest, ComboCertificateView, ExtractedInvoiceData,
} from '../types/wcqc';

const BASE = '/api/wcqc';

async function json<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(BASE + path, {
    credentials: 'same-origin',
    headers: init?.body ? { 'Content-Type': 'application/json' } : undefined,
    ...init,
  });
  if (!res.ok) {
    let message = `Request failed (${res.status})`;
    try {
      const body = await res.json();
      message = body?.message ?? message;
    } catch { /* non-JSON error page */ }
    throw new Error(message);
  }
  if (res.status === 204) return undefined as T;
  return res.json();
}

/** Streams a generated file (Excel/Word) and hands it to the browser to save. */
async function download(path: string): Promise<void> {
  const res = await fetch(BASE + path, { credentials: 'same-origin' });
  if (!res.ok) {
    let message = `Could not generate that file (${res.status})`;
    try {
      const body = await res.json();
      message = body?.message ?? message;
    } catch { /* ignore */ }
    throw new Error(message);
  }
  const disposition = res.headers.get('Content-Disposition') ?? '';
  const match = /filename="?([^";]+)"?/.exec(disposition);
  const filename = match ? match[1] : 'certificate';

  const url = URL.createObjectURL(await res.blob());
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

export const wcqcApi = {
  /** Uploads an invoice (PDF or Excel) for field extraction. Never creates or touches a billing invoice. */
  uploadInvoice: async (file: File): Promise<ExtractedInvoiceData> => {
    const form = new FormData();
    form.append('file', file);
    const res = await fetch(BASE + '/upload', { method: 'POST', credentials: 'same-origin', body: form });
    if (!res.ok) {
      let message = `Could not read that file (${res.status})`;
      try {
        const body = await res.json();
        message = body?.message ?? message;
      } catch { /* ignore */ }
      throw new Error(message);
    }
    return res.json();
  },

  create: (req: CertificateFieldsRequest) => json<CertificateView>('/certificates', {
    method: 'POST', body: JSON.stringify(req),
  }),
  createCombo: (req: ComboCertificateRequest) => json<ComboCertificateView>('/certificates/combo', {
    method: 'POST', body: JSON.stringify(req),
  }),
  update: (id: number, req: CertificateFieldsRequest) => json<CertificateView>(`/certificates/${id}`, {
    method: 'PUT', body: JSON.stringify(req),
  }),
  get: (id: number) => json<CertificateView>(`/certificates/${id}`),
  remove: (id: number) => json<void>(`/certificates/${id}`, { method: 'DELETE' }),
  regenerate: (id: number) => json<CertificateView>(`/certificates/${id}/regenerate`, { method: 'POST' }),

  history: (opts: { type?: CertificateType; q?: string } = {}) => {
    const p = new URLSearchParams();
    if (opts.type) p.set('type', opts.type);
    if (opts.q) p.set('q', opts.q);
    return json<CertificateSummaryView[]>(`/certificates?${p}`);
  },

  downloadExcel: (id: number) => download(`/certificates/${id}/excel`),
  downloadWord: (id: number) => download(`/certificates/${id}/word`),

  /**
   * Downloads the WC and QC Excel files from one action — two separate
   * .xlsx files, not merged into one workbook. Browsers can silently drop
   * a second download fired immediately after the first, so this waits a
   * beat between the two.
   */
  downloadBothExcel: async (wcId: number, qcId: number) => {
    await download(`/certificates/${wcId}/excel`);
    await new Promise((r) => setTimeout(r, 400));
    await download(`/certificates/${qcId}/excel`);
  },
};
