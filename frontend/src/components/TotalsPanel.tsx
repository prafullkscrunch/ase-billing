import type { Invoice } from '../types';
import { money } from './Money';

/** The totals stack, mirroring the block printed on the bill. */
export function TotalsPanel({ invoice }: { invoice: Invoice }) {
  const interState = invoice.igstRate > 0;

  return (
    <div className="card">
      <div className="card-body py-3">
        <table className="table table-sm mb-0">
          <tbody>
            <Row label="Subtotal" value={invoice.subtotal} />
            {invoice.subtotal !== invoice.taxableAmount && (
              <Row label="Taxable" value={invoice.taxableAmount} muted />
            )}
            {interState ? (
              <Row label={`IGST ${invoice.igstRate}%`} value={invoice.igstAmount} />
            ) : (
              <>
                <Row label={`SGST ${invoice.sgstRate}%`} value={invoice.sgstAmount} />
                <Row label={`CGST ${invoice.cgstRate}%`} value={invoice.cgstAmount} />
              </>
            )}
            <tr className="border-top">
              <th className="ps-0">Grand total</th>
              <td className="text-end font-monospace fw-semibold pe-0">
                {money(invoice.grandTotal)}
              </td>
            </tr>
            {invoice.postTaxAdjustmentAmount > 0 && (
              <>
                <Row
                  label={invoice.postTaxAdjustmentLabel ?? 'Less'}
                  value={-invoice.postTaxAdjustmentAmount}
                  muted
                />
                <tr>
                  <th className="ps-0">Payable</th>
                  <td className="text-end font-monospace fw-semibold pe-0">
                    {money(invoice.netPayable)}
                  </td>
                </tr>
              </>
            )}
          </tbody>
        </table>

        <p className="small text-secondary mb-0 mt-2">
          <span className="text-uppercase">Amount in words</span>
          <br />
          {invoice.amountInWords}
        </p>
      </div>
    </div>
  );
}

function Row({ label, value, muted }: { label: string; value: number; muted?: boolean }) {
  return (
    <tr className={muted ? 'text-secondary' : undefined}>
      <td className="ps-0">{label}</td>
      <td className="text-end font-monospace pe-0">{money(value)}</td>
    </tr>
  );
}
