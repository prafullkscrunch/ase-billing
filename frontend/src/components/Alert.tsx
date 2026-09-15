import { ApiFailure } from '../api/client';

/**
 * Shows what the server actually objected to. The finalisation checks return a
 * list of specific problems ("CGST is 2736 but 9% of 30700 is 2763"), and those
 * are the whole point — collapsing them into "an error occurred" wastes them.
 */
export function ErrorAlert({ error, onDismiss }: { error: unknown; onDismiss?: () => void }) {
  if (!error) return null;

  const failure = error instanceof ApiFailure ? error : null;
  const message = error instanceof Error ? error.message : String(error);

  return (
    <div className="alert alert-danger" role="alert">
      <div className="d-flex justify-content-between align-items-start gap-3">
        <div>
          <strong>{message}</strong>
          {failure && failure.problems.length > 0 && (
            <ul className="mb-0 mt-2 ps-3">
              {failure.problems.map((p, i) => (
                <li key={i}>
                  {p.field && <code className="me-1">{p.field}</code>}
                  {p.message}
                </li>
              ))}
            </ul>
          )}
        </div>
        {onDismiss && (
          <button type="button" className="btn-close" aria-label="Dismiss" onClick={onDismiss} />
        )}
      </div>
    </div>
  );
}

export function Spinner({ label = 'Loading' }: { label?: string }) {
  return (
    <div className="d-flex align-items-center gap-2 text-secondary py-4">
      <div className="spinner-border spinner-border-sm" role="status" aria-hidden="true" />
      <span>{label}…</span>
    </div>
  );
}
