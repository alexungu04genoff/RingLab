import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import { Navigate, useLocation } from "react-router-dom";
import { api, ApiError, hasToken, json, setToken } from "./api";
import type { Session, User } from "./types";
interface Auth {
  user: User | null;
  loading: boolean;
  sessionError: string;
  accept: (session: Session) => void;
  logout: () => Promise<void>;
}
const Context = createContext<Auth | null>(null);
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(hasToken);
  const [sessionError, setSessionError] = useState("");
  const [sessionCheck, setSessionCheck] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setSessionError("");
    setLoading(hasToken());
    if (hasToken())
      api<User>("/auth/me", { signal: controller.signal })
        .then((current) => { if (!controller.signal.aborted) setUser(current); })
        .catch((error: unknown) => {
          if (controller.signal.aborted) return;
          if (error instanceof ApiError && error.status === 401) {
            setToken(null);
            setUser(null);
          } else {
            setSessionError(error instanceof Error ? error.message : "Session check failed.");
          }
        })
        .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    const expire = () => { setUser(null); setSessionError(""); };
    window.addEventListener("ringlab-session-expired", expire);
    return () => {
      controller.abort();
      window.removeEventListener("ringlab-session-expired", expire);
    };
  }, [sessionCheck]);
  function accept(s: Session) {
    setSessionError("");
    setToken(s.token);
    setUser(s.user);
  }
  async function logout() {
    try {
      await api("/auth/logout", json("POST"));
    } finally {
      setToken(null);
      setUser(null);
      setSessionError("");
    }
  }
  return (
    <Context.Provider value={{ user, loading, sessionError, accept, logout }}>
      {sessionError && <div role="alert">
        <p>Could not check your session. {sessionError}</p>
        <button onClick={() => setSessionCheck((check) => check + 1)}>Retry session check</button>
      </div>}
      {children}
    </Context.Provider>
  );
}
export function useAuth() {
  return useContext(Context)!;
}
export function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading, sessionError } = useAuth();
  const location = useLocation();
  if (loading) return <p role="status">Checking your session…</p>;
  if (sessionError) return null;
  return user ? (
    children
  ) : (
    <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  );
}
