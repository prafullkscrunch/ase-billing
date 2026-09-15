/** Indian grouping, two decimals, tabular figures so columns line up. */
export function money(v: number | null | undefined): string {
  if (v === null || v === undefined) return '';
  return v.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

export function Money({ value, className }: { value: number | null; className?: string }) {
  return <span className={`font-monospace ${className ?? ''}`}>{money(value)}</span>;
}

export function StatusBadge({ status }: { status: string }) {
  const tone =
    status === 'FINALIZED' ? 'success' : status === 'CANCELLED' ? 'secondary' : 'warning';
  return <span className={`badge text-bg-${tone}`}>{status.toLowerCase()}</span>;
}
