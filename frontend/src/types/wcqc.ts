export type CertificateType = 'WC' | 'QC';
export type CertificateVariant = 'STANDARD' | 'COPROCAFE';
export type CertificateStatus = 'DRAFT' | 'GENERATED';

/** What the upload endpoint hands back. Any field the extractor couldn't
 *  confidently read is null and its name appears in missingFields — those
 *  fields should be shown to the operator as blank, with a note to fill
 *  them in by hand, never guessed at. */
export interface ExtractedInvoiceData {
  sourceFileName: string;
  suggestedMarksAndNos: string | null;
  invoiceNumber: string | null;
  invoiceDate: string | null;
  /** The invoice's NOTIFY party, formatted — the default suggestion for the certificate's combined CONSIGNEE/NOTIFY block. */
  consigneeNotifyBlock: string | null;
  /** The invoice's Buyer (or bare Consignee, if there's no separate Buyer block) — an alternate party some shipments' certificates use instead of Notify. */
  alternatePartyBlock: string | null;
  alternatePartyLabel: string | null;
  preCarriageBy: string | null;
  placeOfReceipt: string | null;
  countryOfOrigin: string | null;
  portOfLoading: string | null;
  portOfDischarge: string | null;
  finalDestination: string | null;
  countryOfFinalDestination: string | null;
  marksColumnText: string | null;
  descriptionColumnText: string | null;
  quantityColumnText: string | null;
  /** Auto-composed weight-breakdown suggestion (WC only, from the invoice's own totals) — always a suggestion to review. */
  summaryStatementLines: string | null;
  /** Auto-composed quality-statement suggestion (QC only, from the invoice's own grade/origin lines) — always a suggestion to review. */
  qcQualityStatementLines: string | null;
  missingFields: string[];
}

export interface CertificateFieldsRequest {
  type: CertificateType;
  variant?: CertificateVariant;
  sourceInvoiceFileName?: string | null;
  marksAndNos: string;
  invoiceNumber?: string | null;
  invoiceDate?: string | null;
  declarationLine?: string | null;
  consigneeNotifyBlock?: string | null;
  preCarriageBy?: string | null;
  placeOfReceipt?: string | null;
  countryOfOrigin?: string | null;
  portOfLoading?: string | null;
  portOfDischarge?: string | null;
  finalDestination?: string | null;
  countryOfFinalDestination?: string | null;
  marksColumnText?: string | null;
  descriptionColumnText?: string | null;
  quantityColumnText?: string | null;
  summaryStatementLabel?: string | null;
  summaryStatementLines?: string | null;
  coprocafeBagsCount?: number | null;
  coprocafeTotalKg?: number | null;
  coprocafeMoisturePercent?: number | null;
  coprocafeVariety?: string | null;
  notes?: string | null;
}

export interface CertificateView extends CertificateFieldsRequest {
  id: number;
  status: CertificateStatus;
  generatedAt: string | null;
  generatedBy: string | null;
  wordAvailable: boolean;
}

export interface CertificateSummaryView {
  id: number;
  type: CertificateType;
  variant: CertificateVariant;
  status: CertificateStatus;
  marksAndNos: string;
  invoiceNumber: string | null;
  invoiceDate: string | null;
  consigneeName: string | null;
  createdAt: string | null;
  generatedAt: string | null;
  wordAvailable: boolean;
}

/** The combo workflow: one shared field set, one WC and one QC certificate created together. */
export interface ComboCertificateRequest {
  marksAndNos: string;
  invoiceNumber?: string | null;
  invoiceDate?: string | null;
  declarationLine?: string | null;
  consigneeNotifyBlock?: string | null;
  preCarriageBy?: string | null;
  placeOfReceipt?: string | null;
  countryOfOrigin?: string | null;
  portOfLoading?: string | null;
  portOfDischarge?: string | null;
  finalDestination?: string | null;
  countryOfFinalDestination?: string | null;
  marksColumnText?: string | null;
  descriptionColumnText?: string | null;
  quantityColumnText?: string | null;
  wcSummaryStatementLines?: string | null;
  qcSummaryStatementLines?: string | null;
  qcVariant?: CertificateVariant;
  coprocafeBagsCount?: number | null;
  coprocafeTotalKg?: number | null;
  coprocafeMoisturePercent?: number | null;
  coprocafeVariety?: string | null;
  notes?: string | null;
}

export interface ComboCertificateView {
  wc: CertificateView;
  qc: CertificateView;
}
