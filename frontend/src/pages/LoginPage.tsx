import { useState } from 'react';
import { useAuth } from '../hooks/useAuth';
import { ErrorAlert } from '../components/Alert';

export function LoginPage() {
  const { signIn } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true); setError(null);
    try { await signIn(username, password); }
    catch (err) { setError(err); }
    finally { setBusy(false); }
  }

  return (
    <div className="container" style={{ maxWidth: 400, paddingTop: '12vh' }}>
      <h1 className="h5 mb-1">Aprameya Shipping Enterprises</h1>
      <p className="text-secondary mb-4">Billing &amp; statement of account</p>

      <ErrorAlert error={error} onDismiss={() => setError(null)} />

      <form onSubmit={submit}>
        <div className="mb-3">
          <label className="form-label" htmlFor="u">Username</label>
          <input id="u" className="form-control" autoComplete="username" autoFocus
                 value={username} onChange={(e) => setUsername(e.target.value)} />
        </div>
        <div className="mb-3">
          <label className="form-label" htmlFor="p">Password</label>
          <input id="p" type="password" className="form-control" autoComplete="current-password"
                 value={password} onChange={(e) => setPassword(e.target.value)} />
        </div>
        <button className="btn btn-primary w-100" disabled={busy || !username || !password}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </div>
  );
}
