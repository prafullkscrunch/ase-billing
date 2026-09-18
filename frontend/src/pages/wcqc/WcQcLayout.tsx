import { NavLink, Outlet } from 'react-router-dom';

/**
 * Everything under /wcqc renders inside here. This is the WC & QC module's
 * own sub-navigation — it does not touch or extend Billing's navbar beyond
 * the single top-level "WC & QC" entry added to App.tsx.
 */
export function WcQcLayout() {
  return (
    <div>
      <ul className="nav nav-pills mb-4">
        <SubItem to="/wcqc/generate" label="Generate Certificates" />
        <SubItem to="/wcqc/generate-wc" label="Generate WC only" />
        <SubItem to="/wcqc/generate-qc" label="Generate QC only" />
        <SubItem to="/wcqc/history" label="Certificate History" />
      </ul>
      <Outlet />
    </div>
  );
}

function SubItem({ to, label }: { to: string; label: string }) {
  return (
    <li className="nav-item">
      <NavLink to={to} className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>
        {label}
      </NavLink>
    </li>
  );
}
