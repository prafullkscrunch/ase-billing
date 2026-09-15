import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { invoiceApi, masters, shipmentApi } from '../api/endpoints';
import { ErrorAlert, Spinner } from '../components/Alert';
import { ParticularsTable, blankLine } from '../components/ParticularsTable';
import { StatusBadge } from '../components/Money';
import { TotalsPanel } from '../components/TotalsPanel';
import { NextNumber } from '../components/NextNumber';
import { PscStatement } from '../components/PscStatement';
import type {
  Annexure, Category, Customer, Invoice, InvoiceItemLine, InvoiceRequest, ServiceItem,
  TransportRoute,
} from '../types';

/**
 * Opens an existing invoice, or creates a standalone one (TA, S, CB — the
 * categories that are not part of a shipment pair).
 *
 * Every save round-trips: the server recomputes the totals and the response is
 * what gets rendered. The browser never decides what an invoice is worth.
 */
export function InvoiceEditor() {
  const { id } = useParams();
  const navigate = useNavigate();
  const isNew = id === undefined;

  const [invoice, setInvoice] = useState<Invoice | null>(null);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [services, setServices] = useState<ServiceItem[]>([]);
  const [routes, setRoutes] = useState<TransportRoute[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  // Draft form state, used until the invoice exists on the server.
  const [customerId, setCustomerId] = useState<number | null>(null);
  const [categoryCode, setCategoryCode] = useState('TA');
  const [invoiceDate, setInvoiceDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [headerNote, setHeaderNote] = useState('');
  const [items, setItems] = useState<InvoiceItemLine[]>([]);
  const [adjustLabel, setAdjustLabel] = useState('');
  const [adjustAmount, setAdjustAmount] = useState<number>(0);

  // TA bills are tied to a shipment but aren't part of a CNF+T pair, so the
  // shipment header is entered here rather than on the shipment screen.
  const [hcInvoiceNumber, setHcInvoiceNumber] = useState('');
  const [icoMarkFull, setIcoMarkFull] = useState('');
  const [containerCount, setContainerCount] = useState(1);
  const [containerSize, setContainerSize] = useState('20');
  const [deleting, setDeleting] = useState(false);
  const [annexure, setAnnexure] = useState<Annexure | null>(null);

  // A number typed under "reuse a deleted number" in NextNumber — bypasses the
  // sequence and is sent as-is on create. Cleared whenever the customer,
  // category or date changes, since a number that was free for one of those
  // combinations may not be free (or even meaningful) for another.
  const [explicitNumber, setExplicitNumber] = useState<number | null>(null);

  // Editing an existing shipment's container count/TEU while it's still a draft.
  const [editingTeu, setEditingTeu] = useState(false);
  const [teuContainerCount, setTeuContainerCount] = useState(1);
  const [teuContainerSize, setTeuContainerSize] = useState('20');
  const [teuBusy, setTeuBusy] = useState(false);

  const category = categories.find((c) => c.code === categoryCode);
  const needsShipment = category?.requiresShipment ?? false;
  const teu = invoice?.shipment?.teu
    ?? containerCount * (containerSize === '40' ? 2 : 1);
  const containerNotation = invoice?.shipment?.containerNotation
    ?? `${containerCount}X${containerSize}`;
  // A route bills per container, not per TEU, so it needs the raw count too.
  const effectiveContainerCount = invoice?.shipment?.containerCount ?? containerCount;
  const readOnly = invoice != null && !invoice.editable;

  useEffect(() => {
    Promise.all([masters.customers(), masters.categories(), masters.routes()])
      .then(([cs, cats, rts]) => {
        setCustomers(cs);
        setCategories(cats);
        setRoutes(rts);
        if (cs.length > 0) setCustomerId((prev) => prev ?? cs[0].id);
      })
      .catch(setError);
  }, []);

  useEffect(() => {
    if (isNew) {
      setLoading(false);
      return;
    }
    invoiceApi.get(Number(id))
      .then((inv) => {
        setInvoice(inv);
        setCustomerId(inv.customerId);
        setCategoryCode(inv.categoryCode);
        setInvoiceDate(inv.invoiceDate);
        setHeaderNote(inv.headerNote ?? '');
        setItems(inv.items);
        setAdjustLabel(inv.postTaxAdjustmentLabel ?? '');
        setAdjustAmount(inv.postTaxAdjustmentAmount ?? 0);
        setAnnexure(inv.annexure ?? null);
      })
      .catch(setError)
      .finally(() => setLoading(false));
  }, [id, isNew]);

  useEffect(() => {
    if (customerId == null || !categoryCode) return;
    masters.services(categoryCode, customerId).then(setServices).catch(() => setServices([]));
  }, [categoryCode, customerId]);

  useEffect(() => { setExplicitNumber(null); }, [customerId, categoryCode, invoiceDate]);

  useEffect(() => {
    if (invoice?.shipment) {
      setTeuContainerCount(invoice.shipment.containerCount);
      setTeuContainerSize(invoice.shipment.containerSize);
    }
  }, [invoice?.shipment]);

  const request: InvoiceRequest | null = useMemo(() => {
    if (customerId == null) return null;
    return {
      customerId,
      categoryCode,
      invoiceDate,
      shipmentId: invoice?.shipment?.id ?? null,
      // A new shipment is sent inline when the category needs one and the
      // invoice doesn't already have it.
      shipment: !invoice?.shipment && needsShipment && hcInvoiceNumber && icoMarkFull
        ? { hcInvoiceNumber, icoMarkFull, containerCount, containerSize }
        : null,
      headerNote: headerNote || null,
      postTaxAdjustmentLabel: adjustAmount > 0 ? adjustLabel : null,
      postTaxAdjustmentAmount: adjustAmount > 0 ? adjustAmount : 0,
      items,
      annexure,
      // Only meaningful on create — reissues a number freed by a deletion.
      runningNumber: !invoice ? explicitNumber : null,
    };
  }, [customerId, categoryCode, invoiceDate, headerNote, items, adjustLabel, adjustAmount,
      invoice, needsShipment, hcInvoiceNumber, icoMarkFull, containerCount, containerSize,
      annexure, explicitNumber]);

  async function save() {
    if (!request) return;
    setBusy(true);
    setError(null);
    try {
      const saved = invoice
        ? await invoiceApi.update(invoice.id, request)
        : await invoiceApi.create(request);
      setInvoice(saved);
      setItems(saved.items);
      if (!invoice) navigate(`/invoices/${saved.id}`, { replace: true });
      return saved;
    } catch (e) {
      setError(e);
      return null;
    } finally {
      setBusy(false);
    }
  }

  async function act(fn: () => Promise<Invoice>) {
    setBusy(true);
    setError(null);
    try {
      const next = await fn();
      setInvoice(next);
      setItems(next.items);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }

  // Finalise used to act on whatever the server already had saved, so any
  // particular typed after the last "Save draft" — or never saved at all —
  // was silently dropped from the finalised invoice. This always saves the
  // form's current state first, then finalises that.
  async function saveThenFinalize() {
    if (!invoice || !request) return;
    setBusy(true);
    setError(null);
    try {
      const current = await invoiceApi.update(invoice.id, request);
      const done = await invoiceApi.finalize(current.id);
      setInvoice(done);
      setItems(done.items);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }

  async function updateTeu() {
    if (!invoice?.shipment) return;
    setTeuBusy(true);
    setError(null);
    try {
      await shipmentApi.updateContainers(invoice.shipment.id,
        { containerCount: teuContainerCount, containerSize: teuContainerSize });
      const fresh = await invoiceApi.get(invoice.id);
      setInvoice(fresh);
      setItems(fresh.items);
      setEditingTeu(false);
    } catch (e) {
      setError(e);
    } finally {
      setTeuBusy(false);
    }
  }


  if (loading) return <Spinner label="Loading invoice" />;

  return (
    <div>
      <div className="d-flex justify-content-between align-items-start flex-wrap gap-2 mb-1">
        <h1 className="h4 mb-0">
          {invoice ? (
            <span className="font-monospace">{invoice.invoiceNumber}</span>
          ) : 'New invoice'}
        </h1>
        {invoice && (
          <div className="d-flex gap-2 align-items-center">
            <StatusBadge status={invoice.status} />
            <span className="text-secondary small">FY {invoice.financialYear}</span>
          </div>
        )}
      </div>
      <p className="text-secondary">
        {invoice
          ? `${invoice.categoryDescription} · ${invoice.customerName}`
          : 'For a taxi hire, service or export incentive bill. Shipment pairs are created from the shipment screen.'}
      </p>

      <ErrorAlert error={error} onDismiss={() => setError(null)} />

      {readOnly && (
        <div className="alert alert-secondary py-2">
          This invoice is {invoice!.status.toLowerCase()} and can no longer be changed.
          Duplicate it if you need a similar bill.
        </div>
      )}

      <div className="card mb-3">
        {!invoice && (
          <div className="card-header">
            <NextNumber customerId={customerId} category={categoryCode} date={invoiceDate}
                        explicitNumber={explicitNumber} onExplicitNumber={setExplicitNumber} />
          </div>
        )}
        <div className="card-body">
          <div className="row g-3">
            <div className="col-md-4">
              <label className="form-label" htmlFor="customer">Customer</label>
              <select id="customer" className="form-select" value={customerId ?? ''}
                      disabled={invoice != null}
                      onChange={(e) => setCustomerId(Number(e.target.value))}>
                {customers.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
              </select>
            </div>

            <div className="col-md-3">
              <label className="form-label" htmlFor="category">Category</label>
              <select id="category" className="form-select" value={categoryCode}
                      disabled={invoice != null}
                      onChange={(e) => setCategoryCode(e.target.value)}>
                {categories.map((c) => (
                  <option key={c.id} value={c.code}>{c.code} — {c.description}</option>
                ))}
              </select>
              {needsShipment && !invoice?.shipment && categoryCode !== 'CNF' && categoryCode !== 'T' && (
                <div className="form-text">Shipment details below.</div>
              )}
              {(categoryCode === 'CNF' || categoryCode === 'T') && !invoice && (
                <div className="form-text text-warning-emphasis">
                  CNF and T bills come in pairs — use “Bill a shipment”.
                </div>
              )}
            </div>

            <div className="col-md-2">
              <label className="form-label" htmlFor="date">Date</label>
              <input id="date" type="date" className="form-control" value={invoiceDate}
                     readOnly={readOnly}
                     onChange={(e) => setInvoiceDate(e.target.value)} />
            </div>

            <div className="col-md-3">
              <label className="form-label" htmlFor="hsn">HSN</label>
              <input id="hsn" className="form-control font-monospace"
                     value={invoice?.hsnCode ?? '996713'} readOnly />
            </div>

            <div className="col-12">
              <label className="form-label" htmlFor="note">Header note</label>
              <input id="note" className="form-control" value={headerNote} readOnly={readOnly}
                     placeholder="EXPORT INCENTIVE CLAIM FOR THE YEAR 2025-2026"
                     onChange={(e) => setHeaderNote(e.target.value)} />
            </div>

            {invoice?.shipment && (
              <div className="col-12">
                <div className="bg-body-tertiary rounded p-2 small font-monospace d-flex align-items-center flex-wrap gap-2">
                  <span>
                    HC INV NO: {invoice.shipment.hcInvoiceNumber} · ICO {invoice.shipment.icoMarkFull}
                    {' · '}
                    {!editingTeu ? (
                      <>{invoice.shipment.containerNotation} · {invoice.shipment.teu} TEU</>
                    ) : null}
                  </span>
                  {!readOnly && !editingTeu && (
                    <button type="button" className="btn btn-sm btn-link p-0"
                            onClick={() => setEditingTeu(true)}>
                      change container count / TEU
                    </button>
                  )}
                  {editingTeu && (
                    <span className="d-flex align-items-center gap-2 flex-wrap">
                      <input type="number" min={1} className="form-control form-control-sm"
                             style={{ width: 80 }} value={teuContainerCount}
                             onChange={(e) => setTeuContainerCount(Number(e.target.value))} />
                      <select className="form-select form-select-sm" style={{ width: 90 }}
                              value={teuContainerSize}
                              onChange={(e) => setTeuContainerSize(e.target.value)}>
                        <option value="20">20 ft</option>
                        <option value="40">40 ft</option>
                      </select>
                      <button type="button" className="btn btn-sm btn-primary" disabled={teuBusy}
                              onClick={updateTeu}>
                        {teuBusy ? 'Saving…' : 'Save'}
                      </button>
                      <button type="button" className="btn btn-sm btn-link"
                              onClick={() => {
                                setEditingTeu(false);
                                setTeuContainerCount(invoice.shipment!.containerCount);
                                setTeuContainerSize(invoice.shipment!.containerSize);
                              }}>
                        cancel
                      </button>
                    </span>
                  )}
                </div>
                {!readOnly && (
                  <div className="form-text">
                    Changing this re-prices every per-TEU charge on this invoice. Refused once
                    any bill against this shipment has been finalised.
                  </div>
                )}
              </div>
            )}

            {needsShipment && !invoice?.shipment && (
              <>
                <div className="col-md-4">
                  <label className="form-label" htmlFor="hcinv">HC invoice no.</label>
                  <input id="hcinv" className="form-control font-monospace"
                         value={hcInvoiceNumber} placeholder="ES/HC/2027/140"
                         onChange={(e) => setHcInvoiceNumber(e.target.value)} />
                </div>
                <div className="col-md-4">
                  <label className="form-label" htmlFor="ico">ICO mark no.</label>
                  <input id="ico" className="form-control font-monospace"
                         value={icoMarkFull} placeholder="14/1850/2026/335"
                         onChange={(e) => setIcoMarkFull(e.target.value)} />
                </div>
                <div className="col-md-4">
                  <label className="form-label" htmlFor="containers">Containers</label>
                  <div className="input-group">
                    <input id="containers" type="number" min={1} className="form-control"
                           value={containerCount}
                           onChange={(e) => setContainerCount(Math.max(1, Number(e.target.value)))} />
                    <select className="form-select" value={containerSize} aria-label="Container size"
                            onChange={(e) => setContainerSize(e.target.value)}>
                      <option value="20">X20</option>
                      <option value="40">X40</option>
                    </select>
                  </div>
                  <div className="form-text">{containerNotation} · {teu} TEU</div>
                </div>
              </>
            )}
          </div>
        </div>
      </div>

      <ParticularsTable
        items={items}
        services={services}
        routes={routes}
        containerNotation={containerNotation}
        teu={teu}
        containerCount={effectiveContainerCount}
        readOnly={readOnly}
        onChange={setItems}
      />

      {categoryCode === 'S' && (
        <PscStatement
          annexure={annexure}
          invoiceNumber={invoice?.runningNumber ? String(invoice.runningNumber) : undefined}
          readOnly={readOnly}
          onChange={setAnnexure}
        />
      )}

      {!readOnly && items.length === 0 && (
        <button className="btn btn-sm btn-outline-primary mb-3"
                onClick={() => setItems([blankLine(teu)])}>
          + Add the first line
        </button>
      )}

      <div className="row g-3">
        <div className="col-md-7">
          <div className="card">
            <div className="card-header"><strong>Deduction after GST</strong></div>
            <div className="card-body">
              <p className="small text-secondary">
                Taken off after GST, as with the DHL charges on bill ASE/HC/S/438
                (60,180 − 3,758 = 56,422). The GST return still reports the grand
                total, not the reduced figure.
              </p>
              {categoryCode === 'S' && adjustAmount === 0 && !readOnly && (
                <button type="button" className="btn btn-sm btn-outline-secondary mb-2"
                        onClick={() => setAdjustLabel('LESS DHL CHARGES A/C ASE-ACC')}>
                  Use the DHL charges label
                </button>
              )}
              <div className="row g-2">
                <div className="col-8">
                  <label className="form-label small" htmlFor="adjlabel">Label</label>
                  <input id="adjlabel" className="form-control form-control-sm" value={adjustLabel}
                         readOnly={readOnly} placeholder="LESS DHL CHARGES A/C ASE-ACC"
                         onChange={(e) => setAdjustLabel(e.target.value)} />
                </div>
                <div className="col-4">
                  <label className="form-label small" htmlFor="adjamt">Amount</label>
                  <input id="adjamt" type="number" step="0.01" min={0}
                         className="form-control form-control-sm text-end font-monospace"
                         value={adjustAmount} readOnly={readOnly}
                         onChange={(e) => setAdjustAmount(Number(e.target.value) || 0)} />
                </div>
              </div>
            </div>
          </div>
        </div>

        <div className="col-md-5">
          {invoice
            ? <TotalsPanel invoice={invoice} />
            : <div className="card"><div className="card-body text-secondary small">
                Totals appear once the draft is saved — the server computes them.
              </div></div>}
        </div>
      </div>

      <div className="d-flex gap-2 flex-wrap mt-3">
        {!readOnly && (
          <button className="btn btn-primary" disabled={busy || !request} onClick={save}>
            {busy ? 'Saving…' : invoice ? 'Save draft' : 'Create draft'}
          </button>
        )}
        {invoice && invoice.editable && (
          <button className="btn btn-success" disabled={busy}
                  onClick={saveThenFinalize}>
            Finalise
          </button>
        )}
        {invoice && (
          <>
            <button className="btn btn-outline-secondary" disabled={busy}
                    onClick={() => invoiceApi.openPdf(invoice.id)}>
              PDF
            </button>
            <button className="btn btn-outline-secondary" disabled={busy}
                    onClick={async () => {
                      const copy = await invoiceApi.duplicate(invoice.id);
                      navigate(`/invoices/${copy.id}`);
                    }}>
              Duplicate
            </button>
            {invoice.status !== 'CANCELLED' && (
              <button className="btn btn-outline-secondary ms-auto" disabled={busy}
                      onClick={() => act(() => invoiceApi.cancel(invoice.id))}>
                Cancel invoice
              </button>
            )}
            <button className="btn btn-outline-danger" disabled={busy || deleting}
                    onClick={async () => {
                      const issued = invoice.status !== 'DRAFT';
                      const reason = issued
                        ? window.prompt(
                            `${invoice.invoiceNumber} has already been issued. Deleting it and reusing `
                            + 'the number is a GST risk, and the deletion is recorded.\n\n'
                            + 'Type a reason to continue:')
                        : 'draft discarded';
                      if (issued && !reason) return;
                      setDeleting(true);
                      try {
                        const res = await invoiceApi.remove(invoice.id, reason ?? undefined);
                        window.alert(`${res.invoiceNumber} deleted. ${res.note}`);
                        navigate('/invoices');
                      } catch (e) {
                        setError(e);
                      } finally {
                        setDeleting(false);
                      }
                    }}>
              Delete
            </button>
          </>
        )}
      </div>
    </div>
  );
}
