import { useEffect, useRef, useState, type FormEvent } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { api, currentSessionGeneration, json } from "../api";
import { useAuth } from "../auth";
import type { Session } from "../types";

/** Owns auth submissions; the page owns labels and layout. */
export function useAuthForm(register: boolean) {
  const [error, setError] = useState("");
  const [registeredEmail, setRegisteredEmail] = useState("");
  const [busy, setBusy] = useState(false);
  const pending = useRef<AbortController | null>(null);
  const { accept } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    setError("");
    setRegisteredEmail("");
    setBusy(false);
    return () => {
      pending.current?.abort();
      pending.current = null;
    };
  }, [register, location.key]);

  async function submit(
    path: "/auth/register" | "/auth/login" | "/auth/google",
    fields: Record<string, string>,
  ) {
    if (pending.current) return;
    const request = new AbortController();
    const generation = currentSessionGeneration();
    const isCurrent = () => !request.signal.aborted && generation === currentSessionGeneration();
    pending.current = request;
    setBusy(true);
    setError("");
    const options = { ...json("POST", fields), anonymous: true, signal: request.signal };
    try {
      if (path === "/auth/register") {
        await api(path, options);
        if (isCurrent()) setRegisteredEmail(fields.email);
      } else {
        const session = await api<Session>(path, options);
        if (!isCurrent()) return;
        accept(session);
        const from = location.state?.from;
        navigate(typeof from === "string" && from.startsWith("/") && !from.startsWith("//")
          ? from : "/");
      }
    } catch (exception) {
      if (isCurrent()) setError((exception as Error).message);
    } finally {
      if (pending.current === request) {
        pending.current = null;
        setBusy(false);
      }
    }
  }

  function submitForm(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const fields: Record<string, string> = {
      username: String(data.get("username") ?? ""),
      password: String(data.get("password") ?? ""),
    };
    if (register) fields.email = String(data.get("email") ?? "");
    void submit(register ? "/auth/register" : "/auth/login", fields);
  }

  function googleLogin(credential: string) {
    void submit("/auth/google", { credential });
  }

  return { error, busy, registeredEmail, submitForm, googleLogin };
}
