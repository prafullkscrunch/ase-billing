import { useEffect, useState } from 'react';
import { sequenceApi } from '../api/endpoints';
import { ApiFailure } from '../api/client';
import type { SequenceState } from '../types';

/**
 * Shows the number the next bill will take, and offers two different ways to
 * change it, because they are not the same operation:
 *
 *  - "change" moves the running sequence itself forward — for when the paper
 *    books ran on ahead of the system. The server refuses to move it
 *    backwards, since that would hand out a number already issued.
 *
 *  - "reuse a deleted number" does NOT touch the sequence at all. It sets the
 *    exact number the next invoice will be created with, which is how a
 *    number freed by deleting an invoice — or a gap left in the middle of the
 *    run — gets issued again. This used to have no UI at all: the only control
 *    here was the forward-only jump, whose input even refused (via its `min`)
 *    to accept a number at or below the highest one issued, which is exactly
 *    the range a freed number falls in.
 */
export function NextNumber({
  customerId, category, date, explicitNumber, onExplicitNumber, onChanged,
}: {
  customerId: number | null;
  category: string;
  date: string;
  /** A specific number to issue instead of the sequence's next one, or null. */
  explicitNumber?: number | null;
  onExplicitNumber?: (n: number | null) => void;
  onChanged?: () => void;
}) {
  const [state, setState] = useState<SequenceState | null>(null);
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState('');
  const [problem, setProblem] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [reusing, setReusing] = useState(false);
  const [reuseValue, setReuseValue] = useState('');
  const [reuseProblem, setReuseProblem] = useState<string | null>(null);
  const [reuseBusy, setReuseBusy] = useState(false);

  function refresh() {
    if (customerId == null) return;
    sequenceApi.peek(customerId, category, date)
      .then((s) => { setState(s); setValue(String(s.nextNumber)); })
      .catch(() => setState(null));
  }

  useEffect(refresh, [customerId, category, date]);

  async function save() {
    if (customerId == null) return;
    setBusy(true);
    setProblem(null);
    try {
      const next = await sequenceApi.jump(customerId, date, Number(value), category);
      setState(next);
      setEditing(false);
      onChanged?.();
    } catch (e) {
      setProblem(e instanceof ApiFailure ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  }

  async function useReusedNumber() {
    if (customerId == null || !state) return;
    const n = Number(reuseValue);
    if (!n || n < 1) {
      setReuseProblem('Enter the number to reuse.');
      return;
    }
    setReuseBusy(true);
    setReuseProblem(null);
    try {
      const free = await sequenceApi.available(customerId, state.financialYear, n);
      if (!free) {
        setReuseProblem(`Number ${n} is already in use for ${state.customerCode} in ${state.financialYear}.`);
        return;
      }
      onExplicitNumber?.(n);
      setReusing(false);
    } catch (e) {
      setReuseProblem(e instanceof ApiFailure ? e.message : String(e));
    } finally {
      setReuseBusy(false);
    }
  }

  if (!state) return null;

  if (explicitNumber != null) {
    return (
      <div className="d-flex align-items-center gap-2 flex-wrap">
        <span className="text-secondary small">Reissuing as</span>
        <code className="fs-6">
          ASE/{state.customerCode}/{category}/{explicitNumber}/{state.financialYear}
        </code>
        <button type="button" className="btn btn-sm btn-link p-0"
                onClick={() => onExplicitNumber?.(null)}>
          use the next number instead
        </button>
      </div>
    );
  }

  return (
    <div className="d-flex align-items-center gap-2 flex-wrap">
      <span className="text-secondary small">Next number</span>

      {!editing ? (
        <>
          <code className="fs-6">{state.previewNumber}</code>
          <button type="button" className="btn btn-sm btn-link p-0"
                  onClick={() => setEditing(true)}>
            change
          </button>
        </>
      ) : (
        <>
          <span className="font-monospace small text-secondary">
            ASE/{state.customerCode}/{category}/
          </span>
          <input
            id="next-number"
            type="number"
            min={(state.highestUsed ?? 0) + 1}
            className="form-control form-control-sm"
            style={{ width: 100 }}
            value={value}
            onChange={(e) => setValue(e.target.value)}
          />
          <span className="font-monospace small text-secondary">/{state.financialYear}</span>
          <button type="button" className="btn btn-sm btn-primary" disabled={busy} onClick={save}>
            Set
          </button>
          <button type="button" className="btn btn-sm btn-link"
                  onClick={() => { setEditing(false); setProblem(null); setValue(String(state.nextNumber)); }}>
            cancel
          </button>
        </>
      )}

      {state.highestUsed != null && (
        <span className="text-secondary small">
          (highest issued: {state.highestUsed})
        </span>
      )}

      {problem && <span className="text-danger small w-100">{problem}</span>}

      {onExplicitNumber && !editing && (
        !reusing ? (
          <button type="button" className="btn btn-sm btn-link p-0 text-secondary"
                  onClick={() => { setReusing(true); setReuseValue(''); setReuseProblem(null); }}>
            reuse a deleted number
          </button>
        ) : (
          <div className="d-flex align-items-center gap-2 flex-wrap w-100">
            <span className="font-monospace small text-secondary">
              ASE/{state.customerCode}/{category}/
            </span>
            <input
              id="reuse-number"
              type="number"
              min={1}
              placeholder="e.g. 439"
              className="form-control form-control-sm"
              style={{ width: 100 }}
              value={reuseValue}
              onChange={(e) => setReuseValue(e.target.value)}
            />
            <span className="font-monospace small text-secondary">/{state.financialYear}</span>
            <button type="button" className="btn btn-sm btn-outline-primary" disabled={reuseBusy}
                    onClick={useReusedNumber}>
              {reuseBusy ? 'Checking…' : 'Use this number'}
            </button>
            <button type="button" className="btn btn-sm btn-link"
                    onClick={() => { setReusing(false); setReuseProblem(null); }}>
              cancel
            </button>
            {reuseProblem && <span className="text-danger small w-100">{reuseProblem}</span>}
          </div>
        )
      )}
    </div>
  );
}
