import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { auth } from '../api/endpoints';
import type { CurrentUser } from '../types';

interface AuthState {
  user: CurrentUser | null;
  loading: boolean;
  signIn: (username: string, password: string) => Promise<void>;
  signOut: () => Promise<void>;
}

const Ctx = createContext<AuthState>(null!);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [loading, setLoading] = useState(true);

  // A 401 here is the normal "not signed in yet" answer, not an error.
  useEffect(() => {
    auth.me().then(setUser).catch(() => setUser(null)).finally(() => setLoading(false));
  }, []);

  async function signIn(username: string, password: string) {
    await auth.login(username, password);
    setUser(await auth.me());
  }

  async function signOut() {
    await auth.logout();
    setUser(null);
  }

  return <Ctx.Provider value={{ user, loading, signIn, signOut }}>{children}</Ctx.Provider>;
}

export function useAuth() {
  return useContext(Ctx);
}
