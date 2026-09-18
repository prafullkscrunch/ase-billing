import { useState } from 'react';
import { wcqcApi } from '../../api/wcqcApi';
import { UploadInvoice } from '../../components/wcqc/UploadInvoice';
import type { CertificateVariant, ComboCertificateRequest, ComboCertificateView, ExtractedInvoiceData } from '../../types/wcqc';

const BLANK: ComboCertificateRequest = {
  marksAndNos: '',
  preCarriageBy: 'ROAD',
  countryOfOrigin: 'INDIA',
  portOfLoading: 'MANGALORE',
  qcVariant: 'STANDARD',
  coprocafeMoisturePercent: 12,
};

function declarationLine(invoiceNumber: string | null, invoiceDate: string | null): string | undefined {
  if (!invoiceNumber || !invoiceDate) return undefined;
  const [y, m, d] = invoiceDate.split('-');
  return `Declaration: -INVOICE NO. ${invoiceNumber}. DT:${d}/${m}/${y}`;
}

/** One text field bound to a key of ComboCertificateRequest. */
function Field({
  label, value, missing, multiline, onChange,
}: { label: string; value: string; missing?: boolean; multiline?: boolean; onChange: (v: string) => void }) {
  return (
    <div className="mb-3">
      <label className="form-label">
        {label}
        {missing && <span className="badge text-bg-warning ms-2">Required information missing — please enter manually</span>}
      </label>
      {multiline ? (
        <textarea className={`form-control${missing ? ' border-warning' : ''}`} rows={3} value={value}
                   onChange={(e) => onChange(e.target.value)} />
      ) : (
        <input className={`form-control${missing ? ' border-warning' : ''}`} value={value}
               onChange={(e) => onChange(e.target.value)} />
      )}
    </div>
  );
}

export function GenerateCombo() {
  const [fields, setFields] = useState<ComboCertificateRequest>(BLANK);
  const [missing, setMissing] = useState<string[]>([]);
  const [notifyBlock, setNotifyBlock] = useState<string | null>(null);
  const [alternateBlock, setAlternateBlock] = useState<string | null>(null);
  const [alternateLabel, setAlternateLabel] = useState<string | null>(null);
  const [partyChoice, setPartyChoice] = useState<'notify' | 'alternate'>('notify');
  const [result, setResult] = useState<ComboCertificateView | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isCoprocafe = fields.qcVariant === 'COPROCAFE';

  function set<K extends keyof ComboCertificateRequest>(key: K, v: ComboCertificateRequest[K]) {
    setFields((f) => ({ ...f, [key]: v }));
    setResult(null);
  }

  function onExtracted(data: ExtractedInvoiceData) {
    setResult(null);
    setNotifyBlock(data.consigneeNotifyBlock);
    setAlternateBlock(data.alternatePartyBlock);
    setAlternateLabel(data.alternatePartyLabel);
    setPartyChoice('notify');
    // Start fresh from BLANK on every new upload, not from the previous
    // form state — merging onto the old state meant a field the new
    // invoice's extraction couldn't find (e.g. no bag-count pattern, so no
    // weight-breakdown suggestion) silently kept whatever the PREVIOUS
    // upload had put there. That's how a 320-jute-bag suggestion from an
    // earlier test could still be sitting in the weight-breakdown field
    // after uploading a completely different (80-big-bag) invoice.
    setFields(() => ({
      ...BLANK,
      marksAndNos: data.suggestedMarksAndNos ?? BLANK.marksAndNos,
      invoiceNumber: data.invoiceNumber ?? undefined,
      invoiceDate: data.invoiceDate ?? undefined,
      declarationLine: declarationLine(data.invoiceNumber, data.invoiceDate),
      consigneeNotifyBlock: data.consigneeNotifyBlock ?? undefined,
      preCarriageBy: data.preCarriageBy ?? BLANK.preCarriageBy,
      placeOfReceipt: data.placeOfReceipt ?? undefined,
      countryOfOrigin: data.countryOfOrigin ?? BLANK.countryOfOrigin,
      portOfLoading: data.portOfLoading ?? BLANK.portOfLoading,
      portOfDischarge: data.portOfDischarge ?? undefined,
      finalDestination: data.finalDestination ?? undefined,
      countryOfFinalDestination: data.countryOfFinalDestination ?? undefined,
      marksColumnText: data.marksColumnText ?? undefined,
      descriptionColumnText: data.descriptionColumnText ?? undefined,
      quantityColumnText: data.quantityColumnText ?? undefined,
      wcSummaryStatementLines: data.summaryStatementLines ?? undefined,
      qcSummaryStatementLines: data.qcQualityStatementLines ?? undefined,
    }));
    setMissing(data.missingFields);
  }

  function choseParty(choice: 'notify' | 'alternate') {
    setPartyChoice(choice);
    const block = choice === 'notify' ? notifyBlock : alternateBlock;
    if (block) set('consigneeNotifyBlock', block);
  }

  async function generate() {
    setBusy(true);
    setError(null);
    try {
      const res = await wcqcApi.createCombo(fields);
      setResult(res);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not generate these certificates.');
    } finally {
      setBusy(false);
    }
  }

  async function download(id: number, kind: 'excel' | 'word') {
    setBusy(true);
    setError(null);
    try {
      if (kind === 'excel') await wcqcApi.downloadExcel(id);
      else await wcqcApi.downloadWord(id);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not generate that file.');
    } finally {
      setBusy(false);
    }
  }

  async function downloadBoth() {
    if (!result) return;
    setBusy(true);
    setError(null);
    try {
      await wcqcApi.downloadBothExcel(result.wc.id, result.qc.id);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not generate those files.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <h4 className="mb-3">Generate Certificates (WC &amp; QC together)</h4>
      <p className="text-muted">
        One upload, one review — both certificates are created from the same shipment data.
      </p>

      <UploadInvoice onExtracted={onExtracted} />

      <div className="card mb-3">
        <div className="card-body">
          <h6 className="text-muted text-uppercase small mb-2">Shipment reference</h6>
          <div className="row">
            <div className="col-md-4">
              <Field label="Marks & Nos (shipment mark)" value={fields.marksAndNos}
                     missing={missing.includes('marksAndNos')} onChange={(v) => set('marksAndNos', v)} />
            </div>
            <div className="col-md-4">
              <Field label="Invoice number" value={fields.invoiceNumber ?? ''}
                     missing={missing.includes('invoiceNumber')} onChange={(v) => set('invoiceNumber', v)} />
            </div>
            <div className="col-md-4">
              <Field label="Invoice date (YYYY-MM-DD)" value={fields.invoiceDate ?? ''}
                     missing={missing.includes('invoiceDate')} onChange={(v) => set('invoiceDate', v)} />
            </div>
          </div>
          <Field label="Declaration line (as it will print)" value={fields.declarationLine ?? ''}
                 onChange={(v) => set('declarationLine', v)} />

          <h6 className="text-muted text-uppercase small mb-2 mt-4">Consignee / Notify</h6>
          {(notifyBlock || alternateBlock) && (
            <div className="btn-group mb-2" role="group">
              {notifyBlock && (
                <button type="button" className={`btn btn-sm btn-outline-secondary${partyChoice === 'notify' ? ' active' : ''}`}
                        onClick={() => choseParty('notify')}>
                  Use Notify: {notifyBlock.split('\n')[0]}
                </button>
              )}
              {alternateBlock && (
                <button type="button" className={`btn btn-sm btn-outline-secondary${partyChoice === 'alternate' ? ' active' : ''}`}
                        onClick={() => choseParty('alternate')}>
                  Use {alternateLabel ?? 'Buyer/Consignee'}
                </button>
              )}
            </div>
          )}
          <Field label="Consignee / Notify (one line each)" value={fields.consigneeNotifyBlock ?? ''}
                 multiline missing={missing.includes('consigneeNotifyBlock')}
                 onChange={(v) => set('consigneeNotifyBlock', v)} />

          <h6 className="text-muted text-uppercase small mb-2 mt-4">Route</h6>
          <div className="row">
            <div className="col-md-4"><Field label="Pre carriage by" value={fields.preCarriageBy ?? ''} onChange={(v) => set('preCarriageBy', v)} /></div>
            <div className="col-md-4"><Field label="Place of receipt" value={fields.placeOfReceipt ?? ''} onChange={(v) => set('placeOfReceipt', v)} /></div>
            <div className="col-md-4"><Field label="Country of origin" value={fields.countryOfOrigin ?? ''} onChange={(v) => set('countryOfOrigin', v)} /></div>
          </div>
          <div className="row">
            <div className="col-md-4"><Field label="Port of loading" value={fields.portOfLoading ?? ''} onChange={(v) => set('portOfLoading', v)} /></div>
            <div className="col-md-4">
              <Field label="Port of discharge" value={fields.portOfDischarge ?? ''}
                     missing={missing.includes('portOfDischarge')} onChange={(v) => set('portOfDischarge', v)} />
            </div>
            <div className="col-md-4">
              <Field label="Final destination" value={fields.finalDestination ?? ''}
                     missing={missing.includes('finalDestination')} onChange={(v) => set('finalDestination', v)} />
            </div>
          </div>
          <div className="row">
            <div className="col-md-4">
              <Field label="Country of final destination" value={fields.countryOfFinalDestination ?? ''}
                     missing={missing.includes('countryOfFinalDestination')} onChange={(v) => set('countryOfFinalDestination', v)} />
            </div>
          </div>

          <h6 className="text-muted text-uppercase small mb-2 mt-4">Marks, description & quantity (shared)</h6>
          <div className="row">
            <div className="col-md-4">
              <Field label="Marks / origin / grade column" value={fields.marksColumnText ?? ''} multiline
                     missing={missing.includes('marksColumnText')} onChange={(v) => set('marksColumnText', v)} />
            </div>
            <div className="col-md-4">
              <Field label="Description of goods column" value={fields.descriptionColumnText ?? ''} multiline
                     missing={missing.includes('descriptionColumnText')} onChange={(v) => set('descriptionColumnText', v)} />
            </div>
            <div className="col-md-4">
              <Field label="Quantity column" value={fields.quantityColumnText ?? ''} multiline
                     missing={missing.includes('quantityColumnText')} onChange={(v) => set('quantityColumnText', v)} />
            </div>
          </div>

          <h6 className="text-muted text-uppercase small mb-2 mt-4">WC — weight breakdown</h6>
          <Field label='Lines (auto-suggested from the invoice totals — review before generating)'
                 value={fields.wcSummaryStatementLines ?? ''} multiline
                 missing={missing.includes('summaryStatementLines')}
                 onChange={(v) => set('wcSummaryStatementLines', v)} />

          <h6 className="text-muted text-uppercase small mb-2 mt-4">QC — quality statement</h6>
          <div className="btn-group mb-2" role="group">
            <button type="button" className={`btn btn-sm btn-outline-primary${!isCoprocafe ? ' active' : ''}`}
                    onClick={() => set('qcVariant', 'STANDARD' as CertificateVariant)}>Standard QC</button>
            <button type="button" className={`btn btn-sm btn-outline-primary${isCoprocafe ? ' active' : ''}`}
                    onClick={() => set('qcVariant', 'COPROCAFE' as CertificateVariant)}>COPROCAFE QC</button>
          </div>
          <Field label="Quality statement line(s)" value={fields.qcSummaryStatementLines ?? ''} multiline
                 missing={missing.includes('qcQualityStatementLines')}
                 onChange={(v) => set('qcSummaryStatementLines', v)} />

          {isCoprocafe && (
            <div className="row">
              <div className="col-md-3"><Field label="Bags" value={String(fields.coprocafeBagsCount ?? '')} onChange={(v) => set('coprocafeBagsCount', v ? Number(v) : null)} /></div>
              <div className="col-md-3"><Field label="Total weight (KGS)" value={String(fields.coprocafeTotalKg ?? '')} onChange={(v) => set('coprocafeTotalKg', v ? Number(v) : null)} /></div>
              <div className="col-md-3"><Field label="Moisture %" value={String(fields.coprocafeMoisturePercent ?? '')} onChange={(v) => set('coprocafeMoisturePercent', v ? Number(v) : null)} /></div>
              <div className="col-md-3"><Field label="Variety" value={fields.coprocafeVariety ?? ''} onChange={(v) => set('coprocafeVariety', v)} /></div>
            </div>
          )}

          {error && <div className="alert alert-danger mt-3">{error}</div>}

          <button className="btn btn-primary mt-3" disabled={busy || !fields.marksAndNos} onClick={generate}>
            Generate WC &amp; QC
          </button>
        </div>
      </div>

      {result && (
        <>
          <div className="d-flex justify-content-end mb-2">
            <button className="btn btn-primary" disabled={busy} onClick={downloadBoth}>
              Download both (Excel)
            </button>
          </div>
          <div className="row">
            <div className="col-md-6">
              <div className="card mb-3">
                <div className="card-body">
                  <h6>WC — Certificate of Weight</h6>
                  <div className="btn-group btn-group-sm">
                    <button className="btn btn-outline-secondary" disabled={busy} onClick={() => download(result.wc.id, 'excel')}>Excel</button>
                  </div>
                </div>
              </div>
            </div>
            <div className="col-md-6">
              <div className="card mb-3">
                <div className="card-body">
                  <h6>QC — Certificate of Quality{isCoprocafe ? ' (COPROCAFE)' : ''}</h6>
                  <div className="btn-group btn-group-sm">
                    <button className="btn btn-outline-secondary" disabled={busy} onClick={() => download(result.qc.id, 'excel')}>Excel</button>
                    {isCoprocafe && (
                      <button className="btn btn-outline-secondary" disabled={busy} onClick={() => download(result.qc.id, 'word')}>Word</button>
                    )}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
