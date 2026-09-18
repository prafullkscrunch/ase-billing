import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { wcqcApi } from '../../api/wcqcApi';
import { UploadInvoice } from '../../components/wcqc/UploadInvoice';
import { CertificateFieldForm } from '../../components/wcqc/CertificateFieldForm';
import type { CertificateFieldsRequest, ExtractedInvoiceData } from '../../types/wcqc';

const BLANK: CertificateFieldsRequest = {
  type: 'WC',
  variant: 'STANDARD',
  marksAndNos: '',
  preCarriageBy: 'ROAD',
  countryOfOrigin: 'INDIA',
  portOfLoading: 'MANGALORE',
  summaryStatementLabel: 'WEIGHT:-',
};

function declarationLine(invoiceNumber: string | null, invoiceDate: string | null): string | undefined {
  if (!invoiceNumber || !invoiceDate) return undefined;
  const [y, m, d] = invoiceDate.split('-');
  return `Declaration: -INVOICE NO. ${invoiceNumber}. DT:${d}/${m}/${y}`;
}

export function GenerateWc() {
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

  function onExtracted(data: ExtractedInvoiceData) {
    setSavedId(null);
    setSaved(false);
    // Reset to BLANK on every new upload rather than merging onto the
    // previous form state — see GenerateCombo.tsx for why: otherwise a
    // field the new invoice's extraction can't find quietly keeps whatever
    // the PREVIOUS upload put there.
    setFields(() => ({
      ...BLANK,
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
      summaryStatementLines: data.summaryStatementLines ?? undefined,
    }));
    setMissing(data.missingFields);
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

  async function download() {
    if (!savedId) return;
    setBusy(true);
    setError(null);
    try {
      await wcqcApi.downloadExcel(savedId);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not generate that file.');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <h4 className="mb-3">Generate WC (Certificate of Weight)</h4>

      <UploadInvoice onExtracted={onExtracted} />

      <div className="card">
        <div className="card-body">
          <CertificateFieldForm type="WC" value={fields} onChange={(v) => { setFields(v); setSaved(false); }} missingFields={missing} />

          {error && <div className="alert alert-danger">{error}</div>}
          {saved && !error && <div className="alert alert-success py-2">Saved. You can download below.</div>}

          <div className="d-flex gap-2 mt-3">
            <button className="btn btn-primary" disabled={busy || !fields.marksAndNos} onClick={save}>
              {savedId ? 'Save changes' : 'Save certificate'}
            </button>
            <button className="btn btn-outline-secondary" disabled={busy || !savedId} onClick={download}>
              Download Excel
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
