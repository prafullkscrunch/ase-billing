import { api } from './client';
import type {
  BillPairRequest, BulkFinalizeResult, Category, Consignee, CurrentUser, Customer,
  Dashboard, DeleteResult, Invoice, InvoiceRequest, InvoiceStatus, InvoiceSummary,
  LastShipment, SequenceState, ServiceItem, ShipmentContainerRequest, SoaPreview, TransportRoute,
} from '../types';

export const auth = {
  me: () => api.get<CurrentUser>('/auth/me'),
  login: (username: string, password: string) => api.form('/auth/login', { username, password }),
  logout: () => api.post<void>('/auth/logout'),
};

export const masters = {
  customers: () => api.get<Customer[]>('/customers'),
  categories: () => api.get<Category[]>('/categories'),
  services: (categoryCode: string, customerId: number) =>
    api.get<ServiceItem[]>(`/services?category=${categoryCode}&customerId=${customerId}`),
  routes: () => api.get<TransportRoute[]>('/transport-routes'),
  /** Changes a route's standing price. Bills already issued keep their own rate. */
  updateRouteRate: (id: number, ratePerContainer: number) =>
    api.put<TransportRoute>(`/transport-routes/${id}/rate`, { ratePerContainer }),
  /** Buyers for the sample bill's PSC statement. */
  consignees: () => api.get<Consignee[]>('/consignees'),
  /** Adds a buyer the list doesn't have yet; kept for later months. */
  addConsignee: (name: string, country?: string) =>
    api.post<Consignee>('/consignees', { name, country: country ?? null }),
  /** The fourteen charges every CNF bill carries, in print order. */
  standardSheet: (categoryCode: string, customerId: number) =>
    api.get<ServiceItem[]>(`/services/standard?category=${categoryCode}&customerId=${customerId}`),
};

export interface InvoiceFilters {
  customerId?: number;
  from?: string;
  to?: string;
  category?: string;
  status?: InvoiceStatus;
  q?: string;
}

export const invoiceApi = {
  list: (f: InvoiceFilters = {}) => {
    const p = new URLSearchParams();
    Object.entries(f).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') p.set(k, String(v));
    });
    return api.get<InvoiceSummary[]>(`/invoices?${p}`);
  },
  get: (id: number) => api.get<Invoice>(`/invoices/${id}`),

  /**
   * Saves and returns the invoice with every total recomputed by the server.
   * Always render from the response: a total computed in the browser and one
   * computed in Java will eventually disagree, and the server is authoritative.
   */
  create: (req: InvoiceRequest) => api.post<Invoice>('/invoices', req),
  update: (id: number, req: InvoiceRequest) => api.put<Invoice>(`/invoices/${id}`, req),

  finalize: (id: number) => api.post<Invoice>(`/invoices/${id}/finalize`),
  cancel: (id: number) => api.post<Invoice>(`/invoices/${id}/cancel`),
  duplicate: (id: number, date?: string) =>
    api.post<Invoice>(`/invoices/${id}/duplicate${date ? `?date=${date}` : ''}`),

  openPdf: (id: number) => api.openInTab(`/invoices/${id}/pdf`),

  /** Removes the invoice from the database. A finalised one needs a reason. */
  remove: (id: number, reason?: string) =>
    api.del<DeleteResult>(`/invoices/${id}${reason ? `?reason=${encodeURIComponent(reason)}` : ''}`),

  /** Approves a reviewed batch. A refusal on one does not stop the rest. */
  bulkFinalize: (ids: number[]) =>
    api.post<BulkFinalizeResult>('/invoices/bulk-finalize', { ids }),

  /** Many bills in one PDF, in number order. */
  batchPdf: (opts: { status?: InvoiceStatus; fromNumber?: number; toNumber?: number;
                     customerId?: number }) => {
    const p = new URLSearchParams();
    Object.entries(opts).forEach(([k, v]) => {
      if (v !== undefined && v !== null) p.set(k, String(v));
    });
    return api.download(`/invoices/batch-pdf?${p}`);
  },

  /** One shipment, one call, a CNF and/or T draft back depending on billMode. */
  billPair: (req: BillPairRequest) => api.post<Invoice[]>('/shipments/bill-pair', req),
};

export const shipmentApi = {
  /**
   * Corrects the container count/size while the shipment's bill(s) are still
   * drafts. Returns every invoice attached to the shipment, re-priced.
   */
  updateContainers: (id: number, req: ShipmentContainerRequest) =>
    api.put<Invoice[]>(`/shipments/${id}/containers`, req),

  /** The customer's most recent shipment, to suggest the next HC invoice /
   *  ICO mark number. Both fields are null when there's no prior shipment. */
  last: (customerId: number) =>
    api.get<LastShipment>(`/shipments/last?customerId=${customerId}`),
};

export const sequenceApi = {
  /** What number the next bill will take. */
  peek: (customerId: number, category: string, date: string) =>
    api.get<SequenceState>(
      `/invoice-sequence?customerId=${customerId}&category=${category}&date=${date}`),
  /** Skip ahead. Moving the number backwards is refused by the server. */
  jump: (customerId: number, date: string, nextNumber: number, categoryCode: string) =>
    api.put<SequenceState>('/invoice-sequence',
      { customerId, date, nextNumber, categoryCode }),
  /** Whether a specific (usually freed) number is still free to issue. */
  available: (customerId: number, financialYear: string, runningNumber: number) =>
    api.get<boolean>(
      `/invoice-sequence/available?customerId=${customerId}`
      + `&financialYear=${financialYear}&runningNumber=${runningNumber}`),
};

export const soaApi = {
  preview: (customerId: number, from: string, to: string) =>
    api.get<SoaPreview>(`/soa/preview?customerId=${customerId}&from=${from}&to=${to}`),
  excel: (customerId: number, from: string, to: string) =>
    api.download(`/soa/excel?customerId=${customerId}&from=${from}&to=${to}`),
};

export const dashboardApi = {
  summary: (month?: string) => api.get<Dashboard>(`/dashboard${month ? `?month=${month}` : ''}`),
};
