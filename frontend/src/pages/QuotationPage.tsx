import { useEffect, useMemo, useState } from 'react';
import { quotationApi, masters } from '../api/endpoints';
import { ErrorAlert, Spinner } from '../components/Alert';
import { money } from '../components/Money';
import type { QuotationLine } from '../types';

/**
 * The rate card for Hangal Coffee Exporting — the sole customer these rates
 * are billed to, so this edits the general house rate directly rather than
 * offering a customer picker.
 *
 * Read-only by default, with an explicit "Edit rates" to unlock it — a rate
 * changed here reaches every bill created afterward, so it's treated with the
 * same care as anything else that moves a master rate. Saving asks for
 * confirmation first (the "fail-safe"): there's no silent, accidental way to
 * move a price.
 *
 * Shows two figures per charge, not the raw base/rate columns the database
 * uses — "what one container costs" and "what each one after that adds" —
 * since that's how ASE actually thinks about pricing, and the base-amount
 * arithmetic needed to make the billing formula work is not something anyone
 * editing a price list should have to do by hand.
 */
export function QuotationPage() {
  const [lines, setLines] = useState<QuotationLine[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [editing, setEditing] = useState(false);
  const [category, setCategory] = useState<string | null>(null);

  // Only while editing: line key -> the two typed figures, as strings so an
  // emptied field doesn't collapse to 0 while the operator is still typing.
  // Keyed by lineKey(), not serviceId, since a transport-route line has no
  // serviceId at all (see lineKey below).
  const [drafts, setDrafts] = useState<Record<string, { one: string; addl: string }>>({});
  const [savingKey, setSavingKey] = useState<string | null>(null);

  function load() {
    setLoading(true);
    setError(null);
    quotationApi.all()
      .then((ls) => {
        setLines(ls);
        if (category == null && ls.length > 0) setCategory(ls[0].categoryCode);
      })
      .catch(setError)
      .finally(() => setLoading(false));
  }

  useEffect(load, []);

  const categories = useMemo(() => {
    const seen = new Map<string, string>();
    lines.forEach((l) => seen.set(l.categoryCode, l.categoryName));
    return Array.from(seen.entries());
  }, [lines]);

  const visible = lines.filter((l) => l.categoryCode === category);

  /** A stable key for both a service line and a route line — exactly one of serviceId/routeId is set on any given line. */
  function lineKey(l: QuotationLine): string {
    return l.serviceId != null ? `s${l.serviceId}` : `r${l.routeId}`;
  }

  function startEditing() {
    const next: Record<string, { one: string; addl: string }> = {};
    lines.forEach((l) => {
      next[lineKey(l)] = {
        one: String(l.rateForOneContainer),
        addl: String(l.additionalPerContainer),
      };
    });
    setDrafts(next);
    setEditing(true);
  }

  function stopEditing() {
    setEditing(false);
    setDrafts({});
    setError(null);
  }

  async function save(line: QuotationLine) {
    const key = lineKey(line);
    const draft = drafts[key];
    const one = Number(draft?.one);
    const addl = line.scalesWithContainers ? Number(draft?.addl) : one;

    if (!Number.isFinite(one) || one < 0 || !Number.isFinite(addl) || addl < 0) {
      setError(new Error(`Enter valid, non-negative numbers for ${line.name}.`));
      return;
    }
    if (line.routeId == null && addl > one) {
      setError(new Error(
        `${line.name}: the additional-per-container rate can't be more than the 1-container rate.`));
      return;
    }

    const changed = one !== line.rateForOneContainer || addl !== line.additionalPerContainer;
    if (!changed) return;

    const confirmMessage = line.routeId != null
      ? `Change ${line.name} to ${money(one)} per container?\n\nThis affects every bill created from now on. Bills already issued keep their own figures.`
      : `Change ${line.name} to ${money(one)} for one container`
        + (line.scalesWithContainers ? ` and ${money(addl)} for each one after that` : '')
        + '?\n\nThis affects every bill created from now on. Bills already issued keep their own figures.';
    if (!window.confirm(confirmMessage)) return;

    setSavingKey(key);
    setError(null);
    try {
      if (line.routeId != null) {
        const updatedRoute = await masters.updateRouteRate(line.routeId, one);
        setLines((cur) => cur.map((l) => (l.routeId === line.routeId
          ? { ...l, rateForOneContainer: updatedRoute.ratePerContainer, additionalPerContainer: updatedRoute.ratePerContainer }
          : l)));
      } else {
        const updated = await quotationApi.update(line.serviceId!,
          { rateForOneContainer: one, additionalPerContainer: addl });
        setLines((cur) => cur.map((l) => (l.serviceId === updated.serviceId ? updated : l)));
      }
    } catch (e) {
      setError(e);
    } finally {
      setSavingKey(null);
    }
  }

  if (loading) return <Spinner label="Loading the rate card" />;

  return (
    <div className="quotation-page">
      <style>{`
        @media print {
          nav, .no-print { display: none !important; }
          .quotation-page { padding: 0 !important; }
        }
      `}</style>

      <div className="d-flex justify-content-between align-items-center mb-3 flex-wrap gap-2 no-print">
        <div>
          <h1 className="h4 mb-1">Quotation</h1>
          <p className="text-secondary mb-0">
            Rate card for Hangal Coffee Exporting. {editing
              ? 'Editing — changes apply the moment you save each row.'
              : 'Read-only — click "Edit rates" to change what a bill uses.'}
          </p>
        </div>
        <div className="d-flex gap-2">
          <button className="btn btn-outline-secondary" onClick={() => window.print()}>
            Print
          </button>
          {!editing ? (
            <button className="btn btn-outline-primary" onClick={startEditing}>
              Edit rates
            </button>
          ) : (
            <button className="btn btn-outline-secondary" onClick={stopEditing}>
              Done editing
            </button>
          )}
        </div>
      </div>

      <ErrorAlert error={error} onDismiss={() => setError(null)} />

      <ul className="nav nav-tabs mb-3 no-print">
        {categories.map(([code, name]) => (
          <li className="nav-item" key={code}>
            <button
              className={`nav-link${category === code ? ' active' : ''}`}
              onClick={() => setCategory(code)}
            >
              {name}
            </button>
          </li>
        ))}
      </ul>

      {category && (
        <h2 className="h6 text-uppercase text-secondary d-none d-print-block">
          {categories.find(([c]) => c === category)?.[1]}
        </h2>
      )}

      <div className="table-responsive">
        <table className="table align-middle">
          <thead>
            <tr>
              <th>Charge</th>
              <th className="text-end">Rate (1 container)</th>
              <th className="text-end">Addl. per container</th>
              <th className="text-end no-print">Since</th>
              {editing && <th className="no-print" />}
            </tr>
          </thead>
          <tbody>
            {visible.map((l) => {
              const key = lineKey(l);
              const draft = drafts[key];
              return (
                <tr key={key}>
                  <td>{l.name}</td>
                  {editing ? (
                    <>
                      <td className="text-end" style={{ maxWidth: 140 }}>
                        <input type="number" min={0} className="form-control form-control-sm text-end"
                               value={draft?.one ?? ''}
                               onChange={(e) => setDrafts((d) => ({
                                 ...d, [key]: { ...d[key], one: e.target.value },
                               }))} />
                      </td>
                      <td className="text-end" style={{ maxWidth: 140 }}>
                        {l.scalesWithContainers && l.routeId == null ? (
                          <input type="number" min={0} className="form-control form-control-sm text-end"
                                 value={draft?.addl ?? ''}
                                 onChange={(e) => setDrafts((d) => ({
                                   ...d, [key]: { ...d[key], addl: e.target.value },
                                 }))} />
                        ) : (
                          <span className="text-muted">{l.routeId != null ? '— same per container —' : '— flat —'}</span>
                        )}
                      </td>
                    </>
                  ) : (
                    <>
                      <td className="text-end font-monospace">{money(l.rateForOneContainer)}</td>
                      <td className="text-end font-monospace">
                        {l.routeId != null
                          ? <span className="text-muted">— same per container —</span>
                          : l.scalesWithContainers ? money(l.additionalPerContainer)
                          : <span className="text-muted">— flat —</span>}
                      </td>
                    </>
                  )}
                  <td className="text-end text-secondary small no-print">
                    {l.effectiveFrom ?? '—'}
                  </td>
                  {editing && (
                    <td className="no-print">
                      <button className="btn btn-sm btn-primary" disabled={savingKey === key}
                              onClick={() => save(l)}>
                        {savingKey === key ? 'Saving…' : 'Save'}
                      </button>
                    </td>
                  )}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {!editing && (
        <div className="alert alert-secondary no-print">
          Read-only. Click "Edit rates" above to unlock editing — each save asks you to confirm,
          since it changes every future bill.
        </div>
      )}
    </div>
  );
}
