import type { CertificateFieldsRequest, CertificateType } from '../../types/wcqc';

interface Props {
  type: CertificateType;
  value: CertificateFieldsRequest;
  onChange: (next: CertificateFieldsRequest) => void;
  missingFields?: string[];
}

/**
 * Every field here maps 1:1 to something printed on the certificate — see
 * Certificate.java for why the body sections (marks/description/quantity)
 * are multi-line text blocks rather than small atomic fields: the
 * historical documents were typed free-hand at varying line counts, so the
 * safest way to preserve exact wording is to let the operator edit whole
 * lines, not have this form recompose them.
 */
export function CertificateFieldForm({ type, value, onChange, missingFields = [] }: Props) {
  const missing = new Set(missingFields);

  function set<K extends keyof CertificateFieldsRequest>(key: K, v: CertificateFieldsRequest[K]) {
    onChange({ ...value, [key]: v });
  }

  function field(
    key: keyof CertificateFieldsRequest, label: string, opts?: { multiline?: boolean; required?: boolean }
  ) {
    const isMissing = missing.has(key as string);
    const val = (value[key] as string) ?? '';
    return (
      <div className="mb-3">
        <label className="form-label">
          {label} {opts?.required && <span className="text-danger">*</span>}
          {isMissing && (
            <span className="badge text-bg-warning ms-2">Required information missing — please enter manually</span>
          )}
        </label>
        {opts?.multiline ? (
          <textarea
            className={`form-control${isMissing ? ' border-warning' : ''}`}
            rows={3}
            value={val}
            onChange={(e) => set(key, e.target.value as any)}
          />
        ) : (
          <input
            className={`form-control${isMissing ? ' border-warning' : ''}`}
            value={val}
            onChange={(e) => set(key, e.target.value as any)}
          />
        )}
      </div>
    );
  }

  return (
    <div>
      <h6 className="text-muted text-uppercase small mb-2">Shipment reference</h6>
      <div className="row">
        <div className="col-md-4">{field('marksAndNos', 'Marks & Nos (shipment mark)', { required: true })}</div>
        <div className="col-md-4">{field('invoiceNumber', 'Invoice number')}</div>
        <div className="col-md-4">{field('invoiceDate', 'Invoice date (YYYY-MM-DD)')}</div>
      </div>
      {field('declarationLine', 'Declaration line (as it will print)')}

      <h6 className="text-muted text-uppercase small mb-2 mt-4">Consignee / Notify</h6>
      {field('consigneeNotifyBlock', 'Consignee / Notify (one line each)', { multiline: true, required: true })}

      <h6 className="text-muted text-uppercase small mb-2 mt-4">Route</h6>
      <div className="row">
        <div className="col-md-4">{field('preCarriageBy', 'Pre carriage by')}</div>
        <div className="col-md-4">{field('placeOfReceipt', 'Place of receipt')}</div>
        <div className="col-md-4">{field('countryOfOrigin', 'Country of origin')}</div>
      </div>
      <div className="row">
        <div className="col-md-4">{field('portOfLoading', 'Port of loading')}</div>
        <div className="col-md-4">{field('portOfDischarge', 'Port of discharge')}</div>
        <div className="col-md-4">{field('finalDestination', 'Final destination')}</div>
      </div>
      <div className="row">
        <div className="col-md-4">{field('countryOfFinalDestination', 'Country of final destination')}</div>
      </div>

      <h6 className="text-muted text-uppercase small mb-2 mt-4">
        {type === 'WC' ? 'Marks, packaging & weights' : 'Marks, description & quality'}
      </h6>
      <div className="row">
        <div className="col-md-4">
          {field('marksColumnText', 'Marks / origin / grade column (one line each)', { multiline: true })}
        </div>
        <div className="col-md-4">
          {field('descriptionColumnText', 'Description of goods column (one line each)', { multiline: true })}
        </div>
        <div className="col-md-4">
          {field('quantityColumnText', 'Quantity column (one line each)', { multiline: true })}
        </div>
      </div>

      <h6 className="text-muted text-uppercase small mb-2 mt-4">
        {type === 'WC' ? 'Weight breakdown' : 'Quality statement'}
      </h6>
      <div className="row">
        <div className="col-md-3">{field('summaryStatementLabel', 'Label (e.g. "WEIGHT:-")')}</div>
        <div className="col-md-9">
          {field('summaryStatementLines', 'Lines (one sentence each, as they will print)', { multiline: true })}
        </div>
      </div>

      {field('notes', 'Internal notes (not printed)', { multiline: true })}
    </div>
  );
}
