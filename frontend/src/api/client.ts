import type { ApiError } from '../types';

const BASE = '/api';

/** Thrown for any non-2xx response, carrying the server's field-level problems. */
export class ApiFailure extends Error {
  readonly status: number;
  readonly problems: { field: string | null; message: string }[];

  constructor(status: number, body: ApiError | null) {
    super(body?.message ?? `Request failed (${status})`);
    this.status = status;
    this.problems = body?.problems ?? [];
  }

  /** Every problem on one line, for a banner. */
  get detail(): string {
    return this.problems.map((p) => (p.field ? `${p.field}: ${p.message}` : p.message)).join(' ');
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(BASE + path, {
    credentials: 'same-origin',
    headers: init?.body ? { 'Content-Type': 'application/json' } : undefined,
    ...init,
  });

  if (!res.ok) {
    let body: ApiError | null = null;
    try {
      body = await res.json();
    } catch {
      /* a non-JSON error page; the status alone will have to do */
    }
    throw new ApiFailure(res.status, body);
  }

  if (res.status === 204) return undefined as T;
  return res.json();
}

/** Streams a file the server generated and hands it to the browser to save. */
async function download(path: string): Promise<void> {
  const res = await fetch(BASE + path, { credentials: 'same-origin' });
  if (!res.ok) throw new ApiFailure(res.status, null);

  const disposition = res.headers.get('Content-Disposition') ?? '';
  const match = /filename="?([^";]+)"?/.exec(disposition);
  const filename = match ? match[1] : 'download';

  const url = URL.createObjectURL(await res.blob());
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

function openInTab(path: string): void {
  window.open(BASE + path, '_blank', 'noopener');
}

export const api = {
  get: <T>(p: string) => request<T>(p),
  post: <T>(p: string, body?: unknown) =>
    request<T>(p, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) }),
  put: <T>(p: string, body: unknown) =>
    request<T>(p, { method: 'PUT', body: JSON.stringify(body) }),
  form: (p: string, fields: Record<string, string>) =>
    request<void>(p, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams(fields).toString(),
    }),
  del: <T>(p: string) => request<T>(p, { method: 'DELETE' }),
  download,
  openInTab,
};
