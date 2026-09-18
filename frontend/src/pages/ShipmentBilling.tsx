import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { invoiceApi, masters, shipmentApi } from '../api/endpoints';
import { ErrorAlert, Spinner } from '../components/Alert';
import { ParticularsTable, blankLine, lineFromService, withUpdatedPerTeuSuffix } from '../components/ParticularsTable';
import { money } from '../components/Money';
import { NextNumber } from '../components/NextNumber';
import type {
  Customer, Invoice, InvoiceItemLine, OriginalCnfLookup, ServiceItem, TransportLeg, TransportRoute,
} from '../types';

/**
 * "ES/HC/2027/094" -> "ES/HC/2027/095"; "14/1850/2026/283" -> "14/1850/2026/284".
 * Increments the last run of digits in the string, which is how ASE's own
 * HC invoice and ICO mark numbers usually move from one shipment to the
 * next. Falls back to the original string unchanged if there's no trailing
 * number to bump — this is a starting suggestion, not a guarantee, and the
 * field it fills stays fully editable.
 */
function incrementTrailingNumber(value: string): string {
  const m = value.match(/(\d+)(\D*)$/);
  if (!m) return value;
  const digits = m[1];
  const next = String(Number(digits) + 1).padStart(digits.length, '0');
  return value.slice(0, m.index) + next + m[2];
}

/**
 * The main screen. One shipment produces a CNF bill and a T bill with consecutive
 * numbers, which is how every shipment in ASE's historical bills was billed.
 * Entering the header once and generating both is the whole point of the system.
 */
export function ShipmentBilling() {
  const navigate = useNavigate();

  const [customers, setCustomers] = useState<Customer[]>([]);
  const [services, setServices] = useState<ServiceItem[]>([]);
  const [routes, setRoutes] = useState<TransportRoute[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [created, setCreated] = useState<Invoice[] | null>(null);

  const [customerId, setCustomerId] = useState<number | null>(null);
  const [invoiceDate, setInvoiceDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [hcInvoiceNumber, setHcInvoiceNumber] = useState('');
  const [icoMarkFull, setIcoMarkFull] = useState('');
  const [containerCount, setContainerCount] = useState(1);
  const [containerSize, setContainerSize] = useState('20');
  // Confirmed rare (max ~10/year): a shipment routed via Cochin instead of
  // Mangalore bills six of the standard CNF charges at different rates and
  // adds one Cochin-only charge (Tally Wages) — see masters.standardSheet.
  const [cochin, setCochin] = useState(false);

  // "Taken twice" CNF redo — certificates re-issued for an already-billed
  // shipment (changed consignee, changed port of discharge, etc.). Confirmed
  // against real bills (CNF/037, CNF/347): only ICO/Permit, Phytosanitary and
  // Weight & Quality are re-billed, at ICO's normal per-mark rate but a
  // halved base — plus Certificate of origin, only if the original bill had
  // one, which is why this looks the original bill up rather than assuming.
  const [takenTwice, setTakenTwice] = useState(false);
  const [takenTwiceReason, setTakenTwiceReason] = useState('');
  const [lookupBusy, setLookupBusy] = useState(false);
  const [lookupResult, setLookupResult] = useState<OriginalCnfLookup | null>(null);

  const [cnfItems, setCnfItems] = useState<InvoiceItemLine[]>([]);
  const [standard, setStandard] = useState<ServiceItem[]>([]);
  const [legs, setLegs] = useState<TransportLeg[]>([]);
  const [hassanRate, setHassanRate] = useState<number | null>(null);
  // Not every container on a shipment goes via Hassan. Blank means all of them.
  const [hassanContainers, setHassanContainers] = useState<number | null>(null);

  // Most shipments need both bills, but a shipment that's only being cleared
  // (transport arranged separately) or only being transported (already
  // cleared on another bill) needs just one.
  const [billMode, setBillMode] = useState<'BOTH' | 'CNF' | 'T'>('BOTH');
  const includeCnf = billMode !== 'T';
  const includeTransport = billMode !== 'CNF';

  // A number typed under "reuse a deleted number" in NextNumber.
  const [startingRunningNumber, setStartingRunningNumber] = useState<number | null>(null);

  const teu = useMemo(
    () => containerCount * (containerSize === '40' ? 2 : 1),
    [containerCount, containerSize],
  );
  const containerNotation = `${containerCount}X${containerSize}`;

  // ICO/Permit is billed by number of ICO marks, not TEU, whenever those
  // differ — confirmed against real bills (e.g. CNF/374: "1X20" but marks
  // 292-295, billed for 4). Mirrors Shipment.markCount() on the backend
  // exactly, so both sides agree on the same number.
  const markCount = useMemo(() => {
    const tail = icoMarkFull.split('/').pop()?.trim() ?? '';
    const dash = tail.indexOf('-');
    if (dash <= 0 || dash === tail.length - 1) return 1;
    const start = parseInt(tail.slice(0, dash), 10);
    const end = parseInt(tail.slice(dash + 1), 10);
    if (Number.isNaN(start) || Number.isNaN(end)) return 1;
    const count = end - start + 1;
    return count > 0 ? count : 1;
  }, [icoMarkFull]);

  const icoEffectiveTeu = Math.max(teu, markCount);
  const icoServiceId = useMemo(
    () => services.find((s) => s.name.toUpperCase().includes('ICO/PERMIT'))?.id ?? null,
    [services],
  );

  // Suggests the next HC invoice / ICO mark number from this customer's last
  // shipment — "+1" on whatever was last used, which is the pattern almost
  // every real shipment follows. Only fills the fields when they're still
  // blank, so it never overwrites something already typed for this bill, and
  // both stay freely editable — the suggestion is wrong whenever the
  // customer's own numbering resets (e.g. a new series each October).
  useEffect(() => {
    if (customerId == null) return;
    shipmentApi.last(customerId)
      .then((last) => {
        if (last.hcInvoiceNumber) {
          setHcInvoiceNumber((cur) => cur || incrementTrailingNumber(last.hcInvoiceNumber!));
        }
        if (last.icoMarkFull) {
          setIcoMarkFull((cur) => cur || incrementTrailingNumber(last.icoMarkFull!));
        }
      })
      .catch(() => { /* no prior shipment, or lookup failed — leave fields blank */ });
  }, [customerId]);

  useEffect(() => {
    Promise.all([masters.customers(), masters.routes()])
      .then(([cs, rs]) => {
        setCustomers(cs);
        setRoutes(rs);
        if (cs.length > 0) setCustomerId(cs[0].id);
        if (rs.length > 0) setLegs([{ routeId: rs[0].id, containers: 1 }]);
      })
      .catch(setError)
      .finally(() => setLoading(false));
  }, []);

  // Rates are customer-specific, so reload the CNF service list when it changes,
  // and start the bill from the standard charge sheet — every CNF bill ASE
  // issues carries the same fourteen charges. Also reloads when the Cochin
  // flag changes, since that swaps six of those rates and adds Tally Wages.
  const firstStandardLoad = useRef(true);
  useEffect(() => {
    if (customerId == null) return;
    masters.services('CNF', customerId, cochin).then(setServices).catch(setError);
    masters.standardSheet('CNF', customerId, cochin)
      .then((sheet) => {
        setStandard(sheet);
        const isFirstLoad = firstStandardLoad.current;
        firstStandardLoad.current = false;
        const buildLines = () => sheet.map((svc) =>
          lineFromService(svc, svc.name.toUpperCase().includes('ICO/PERMIT') ? icoEffectiveTeu : teu, containerNotation));
        setCnfItems((current) => {
          if (current.length === 0 || isFirstLoad) {
            return current.length === 0 ? buildLines() : current;
          }
          if (!window.confirm(
            `Switch the CNF charges to the ${cochin ? 'Cochin' : 'Mangalore'} rate sheet?\n\n`
            + 'This replaces every CNF line below with the standard sheet for the new port — '
            + 'any changes already made to those lines will be lost.')) {
            return current;
          }
          return buildLines();
        });
      })
      .catch(setError);
    // teu, icoEffectiveTeu and containerNotation are read for the initial fill
    // only; changing the container count or ICO mark re-prices the lines
    // through the effect below instead.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [customerId, cochin]);

  // Re-price per-TEU lines when the container count (or, for ICO/Permit, the
  // ICO mark count) changes, and refresh their printed wording along with it —
  // the amount already followed the change here; without this the "@Rs.. per
  // TEU" text went stale the moment it changed after the line was added.
  useEffect(() => {
    setCnfItems((current) => current.map((line) => {
      if (line.calculationType !== 'PER_TEU') return line;
      const effectiveTeu = line.serviceId === icoServiceId ? icoEffectiveTeu : teu;
      return {
        ...line,
        teu: effectiveTeu,
        quantity: effectiveTeu,
        amount: (line.baseAmount ?? 0) + (line.rate ?? 0) * effectiveTeu,
        printedDescription: withUpdatedPerTeuSuffix(line.printedDescription, line, effectiveTeu),
      };
    }));
  }, [teu, icoEffectiveTeu, icoServiceId]);

  // Keep the single-leg container count in step with the shipment.
  useEffect(() => {
    setLegs((prev) => (prev.length === 1 ? [{ ...prev[0], containers: containerCount }] : prev));
  }, [containerCount]);

  useEffect(() => { setStartingRunningNumber(null); }, [customerId, invoiceDate, billMode]);

  const legTotal = legs.reduce((s, l) => s + l.containers, 0);
  const legOverflow = legTotal > containerCount;

  /** A leg bills at its typed rate if there is one, otherwise the route's. */
  function legRate(leg: TransportLeg): number {
    if (leg.rate != null) return leg.rate;
    return routes.find((r) => r.id === leg.routeId)?.ratePerContainer ?? 0;
  }

  // The surcharge is priced on the containers that actually went via Hassan.
  const hassanCount = hassanContainers ?? containerCount;
  const hassanTeu = hassanCount * (containerSize === '40' ? 2 : 1);
  const hassanOverflow = hassanCount > containerCount;

  const transportPreview = legs.reduce((sum, leg) => sum + legRate(leg) * leg.containers, 0)
    + (hassanRate ? hassanRate * hassanTeu : 0);

  /**
   * Looks up the original bill for the typed ICO mark and, if found, replaces
   * the CNF lines with the "taken twice" redo sheet: ICO/Permit at its normal
   * per-mark rate but a halved base, Phytosanitary and Weight & Quality at
   * their normal flat rates, and Certificate of origin only if the original
   * bill had one. Confirmed against real bills (CNF/037 single-mark, CNF/347
   * two-mark) rather than assumed.
   */
  async function lookupOriginalAndApplyTakenTwice() {
    if (!icoMarkFull.trim()) {
      setError(new Error('Enter the ICO mark number first — the original bill is looked up by that mark.'));
      return;
    }
    setLookupBusy(true);
    setError(null);
    try {
      const result = await shipmentApi.originalCnf(icoMarkFull.trim());
      setLookupResult(result);

      const byName = (name: string) => services.find((s) => s.name.toLowerCase() === name.toLowerCase());
      const icoService = byName('ICO/Permit, ROC Submission & Self-sealing Documentation') ?? byName('ICO/Permit');
      const phytoService = byName('Expenses on Phytosanitary certificate');
      const certWeightService = byName('Certificate of weight & quality');
      const certOriginService = byName('Certificate of origin');

      const additionalMarks = Math.max(0, result.markCount - 1);
      const icoAmount = 500 + 500 * additionalMarks;
      const icoDescription = (additionalMarks > 0
        ? `${icoService?.printTemplate ?? 'ICO/Permit, ROC Submission & Self-sealing Documentation'} Addl. ICO ${additionalMarks}SETS@500/-`
        : (icoService?.printTemplate ?? 'ICO/Permit, ROC Submission & Self-sealing Documentation'));

      const redoLines: InvoiceItemLine[] = [
        { ...blankLine(teu), serviceId: icoService?.id ?? null, printedDescription: icoDescription,
          calculationType: 'MANUAL', amount: icoAmount, quantity: 1 },
      ];
      if (phytoService) redoLines.push(lineFromService(phytoService, teu, containerNotation));
      if (certWeightService) redoLines.push(lineFromService(certWeightService, teu, containerNotation));
      if (result.hadCertificateOfOrigin && certOriginService) {
        redoLines.push(lineFromService(certOriginService, teu, containerNotation));
      }

      if (cnfItems.length > 0 && !window.confirm(
        'Replace the CNF lines below with the "taken twice" redo sheet (ICO/Permit, Phytosanitary, '
        + `Weight & Quality${result.hadCertificateOfOrigin ? ', Certificate of origin' : ''})?\n\n`
        + 'Any changes already made to the CNF lines below will be lost.')) {
        return;
      }
      setCnfItems(redoLines);
    } catch (e) {
      setError(e);
    } finally {
      setLookupBusy(false);
    }
  }

  async function generate() {
    if (customerId == null) return;
    setSaving(true);
    setError(null);
    try {
      const pair = await invoiceApi.billPair({
        customerId,
        invoiceDate,
        shipment: {
          hcInvoiceNumber, icoMarkFull, containerCount, containerSize,
          notes: takenTwice && takenTwiceReason.trim() ? takenTwiceReason.trim() : undefined,
        },
        cnfItems: includeCnf ? cnfItems : [],
        legs: includeTransport ? legs : [],
        hassanRatePerTeu: includeTransport ? hassanRate : null,
        hassanContainers: includeTransport ? hassanContainers : null,
        billMode,
        startingRunningNumber,
      });
      setCreated(pair);
    } catch (e) {
      setError(e);
    } finally {
      setSaving(false);
    }
  }

  function startAnother() {
    setCreated(null);
    setHcInvoiceNumber('');
    setIcoMarkFull('');
    setCnfItems(loadStandardSheet());
    setHassanRate(null);
    setHassanContainers(null);
    setBillMode('BOTH');
    setStartingRunningNumber(null);
  }

  function loadStandardSheet(): InvoiceItemLine[] {
    return standard.map((svc) => lineFromService(svc, teu, containerNotation));
  }

  if (loading) return <Spinner label="Loading master data" />;

  if (created) {
    return (
      <div>
        <h1 className="h4 mb-1">{created.length === 1 ? 'One draft created' : 'Two drafts created'}</h1>
        <p className="text-secondary">
          {created.length === 1 ? 'It is' : 'Both are'} draft{created.length === 1 ? '' : 's'}.
          Check {created.length === 1 ? 'it' : 'each one'}, then finalise to make {created.length === 1 ? 'it an' : 'them'} issued bill{created.length === 1 ? '' : 's'}.
        </p>

        <div className="row g-3 mb-3">
          {created.map((inv) => (
            <div className="col-md-6" key={inv.id}>
              <div className="card h-100">
                <div className="card-body">
                  <h2 className="h6 font-monospace">{inv.invoiceNumber}</h2>
                  <p className="text-secondary small mb-2">
                    {inv.categoryDescription} · {inv.items.length} line
                    {inv.items.length === 1 ? '' : 's'}
                  </p>
                  <table className="table table-sm mb-3">
                    <tbody>
                      <tr><td className="ps-0">Taxable</td>
                          <td className="text-end font-monospace pe-0">{money(inv.taxableAmount)}</td></tr>
                      <tr><td className="ps-0">SGST + CGST</td>
                          <td className="text-end font-monospace pe-0">
                            {money(inv.sgstAmount + inv.cgstAmount)}</td></tr>
                      <tr className="border-top"><th className="ps-0">Total</th>
                          <th className="text-end font-monospace pe-0">{money(inv.grandTotal)}</th></tr>
                    </tbody>
                  </table>
                  <div className="d-flex gap-2 flex-wrap">
                    <button className="btn btn-sm btn-outline-secondary"
                            onClick={() => invoiceApi.openPdf(inv.id)}>PDF</button>
                    <button className="btn btn-sm btn-outline-primary"
                            onClick={() => navigate(`/invoices/${inv.id}`)}>Open</button>
                  </div>
                </div>
              </div>
            </div>
          ))}
        </div>

        <div className="d-flex gap-2">
          <button className="btn btn-primary" onClick={startAnother}>Bill another shipment</button>
          <button className="btn btn-outline-secondary" onClick={() => navigate('/invoices')}>
            All invoices
          </button>
        </div>
      </div>
    );
  }

  return (
    <div>
      <h1 className="h4 mb-1">Bill a shipment</h1>
      <p className="text-secondary">
        Creates a CNF bill and a transport bill with consecutive numbers, both against
        the same shipment.
      </p>

      <ErrorAlert error={error} onDismiss={() => setError(null)} />

      <div className="card mb-3">
        <div className="card-header d-flex justify-content-between align-items-center flex-wrap gap-2">
          <strong>Shipment</strong>
          <NextNumber customerId={customerId} category={includeCnf ? 'CNF' : 'T'} date={invoiceDate}
                      explicitNumber={startingRunningNumber}
                      onExplicitNumber={setStartingRunningNumber} />
        </div>
        <div className="card-body">
          <div className="row g-3">
            <div className="col-12">
              <label className="form-label d-block">Bill</label>
              <div className="btn-group" role="group" aria-label="Which bills to create">
                {(['BOTH', 'CNF', 'T'] as const).map((m) => (
                  <button key={m} type="button"
                          className={`btn btn-sm ${billMode === m ? 'btn-primary' : 'btn-outline-primary'}`}
                          onClick={() => setBillMode(m)}>
                    {m === 'BOTH' ? 'Both' : m === 'CNF' ? 'CNF only' : 'Transport only'}
                  </button>
                ))}
              </div>
              {billMode !== 'BOTH' && (
                <div className="form-text">
                  {billMode === 'CNF'
                    ? 'Only the CNF bill is created — for a shipment whose transport is billed separately.'
                    : 'Only the transport bill is created — for a shipment already cleared on another bill.'}
                </div>
              )}

              {billMode === 'CNF' && (
                <div className="border rounded p-2 mt-2">
                  <div className="form-check">
                    <input id="takenTwice" type="checkbox" className="form-check-input"
                           checked={takenTwice}
                           onChange={(e) => { setTakenTwice(e.target.checked); setLookupResult(null); }} />
                    <label className="form-check-label" htmlFor="takenTwice">
                      Taken twice (certificates re-issued for an already-billed shipment)
                    </label>
                  </div>
                  {takenTwice && (
                    <div className="mt-2">
                      <label className="form-label" htmlFor="takenTwiceReason">
                        Reason (prints after the HSN code, in parens — e.g. "Taken twice due change in consignee")
                      </label>
                      <div className="input-group">
                        <input id="takenTwiceReason" className="form-control"
                               placeholder="Taken twice due change in consignee"
                               value={takenTwiceReason}
                               onChange={(e) => setTakenTwiceReason(e.target.value)} />
                        <button className="btn btn-outline-primary" type="button" disabled={lookupBusy}
                                onClick={lookupOriginalAndApplyTakenTwice}>
                          {lookupBusy ? 'Looking up…' : 'Look up original & fill CNF lines'}
                        </button>
                      </div>
                      {lookupResult && (
                        <div className="form-text">
                          {lookupResult.found
                            ? `Original bill found — ${lookupResult.markCount} mark(s), `
                              + `${lookupResult.hadCertificateOfOrigin ? 'included' : 'did not include'} Certificate of origin.`
                            : 'No earlier bill found for this mark — enter the redo lines by hand below.'}
                        </div>
                      )}
                    </div>
                  )}
                </div>
              )}
            </div>

            <div className="col-md-4">
              <label className="form-label" htmlFor="customer">Customer</label>
              <select id="customer" className="form-select" value={customerId ?? ''}
                      onChange={(e) => {
                        setCustomerId(Number(e.target.value));
                        // A different customer's numbering is unrelated to
                        // whatever was suggested for the last one.
                        setHcInvoiceNumber('');
                        setIcoMarkFull('');
                      }}>
                {customers.map((c) => (
                  <option key={c.id} value={c.id}>{c.name}</option>
                ))}
              </select>
            </div>

            <div className="col-md-2">
              <label className="form-label" htmlFor="date">Invoice date</label>
              <input id="date" type="date" className="form-control" value={invoiceDate}
                     onChange={(e) => setInvoiceDate(e.target.value)} />
            </div>

            <div className="col-md-3">
              <label className="form-label" htmlFor="hcinv">HC invoice no.</label>
              <input id="hcinv" className="form-control font-monospace" value={hcInvoiceNumber}
                     placeholder="ES/HC/2027/094"
                     onChange={(e) => setHcInvoiceNumber(e.target.value)} />
              <div className="form-text">Suggested from this customer's last shipment — edit freely.</div>
            </div>

            <div className="col-md-3">
              <label className="form-label" htmlFor="ico">ICO mark no.</label>
              <input id="ico" className="form-control font-monospace" value={icoMarkFull}
                     placeholder="14/1850/2026/283"
                     onChange={(e) => setIcoMarkFull(e.target.value)} />
              <div className="form-text">A range such as 292-295 is fine; the SOA takes the first.</div>
            </div>

            <div className="col-md-3">
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

            <div className="col-md-3 d-flex align-items-end">
              <div className="form-check">
                <input id="cochin" type="checkbox" className="form-check-input"
                       checked={cochin} onChange={(e) => setCochin(e.target.checked)} />
                <label className="form-check-label" htmlFor="cochin">
                  Cochin-bound shipment
                </label>
                <div className="form-text">Uses Cochin CNF rates and adds Tally Wages.</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      {includeCnf && (
        <>
          <div className="d-flex justify-content-between align-items-center mt-4 mb-1 flex-wrap gap-2">
            <h2 className="h6 text-uppercase text-secondary mb-0">CNF bill</h2>
            <div className="d-flex gap-2">
              <button className="btn btn-sm btn-outline-secondary"
                      onClick={() => setCnfItems(loadStandardSheet())}>
                Reset to standard {standard.length} charges
              </button>
              <button className="btn btn-sm btn-outline-secondary"
                      onClick={() => setCnfItems([])}>
                Clear all
              </button>
            </div>
          </div>
          <p className="text-secondary small">
            Pre-filled with the standard CNF charge sheet. Amounts come from quantity ×
            rate — remove any line that does not apply to this shipment.
          </p>

          <ParticularsTable
            items={cnfItems}
            services={services}
            routes={routes}
            containerNotation={containerNotation}
            teu={teu}
            containerCount={containerCount}
            onChange={setCnfItems}
          />
          {cnfItems.length === 0 && (
            <div className="d-flex gap-2 mb-3">
              <button className="btn btn-sm btn-outline-primary"
                      onClick={() => setCnfItems(loadStandardSheet())}>
                Load the standard {standard.length} charges
              </button>
              <button className="btn btn-sm btn-outline-secondary"
                      onClick={() => setCnfItems([blankLine(teu)])}>
                + Add a single charge
              </button>
            </div>
          )}
        </>
      )}

      {includeTransport && (
      <>
      <h2 className="h6 text-uppercase text-secondary mt-4">Transport bill</h2>
      <div className="card mb-3">
        <div className="card-body">
          {legs.map((leg, i) => {
            const route = routes.find((r) => r.id === leg.routeId);
            const master = route?.ratePerContainer ?? 0;
            const rate = legRate(leg);
            return (
              <div className="row g-2 align-items-end mb-2" key={i}>
                <div className="col-md-5">
                  <label className="form-label small" htmlFor={`route-${i}`}>Route</label>
                  <select id={`route-${i}`} className="form-select form-select-sm"
                          value={leg.routeId}
                          onChange={(e) => setLegs(legs.map((l, k) =>
                            // A different route brings its own price, so drop any override.
                            k === i ? { ...l, routeId: Number(e.target.value),
                                        rate: null, updateMasterRate: false } : l))}>
                    {routes.map((r) => (
                      <option key={r.id} value={r.id}>
                        {r.origin} → {r.destination} · {money(r.ratePerContainer)}/container
                      </option>
                    ))}
                  </select>
                </div>
                <div className="col-md-2">
                  <label className="form-label small" htmlFor={`legc-${i}`}>Containers</label>
                  <input id={`legc-${i}`} type="number" min={1}
                         className="form-control form-control-sm" value={leg.containers}
                         onChange={(e) => setLegs(legs.map((l, k) =>
                           k === i ? { ...l, containers: Math.max(1, Number(e.target.value)) } : l))} />
                </div>
                <div className="col-md-2">
                  <label className="form-label small" htmlFor={`legr-${i}`}>Per container</label>
                  <input id={`legr-${i}`} type="number" step="0.01" min={0}
                         className="form-control form-control-sm" value={rate}
                         title="A changed price is kept for next time unless you untick it"
                         onChange={(e) => {
                           const typed = e.target.value === '' ? null : Number(e.target.value);
                           setLegs(legs.map((l, k) => k === i
                             ? { ...l, rate: typed,
                                 updateMasterRate: typed != null && typed !== master }
                             : l));
                         }} />
                  {leg.updateMasterRate && (
                    <div className="form-check mt-1">
                      <input className="form-check-input" type="checkbox"
                             id={`legkeep-${i}`} checked
                             onChange={() => setLegs(legs.map((l, k) =>
                               k === i ? { ...l, updateMasterRate: false } : l))} />
                      <label className="form-check-label small text-success" htmlFor={`legkeep-${i}`}>
                        keep for next time
                      </label>
                    </div>
                  )}
                </div>
                <div className="col-md-2">
                  <span className="font-monospace">{money(rate * leg.containers)}</span>
                </div>
                <div className="col-md-1 text-end">
                  {legs.length > 1 && (
                    <button className="btn btn-sm btn-link text-danger"
                            onClick={() => setLegs(legs.filter((_, k) => k !== i))}>✕</button>
                  )}
                </div>
              </div>
            );
          })}

          <button className="btn btn-sm btn-outline-secondary mt-1"
                  onClick={() => routes[0] && setLegs([...legs, { routeId: routes[0].id, containers: 1 }])}>
            + Add a route
          </button>

          {legOverflow && (
            <div className="alert alert-warning mt-3 mb-0 py-2">
              The legs cover {legTotal} containers but the shipment has {containerCount}.
            </div>
          )}

          <hr />

          <div className="row g-2 align-items-end">
            <div className="col-md-4">
              <label className="form-label small" htmlFor="hassan">
                Movement via Hassan, per TEU
              </label>
              <input id="hassan" type="number" step="0.01" className="form-control form-control-sm"
                     placeholder="leave blank to omit" value={hassanRate ?? ''}
                     onChange={(e) =>
                       setHassanRate(e.target.value === '' ? null : Number(e.target.value))} />
              <div className="form-text">Charged at 4,000 per TEU on the historical bills.</div>
            </div>

            {hassanRate != null && hassanRate > 0 && (
              <div className="col-md-4">
                <label className="form-label small" htmlFor="hassanc">
                  Containers via Hassan
                </label>
                <input id="hassanc" type="number" min={1} max={containerCount}
                       className={`form-control form-control-sm${hassanOverflow ? ' is-invalid' : ''}`}
                       placeholder={`all ${containerCount}`}
                       value={hassanContainers ?? ''}
                       onChange={(e) => setHassanContainers(
                         e.target.value === '' ? null : Math.max(1, Number(e.target.value)))} />
                <div className="form-text">
                  {hassanOverflow
                    ? `The shipment only has ${containerCount}.`
                    : `${hassanCount}x${containerSize} · ${hassanTeu} TEU · ${money(hassanRate * hassanTeu)}`}
                </div>
              </div>
            )}
            <div className="col-md-4">
              <span className="text-secondary small">Transport total before GST</span><br />
              <span className="font-monospace fs-5">{money(transportPreview)}</span>
            </div>
          </div>
        </div>
      </div>
      </>
      )}

      <div className="d-flex gap-2 align-items-center">
        <button className="btn btn-primary"
                disabled={saving || (includeTransport && (legOverflow || hassanOverflow))}
                onClick={generate}>
          {saving ? 'Generating…' : billMode === 'BOTH' ? 'Generate both bills' : 'Generate bill'}
        </button>
        <span className="text-secondary small">
          Creates {billMode === 'BOTH' ? 'two drafts' : 'a draft'}. Nothing is issued until you finalise
          {billMode === 'BOTH' ? ' them' : ' it'}.
        </span>
      </div>
    </div>
  );
}
