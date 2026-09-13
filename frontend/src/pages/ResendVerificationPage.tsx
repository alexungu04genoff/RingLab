import { useRef, useState } from "react";
import { Link } from "react-router-dom";
import { api, json } from "../api";
import { ErrorNotice } from "../components";

export function ResendVerificationPage() {
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState("");
  const submitting = useRef(false);
  return <div className="auth-layout">
    <div className="auth-intro"><div className="eyebrow accent">ONE MORE LAP</div><h1>Verify your<br /><em>email.</em></h1></div>
    <form className="panel auth-form" onSubmit={async (event) => {
      event.preventDefault();
      if (submitting.current) return;
      const email = String(new FormData(event.currentTarget).get("email") ?? "");
      submitting.current = true;
      setBusy(true);
      setSent(false);
      setError("");
      try {
        await api("/auth/resend-verification", json("POST", { email }));
        setSent(true);
      } catch (failure) {
        setError((failure as Error).message);
      } finally {
        submitting.current = false;
        setBusy(false);
      }
    }}>
      <h2>Resend verification email</h2>
      <p>Request a new link if your email did not arrive or your previous link expired.</p>
      <ErrorNotice message={error} />
      {sent && <p role="status">If this address belongs to an account awaiting verification, a new link has been sent. Check your inbox and spam folder.</p>}
      <label>Email<input name="email" type="email" required maxLength={254} autoComplete="email" /></label>
      <button className="primary" disabled={busy}>{busy ? "Please wait…" : "Send verification link"}</button>
      <p><Link to="/login">Go to log in</Link></p>
    </form>
  </div>;
}
