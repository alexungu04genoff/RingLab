import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import { Navigate, useLocation } from "react-router-dom";
import { api, hasToken, json, setToken } from "./api";
import type { Session, User } from "./types";
interface Auth {
  user: User | null;
  loading: boolean;
  accept: (session: Session) => void;
  logout: () => Promise<void>;
}
const Context = createContext<Auth | null>(null);
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(hasToken);
  useEffect(() => {
    if (hasToken())
      api<User>("/auth/me")
        .then(setUser)
        .catch(() => setToken(null))
        .finally(() => setLoading(false));
    const expire = () => setUser(null);
    window.addEventListener("ringlab-session-expired", expire);
    return () => window.removeEventListener("ringlab-session-expired", expire);
  }, []);
  function accept(s: Session) {
    setToken(s.token);
    setUser(s.user);
  }
  async function logout() {
    try {
      await api("/auth/logout", json("POST"));
    } finally {
      setToken(null);
      setUser(null);
    }
  }
  return (
    <Context.Provider value={{ user, loading, accept, logout }}>
      {children}
    </Context.Provider>
  );
}
export function useAuth() {
  return useContext(Context)!;
}
export function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) return <p role="status">Checking your session…</p>;
  return user ? (
    children
  ) : (
    <Navigate to="/login" replace state={{ from: location.pathname }} />
  );
}
