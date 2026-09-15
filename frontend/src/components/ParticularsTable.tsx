import { useState } from 'react';
import type { InvoiceItemLine, ServiceItem, TransportRoute } from '../types';
import { money } from './Money';

/**
 * The particulars grid.
 *
 * Quantity, unit and rate are captured as real fields because the rate master and
 * the calculation engine need them. What PRINTS is a single composed line in
 * `printedDescription` — the paper bill has only two columns, Particulars and
 * Amount, with the quantity and rate written into the description text
 * ("VGM expenses 3@RS750/TEU"). Choosing a service prefills that line from the
 * service template; the user can then edit it freely.
 *
 * The amounts shown here are a local preview for immediate feedback. The figures
 * that count come back from the server on save.
 */

interface Props {
  items: InvoiceItemLine[];
  services: ServiceItem[];
  /** Transport routes, so a route-based charge can go on any bill, not just a T bill. */
  routes?: TransportRoute[];
  containerNotation: string;
  teu: number;
  /** Raw container count (not TEU) — a route bills per container, not per TEU. */
  containerCount?: number;
  readOnly?: boolean;
  onChange: (items: InvoiceItemLine[]) => void;
}

export function blankLine(teu: number): InvoiceItemLine {
  return {
    serviceId: null,
    routeId: null,
    printedDescription: '',
    quantity: 1,
    unit: null,
    rate: null,
    baseAmount: 0,
    days: null,
    teu,
    calculationType: 'MANUAL',
    amount: 0,
    taxable: true,
    subLines: [],
  };
}

/**
 * Turns a master service into an invoice line with its rate and quantity filled
 * in, so the amount computes itself. Used both for the standard sheet and for
 * picking a service from the dropdown.
 */
export function lineFromService(
  svc: ServiceItem, teu: number, containerNotation: string,
): InvoiceItemLine {
  const quantity = svc.calculationType === 'PER_TEU' ? teu : (svc.defaultQuantity ?? 1);
  const rate = svc.currentRate;
  const line: InvoiceItemLine = {
    serviceId: svc.id,
    routeId: null,
    printedDescription:
      renderTemplate(svc.printTemplate, quantity, rate, null, containerNotation) || svc.name,
    quantity,
    unit: svc.defaultUnit,
    rate,
    baseAmount: svc.baseAmount ?? 0,
    days: null,
    teu,
    calculationType: svc.calculationType,
    amount: 0,
    taxable: true,
    subLines: [],
  };
  line.amount = estimate(line, teu);
  return line;
}

/**
 * Same idea as lineFromService, for a transport route: pick a route, get the
 * printed line and its per-container rate filled in, ready to bill or override.
 */
export function lineFromRoute(
  route: TransportRoute, containerCount: number, containerNotation: string,
): InvoiceItemLine {
  const quantity = containerCount;
  const rate = route.ratePerContainer;
  const line: InvoiceItemLine = {
    serviceId: null,
    routeId: route.id,
    printedDescription:
      renderTemplate(route.printTemplate, quantity, rate, null, containerNotation) || route.name,
    quantity,
    unit: 'container',
    rate,
    baseAmount: 0,
    days: null,
    teu: quantity,
    calculationType: 'SIMPLE',
    amount: 0,
    taxable: true,
    subLines: [],
  };
  line.amount = estimate(line, quantity);
  return line;
}

function renderTemplate(
  template: string | null,
  qty: number | null,
  rate: number | null,
  days: number | null,
  containers: string,
): string {
  if (!template) return '';
  return template
    .replace('{qty}', qty == null ? '' : String(qty))
    .replace('{rate}', rate == null ? '' : String(rate))
    .replace('{days}', days == null ? '' : String(days))
    .replace('{containers}', containers);
}

/** Mirrors InvoiceCalculationService so the operator sees the figure before saving. */
function estimate(line: InvoiceItemLine, teu: number): number {
  const rate = line.rate ?? 0;
  const qty = line.quantity ?? 1;
  const days = line.days ?? 1;
  const base = line.baseAmount ?? 0;
  switch (line.calculationType) {
    case 'SIMPLE':
    case 'PER_SET':
      return base + qty * rate;
    case 'PER_TEU':
      return base + teu * rate;
    case 'PER_TEU_PER_DAY':
      return base + teu * days * rate;
    default:
      // A custom/manual line has no fixed formula, so a typed Amount is kept
      // as-is — that's the whole point of "manual". But once the operator
      // types a rate (and, with it, usually a quantity) they clearly mean
      // "quantity x rate", the same as a SIMPLE line, and expect the amount to
      // follow. Without this, a rate or quantity typed into a custom line had
      // no effect at all: the Amount box would even swap for a read-only
      // figure that never changed.
      return line.rate == null ? line.amount : base + qty * rate;
  }
}

export function ParticularsTable({
  items, services, routes = [], containerNotation, teu, containerCount, readOnly, onChange,
}: Props) {
  const [openRow, setOpenRow] = useState<number | null>(null);
  const containers = containerCount ?? teu;

  function update(index: number, patch: Partial<InvoiceItemLine>) {
    const next = items.map((it, i) => (i === index ? { ...it, ...patch } : it));
    next[index] = { ...next[index], amount: estimate(next[index], teu) };
    onChange(next);
  }

  function pickService(index: number, serviceId: number) {
    const svc = services.find((s) => s.id === serviceId);
    if (!svc) {
      update(index, { serviceId: null });
      return;
    }
    // Replaces the whole line, so the rate and quantity arrive together and the
    // amount is never left at zero waiting for someone to type it.
    const fresh = lineFromService(svc, teu, containerNotation);
    onChange(items.map((it, i) => (i === index ? { ...fresh, subLines: it.subLines } : it)));
  }

  function pickRoute(index: number, routeId: number) {
    const route = routes.find((r) => r.id === routeId);
    if (!route) {
      update(index, { routeId: null });
      return;
    }
    const fresh = lineFromRoute(route, containers, containerNotation);
    onChange(items.map((it, i) => (i === index ? { ...fresh, subLines: it.subLines } : it)));
  }

  /** The dropdown mixes two masters with separate id sequences, so the value carries a prefix. */
  function pickFromDropdown(index: number, value: string) {
    if (!value) { update(index, { serviceId: null, routeId: null }); return; }
    const [kind, idStr] = value.split(':');
    const id = Number(idStr);
    if (kind === 'route') pickRoute(index, id);
    else pickService(index, id);
  }

  function addRow() {
    onChange([...items, blankLine(teu)]);
  }

  function removeRow(index: number) {
    onChange(items.filter((_, i) => i !== index));
  }

  function move(index: number, delta: number) {
    const target = index + delta;
    if (target < 0 || target >= items.length) return;
    const next = [...items];
    [next[index], next[target]] = [next[target], next[index]];
    onChange(next);
  }

  const total = items.reduce((sum, i) => sum + (i.amount ?? 0), 0);

  return (
    <div className="card mb-3">
      <div className="card-header d-flex justify-content-between align-items-center">
        <strong>Particulars</strong>
        {!readOnly && (
          <button type="button" className="btn btn-sm btn-outline-primary" onClick={addRow}>
            + Add particular
          </button>
        )}
      </div>

      <div className="table-responsive">
        <table className="table table-sm mb-0 align-middle">
          <thead className="table-light">
            <tr>
              <th style={{ width: '3%' }}>#</th>
              {!readOnly && <th style={{ width: '19%' }}>Service / route</th>}
              <th>Prints as</th>
              <th style={{ width: '8%' }}>Qty</th>
              <th style={{ width: '10%' }}>Rate</th>
              <th style={{ width: '12%' }} className="text-end">Amount</th>
              {!readOnly && <th style={{ width: '11%' }} />}
            </tr>
          </thead>
          <tbody>
            {items.map((line, i) => (
              <tr key={i}>
                <td className="text-secondary">{i + 1}</td>

                {!readOnly && (
                  <td>
                    <select
                      id={`svc-${i}`}
                      className="form-select form-select-sm"
                      value={line.routeId != null ? `route:${line.routeId}`
                            : line.serviceId != null ? `svc:${line.serviceId}` : ''}
                      onChange={(e) => pickFromDropdown(i, e.target.value)}
                    >
                      <option value="">(custom line)</option>
                      <optgroup label="Services">
                        {services.map((s) => (
                          <option key={s.id} value={`svc:${s.id}`}>{s.name}</option>
                        ))}
                      </optgroup>
                      {routes.length > 0 && (
                        <optgroup label="Transport routes">
                          {routes.map((r) => (
                            <option key={r.id} value={`route:${r.id}`}>{r.name}</option>
                          ))}
                        </optgroup>
                      )}
                    </select>
                  </td>
                )}

                <td>
                  <input
                    id={`desc-${i}`}
                    className="form-control form-control-sm"
                    value={line.printedDescription}
                    readOnly={readOnly}
                    placeholder="Text exactly as it should print on the bill"
                    onChange={(e) => update(i, { printedDescription: e.target.value })}
                  />
                  {line.subLines.length > 0 && (
                    <div className="small text-secondary ps-3 pt-1">
                      {line.subLines.map((s, k) => <div key={k}>{s}</div>)}
                    </div>
                  )}
                </td>

                <td>
                  <input
                    id={`qty-${i}`}
                    type="number" step="0.001"
                    className="form-control form-control-sm"
                    value={line.quantity ?? ''}
                    readOnly={readOnly
                      || line.calculationType === 'PER_TEU'
                      || line.calculationType === 'PER_TEU_PER_DAY'}
                    title={line.calculationType === 'PER_TEU' || line.calculationType === 'PER_TEU_PER_DAY'
                      ? 'Follows the shipment TEU automatically — change the container count above to change this'
                      : undefined}
                    onChange={(e) =>
                      update(i, { quantity: e.target.value === '' ? null : Number(e.target.value) })}
                  />
                </td>

                <td>
                  <input
                    id={`rate-${i}`}
                    type="number" step="0.01"
                    className="form-control form-control-sm"
                    value={line.rate ?? ''}
                    readOnly={readOnly}
                    title="A changed rate is kept for next time unless you untick it below"
                    onChange={(e) => {
                      const rate = e.target.value === '' ? null : Number(e.target.value);
                      const original = services.find((sv) => sv.id === line.serviceId)?.currentRate;
                      // A rate typed over a master rate is normally the new rate
                      // from here on, so default to keeping it. Untickable below.
                      const changed = line.serviceId != null
                        && original != null && rate != null && rate !== original;
                      update(i, { rate, updateMasterRate: changed });
                    }}
                  />
                  {line.updateMasterRate && !readOnly && (
                    <div className="form-check form-check-inline mt-1">
                      <input className="form-check-input" type="checkbox"
                             id={`keep-${i}`} checked
                             onChange={() => update(i, { updateMasterRate: false })} />
                      <label className="form-check-label small text-success" htmlFor={`keep-${i}`}>
                        keep for next time
                      </label>
                    </div>
                  )}
                </td>

                <td className="text-end">
                  {line.calculationType === 'MANUAL' && line.rate == null && !readOnly ? (
                    <input
                      id={`amt-${i}`}
                      type="number" step="0.01"
                      className="form-control form-control-sm text-end font-monospace"
                      value={line.amount}
                      onChange={(e) => onChange(items.map((it, k) =>
                        k === i ? { ...it, amount: Number(e.target.value) } : it))}
                    />
                  ) : (
                    <span className="font-monospace">{money(line.amount)}</span>
                  )}
                </td>

                {!readOnly && (
                  <td className="text-nowrap">
                    <button type="button" className="btn btn-sm btn-link px-1"
                            onClick={() => move(i, -1)} title="Move up">↑</button>
                    <button type="button" className="btn btn-sm btn-link px-1"
                            onClick={() => move(i, 1)} title="Move down">↓</button>
                    <button type="button" className="btn btn-sm btn-link px-1"
                            onClick={() => setOpenRow(openRow === i ? null : i)}
                            title="Detail lines printed under this charge">⋯</button>
                    <button type="button" className="btn btn-sm btn-link text-danger px-1"
                            onClick={() => removeRow(i)} title="Remove">✕</button>
                  </td>
                )}
              </tr>
            ))}

            {openRow !== null && items[openRow] && !readOnly && (
              <tr>
                <td colSpan={7}>
                  <label className="form-label small text-secondary" htmlFor="sublines">
                    Detail lines for “{items[openRow].printedDescription || 'this charge'}” — printed
                    underneath it with no amount, one per line
                    (trailer and container numbers, container splits)
                  </label>
                  <textarea
                    id="sublines"
                    className="form-control form-control-sm font-monospace"
                    rows={3}
                    value={items[openRow].subLines.join('\n')}
                    placeholder="1.Trailer No. TN02BX4800 &Container No. FATU1354288"
                    onChange={(e) =>
                      update(openRow, {
                        subLines: e.target.value.split('\n').filter((s) => s.trim() !== ''),
                      })}
                  />
                </td>
              </tr>
            )}

            {items.length === 0 && (
              <tr>
                <td colSpan={7} className="text-center text-secondary py-3">
                  No lines yet. An invoice needs at least one.
                </td>
              </tr>
            )}
          </tbody>
          <tfoot>
            <tr className="border-top">
              <td colSpan={readOnly ? 4 : 5} className="text-end fw-semibold">Subtotal</td>
              <td className="text-end font-monospace fw-semibold">{money(total)}</td>
              {!readOnly && <td />}
            </tr>
          </tfoot>
        </table>
      </div>
    </div>
  );
}
