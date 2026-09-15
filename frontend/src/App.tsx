import { NavLink, Route, Routes, Navigate } from 'react-router-dom';
import { useAuth } from './hooks/useAuth';
import { Spinner } from './components/Alert';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { ShipmentBilling } from './pages/ShipmentBilling';
import { InvoiceList } from './pages/InvoiceList';
import { InvoiceEditor } from './pages/InvoiceEditor';
import { SoaPage } from './pages/SoaPage';

export function App() {
  const { user, loading, signOut } = useAuth();

  if (loading) return <div className="container py-5"><Spinner label="Starting" /></div>;
  if (!user) return <LoginPage />;

  return (
    <>
      <nav className="navbar navbar-expand border-bottom bg-body-tertiary">
        <div className="container-fluid px-3">
          <span className="navbar-brand fw-semibold">ASE Billing</span>
          <ul className="navbar-nav me-auto">
            <Item to="/" label="Dashboard" end />
            <Item to="/shipments/new" label="Bill a shipment" />
            <Item to="/invoices" label="Invoices" />
            <Item to="/soa" label="GST sales" />
          </ul>
          <span className="navbar-text me-3 small">{user.displayName}</span>
          <button className="btn btn-sm btn-outline-secondary" onClick={signOut}>Sign out</button>
        </div>
      </nav>

      <main className="container-fluid px-3 py-4" style={{ maxWidth: 1280 }}>
        <Routes>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/shipments/new" element={<ShipmentBilling />} />
          <Route path="/invoices" element={<InvoiceList />} />
          <Route path="/invoices/new" element={<InvoiceEditor />} />
          <Route path="/invoices/:id" element={<InvoiceEditor />} />
          <Route path="/soa" element={<SoaPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </>
  );
}

function Item({ to, label, end }: { to: string; label: string; end?: boolean }) {
  return (
    <li className="nav-item">
      <NavLink to={to} end={end}
               className={({ isActive }) => `nav-link${isActive ? ' active fw-semibold' : ''}`}>
        {label}
      </NavLink>
    </li>
  );
}
