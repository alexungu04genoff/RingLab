import { Link, useSearchParams } from "react-router-dom";
import { useEffect, useRef, useState } from "react";
import { api, json } from "../api";

export function VerifyEmailPage() {
  const [params, setParams] = useSearchParams();
  const token = params.get("token");
  // StrictMode replays effects; share the request, but subscribe separately each time.
  const attempts = useRef(new Map<string, Promise<unknown>>());
  const clearedAfterSuccess = useRef(false);
  const [state, setState] = useState<"verifying" | "success" | "error">("verifying");
  const [message, setMessage] = useState("");
  useEffect(() => {
    if (!token) {
      if (clearedAfterSuccess.current) { clearedAfterSuccess.current = false; return; }
      setState("error");
      setMessage("This verification link is invalid or expired.");
      return;
    }
    clearedAfterSuccess.current = false;
    let active = true;
    setState("verifying");
    let attempt = attempts.current.get(token);
    if (!attempt) {
      attempt = api("/auth/verify-email", json("POST", { token }));
      attempts.current.set(token, attempt);
    }
    attempt.then(() => {
      if (!active) return;
      setState("success");
      clearedAfterSuccess.current = true;
      setParams((previous) => {
        const next = new URLSearchParams(previous);
        next.delete("token");
        return next;
      }, { replace: true });
    }).catch((error: Error) => {
      if (!active) return;
      setState("error");
      setMessage(error.message);
    });
    return () => { active = false; };
  }, [token, setParams]);
  return <section className="panel auth-form verify-email">
    {state === "verifying" && <><h1>Verifying your email…</h1><p>Please wait a moment.</p></>}
    {state === "success" && <><h1>Email verified</h1><p>Your RingLab account is ready. Sign in to start sharing builds.</p><Link className="button primary" to="/login">Go to log in</Link></>}
    {state === "error" && <><h1>Verification unavailable</h1><p>{message}</p><p><Link to="/resend-verification">Resend verification email</Link></p><Link className="button primary" to="/login">Go to log in</Link></>}
  </section>;
}
