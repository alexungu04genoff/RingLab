import { useRef, useState } from "react";
import { Link } from "react-router-dom";
import { api, json } from "../api";
import { ErrorNotice } from "../components";

export function ForgotPasswordPage() {
  const pending = useRef(false);
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState("");
  return <div className="auth-layout">
    <div className="auth-intro"><div className="eyebrow accent">BACK ON TRACK</div><h1>Recover your <br /><em>account.</em></h1></div>
    <form className="panel auth-form" onSubmit={async event => {
      event.preventDefault();
      if (pending.current) return;
      const email = String(new FormData(event.currentTarget).get("email") ?? "");
      pending.current = true; setBusy(true); setError(""); setSent(false);
      try {
        await api("/auth/forgot-password", { ...json("POST", { email }), anonymous: true });
        setSent(true);
      } catch (failure) { setError((failure as Error).message); }
      finally { pending.current = false; setBusy(false); }
    }}>
      <h2>Forgot password?</h2>
      <p>Enter your account email to request a password reset link.</p>
      <ErrorNotice message={error} />
      {sent && <p role="status">If an eligible account exists for that email, a password reset link has been sent.</p>}
      <label>Email<input name="email" type="email" required maxLength={254} autoComplete="email" /></label>
      <button className="primary" disabled={busy}>{busy ? "Please wait…" : "Send reset link"}</button>
      <p><Link to="/login">Back to log in</Link></p>
    </form>
  </div>;
}
