import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { wcqcApi } from '../../api/wcqcApi';
import { UploadInvoice } from '../../components/wcqc/UploadInvoice';
import { CertificateFieldForm } from '../../components/wcqc/CertificateFieldForm';
import type { CertificateFieldsRequest, CertificateVariant, ExtractedInvoiceData } from '../../types/wcqc';

const BLANK: CertificateFieldsRequest = {
  type: 'QC',
  variant: 'STANDARD',
  marksAndNos: '',
  preCarriageBy: 'ROAD',
  countryOfOrigin: 'INDIA',
  portOfLoading: 'MANGALORE',
  summaryStatementLabel: 'QUALITY:',
  coprocafeMoisturePercent: 12,
};

function declarationLine(invoiceNumber: string | null, invoiceDate: string | null): string | undefined {
  if (!invoiceNumber || !invoiceDate) return undefined;
  const [y, m, d] = invoiceDate.split('-');
  return `Declaration: -INVOICE NO. ${invoiceNumber}. DT:${d}/${m}/${y}`;
}

export function GenerateQc() {
  const [fields, setFields] = useState<CertificateFieldsRequest>(BLANK);
  const [missing, setMissing] = useState<string[]>([]);
  const [savedId, setSavedId] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [params] = useSearchParams();

  useEffect(() => {
    const id = params.get('id');
    if (!id) return;
    wcqcApi.get(Number(id)).then((c) => {
      setFields(c);
      setSavedId(c.id);
      setSaved(true);
    }).catch((e) => setError(e instanceof Error ? e.message : 'Could not load that certificate.'));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const isCoprocafe = fields.variant === 'COPROCAFE';

  function onExtracted(data: ExtractedInvoiceData) {
    setSavedId(null);
    setSaved(false);
    // Reset to BLANK on every new upload — see GenerateCombo.tsx for why.
    setFields((f) => ({
      ...BLANK,
      variant: f.variant, // keep the operator's Standard/COPROCAFE choice across an upload
      sourceInvoiceFileName: data.sourceFileName,
      marksAndNos: data.suggestedMarksAndNos ?? BLANK.marksAndNos,
      invoiceNumber: data.invoiceNumber ?? undefined,
      invoiceDate: data.invoiceDate ?? undefined,
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
      declarationLine: declarationLine(data.invoiceNumber, data.invoiceDate),
      summaryStatementLines: data.qcQualityStatementLines ?? undefined,
    }));
    setMissing(data.missingFields);
  }

  function setVariant(variant: CertificateVariant) {
    setFields((f) => ({ ...f, variant }));
    setSaved(false);
  }

  async function save() {
    setBusy(true);
    setError(null);
    try {
      const result = savedId ? await wcqcApi.update(savedId, fields) : await wcqcApi.create(fields);
      setSavedId(result.id);
      setSaved(true);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not save this certificate.');
    } finally {
      setBusy(false);
    }
  }

  async function download(kind: 'excel' | 'word') {
    if (!savedId) return;
    setBusy(true);
    setError(null);
    try {
      if (kind === 'excel') await wcqcApi.downloadExcel(savedId);
      else await wcqcApi.downloadWord(savedId);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not generate that file.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <h4 className="mb-3">Generate QC (Certificate of Quality)</h4>

      <UploadInvoice onExtracted={onExtracted} />

      <div className="card mb-3">
        <div className="card-body">
          <label className="form-label d-block">Template</label>
          <div className="btn-group" role="group">
            <button
              type="button"
              className={`btn btn-outline-primary${!isCoprocafe ? ' active' : ''}`}
              onClick={() => setVariant('STANDARD')}
            >
              Standard QC
            </button>
            <button
              type="button"
              className={`btn btn-outline-primary${isCoprocafe ? ' active' : ''}`}
              onClick={() => setVariant('COPROCAFE')}
            >
              COPROCAFE QC
            </button>
          </div>
          <div className="form-text">COPROCAFE QC also exports as a Word document, matching NKG COPROCAFE's own template.</div>
        </div>
      </div>

      <div className="card">
        <div className="card-body">
          <CertificateFieldForm type="QC" value={fields} onChange={(v) => { setFields(v); setSaved(false); }} missingFields={missing} />

          {isCoprocafe && (
            <>
              <h6 className="text-muted text-uppercase small mb-2 mt-4">COPROCAFE details (needed for the Word export)</h6>
              <div className="row">
                <div className="col-md-3">
                  <label className="form-label">Bags</label>
                  <input
                    type="number"
                    className="form-control"
                    value={fields.coprocafeBagsCount ?? ''}
                    onChange={(e) => setFields((f) => ({ ...f, coprocafeBagsCount: e.target.value ? Number(e.target.value) : null }))}
                  />
                </div>
                <div className="col-md-3">
                  <label className="form-label">Total weight (KGS)</label>
                  <input
                    type="number"
                    className="form-control"
                    value={fields.coprocafeTotalKg ?? ''}
                    onChange={(e) => setFields((f) => ({ ...f, coprocafeTotalKg: e.target.value ? Number(e.target.value) : null }))}
                  />
                </div>
                <div className="col-md-3">
                  <label className="form-label">Moisture %</label>
                  <input
                    type="number"
                    className="form-control"
                    value={fields.coprocafeMoisturePercent ?? ''}
                    onChange={(e) => setFields((f) => ({ ...f, coprocafeMoisturePercent: e.target.value ? Number(e.target.value) : null }))}
                  />
                </div>
                <div className="col-md-3">
                  <label className="form-label">Variety</label>
                  <input
                    className="form-control"
                    placeholder="INDIA ROBUSTA CHERRY AA"
                    value={fields.coprocafeVariety ?? ''}
                    onChange={(e) => setFields((f) => ({ ...f, coprocafeVariety: e.target.value }))}
                  />
                </div>
              </div>
            </>
          )}

          {error && <div className="alert alert-danger mt-3">{error}</div>}
          {saved && !error && <div className="alert alert-success py-2 mt-3">Saved. You can download below.</div>}

          <div className="d-flex gap-2 mt-3">
            <button className="btn btn-primary" disabled={busy || !fields.marksAndNos} onClick={save}>
              {savedId ? 'Save changes' : 'Save certificate'}
            </button>
            <button className="btn btn-outline-secondary" disabled={busy || !savedId} onClick={() => download('excel')}>
              Download Excel
            </button>
            {isCoprocafe && (
              <button className="btn btn-outline-secondary" disabled={busy || !savedId} onClick={() => download('word')}>
                Download Word
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
