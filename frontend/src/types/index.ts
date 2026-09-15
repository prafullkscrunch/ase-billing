export type CalculationType =
  | 'SIMPLE' | 'PER_TEU' | 'PER_TEU_PER_DAY' | 'PER_SET' | 'MANUAL';

export type InvoiceStatus = 'DRAFT' | 'FINALIZED' | 'CANCELLED';

export interface CurrentUser {
  username: string;
  displayName: string;
  role: 'ADMIN' | 'USER';
}

export interface Customer {
  id: number;
  code: string;
  name: string;
  gstin: string | null;
  city: string | null;
  gstTreatment: 'INTRA' | 'INTER';
}

export interface Category {
  id: number;
  code: string;
  description: string;
  pdfLayout: string;
  requiresShipment: boolean;
}

export interface ServiceItem {
  id: number;
  categoryCode: string;
  name: string;
  printTemplate: string | null;
  calculationType: CalculationType;
  defaultUnit: string | null;
  /** The rate that applies today for this customer. A suggestion, not a rule. */
  currentRate: number | null;
  /** Part of the standard charge sheet that every CNF bill starts with. */
  standard: boolean;
  defaultQuantity: number;
  /** Fixed amount billed once alongside quantity/TEU x rate. Zero for most services. */
  baseAmount: number;
}

export interface TransportRoute {
  id: number;
  name: string;
  origin: string;
  destination: string;
  printTemplate: string;
  ratePerContainer: number;
}

export interface Shipment {
  id: number;
  hcInvoiceNumber: string;
  icoMarkFull: string;
  soaMarkNo: string;
  containerCount: number;
  containerSize: string;
  teu: number;
  containerNotation: string;
}

export interface ShipmentRequest {
  hcInvoiceNumber: string;
  icoMarkFull: string;
  containerCount: number;
  containerSize: string;
  destinationCountry?: string | null;
  notes?: string | null;
}

export interface InvoiceItemLine {
  id?: number;
  sequenceNo?: number;
  serviceId: number | null;
  routeId: number | null;
  /** Printed verbatim on the bill. The paper form has only Particulars and Amount. */
  printedDescription: string;
  quantity: number | null;
  unit: string | null;
  rate: number | null;
  /** Fixed component billed once alongside quantity/TEU x rate. Usually zero. */
  baseAmount: number;
  days: number | null;
  teu: number | null;
  calculationType: CalculationType;
  amount: number;
  taxable: boolean;
  notes?: string | null;
  subLines: string[];
  /** Keep this rate as the service's new default from the invoice date on. */
  updateMasterRate?: boolean;
}

export interface Invoice {
  id: number;
  invoiceNumber: string;
  runningNumber: number;
  invoiceDate: string;
  financialYear: string;
  customerId: number;
  customerName: string;
  categoryCode: string;
  categoryDescription: string;
  shipment: Shipment | null;
  hsnCode: string;
  headerNote: string | null;
  subtotal: number;
  taxableAmount: number;
  cgstRate: number;
  cgstAmount: number;
  sgstRate: number;
  sgstAmount: number;
  igstRate: number;
  igstAmount: number;
  grandTotal: number;
  postTaxAdjustmentLabel: string | null;
  postTaxAdjustmentAmount: number;
  netPayable: number;
  amountInWords: string;
  status: InvoiceStatus;
  editable: boolean;
  items: InvoiceItemLine[];
  annexure: Annexure | null;
}

export interface Annexure {
  id?: number;
  title: string;
  col1Header: string;
  col2Header: string;
  footerText: string | null;
  /** The row count is the number of sets billed on every per-set line. */
  drivesQuantity: boolean;
  rows: { sequenceNo?: number; col1: string; col2: string | null }[];
}

export interface Consignee {
  id: number;
  name: string;
  country: string | null;
}

export interface InvoiceSummary {
  id: number;
  invoiceNumber: string;
  invoiceDate: string;
  financialYear: string;
  customerName: string;
  categoryCode: string;
  soaMarkNo: string | null;
  taxableAmount: number;
  cgstAmount: number;
  sgstAmount: number;
  igstAmount: number;
  grandTotal: number;
  status: InvoiceStatus;
}

export interface InvoiceRequest {
  customerId: number;
  categoryCode: string;
  invoiceDate: string;
  shipmentId?: number | null;
  shipment?: ShipmentRequest | null;
  hsnCode?: string | null;
  headerNote?: string | null;
  postTaxAdjustmentLabel?: string | null;
  postTaxAdjustmentAmount?: number | null;
  items: InvoiceItemLine[];
  annexure?: Annexure | null;
  /** Issue this exact number instead of the next one. Used to reissue a freed number. */
  runningNumber?: number | null;
}

export interface DeleteResult {
  invoiceNumber: string;
  wasStatus: string;
  numberFreed: boolean;
  freedNumber: number | null;
  note: string;
}

export interface BulkFinalizeResult {
  approved: string[];
  refused: { id: number; invoiceNumber: string | null; message: string; problems: string[] }[];
  approvedCount: number;
  refusedCount: number;
}

export interface TransportLeg {
  routeId: number;
  containers: number;
  /** Bill this leg at a price other than the route's. Null uses the route master. */
  rate?: number | null;
  /** Keep the typed rate as the route's new price from here on. */
  updateMasterRate?: boolean;
}

export type BillMode = 'BOTH' | 'CNF' | 'T';

export interface BillPairRequest {
  customerId: number;
  invoiceDate: string;
  shipment: ShipmentRequest;
  cnfItems: InvoiceItemLine[];
  legs: TransportLeg[];
  hassanRatePerTeu: number | null;
  /** How many containers actually went via Hassan. Null means all of them. */
  hassanContainers?: number | null;
  /** BOTH (default), CNF-only or T-only. */
  billMode?: BillMode;
  /** Reissue a freed number: the CNF bill takes this, T takes the one after. */
  startingRunningNumber?: number | null;
}

export interface ShipmentContainerRequest {
  containerCount: number;
  containerSize: string;
}

export interface SequenceState {
  customerCode: string;
  financialYear: string;
  nextNumber: number;
  highestUsed: number | null;
  previewNumber: string;
}

export interface SoaPreview {
  customerName: string;
  customerGstin: string;
  from: string;
  to: string;
  rows: InvoiceSummary[];
  totalGrand: number;
  totalTaxable: number;
  totalCgst: number;
  totalSgst: number;
  totalIgst: number;
}

export interface Dashboard {
  month: string;
  invoiceCount: number;
  totalBilled: number;
  totalTaxable: number;
  totalGst: number;
  byCategory: { categoryCode: string; count: number; grandTotal: number }[];
  draftCount: number;
}

/** What the server sends when a request is refused. */
export interface ApiError {
  message: string;
  problems: { field: string | null; message: string }[];
}
