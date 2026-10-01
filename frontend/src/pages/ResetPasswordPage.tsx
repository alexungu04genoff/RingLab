import { useRef, useState } from "react";
import { Link, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { api, ApiError, json, setToken } from "../shared/api/api";
import { ErrorNotice } from "../shared/ui/ErrorNotice";

const invalidLink = "This password reset link is invalid or has expired.";

export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const location = useLocation();
  const navigate = useNavigate();
  // Read the link without persisting it; replace the history entry after consumption.
  const token = params.get("token") ?? "";
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(location.state?.passwordResetComplete === true);
  const [invalid, setInvalid] = useState(!/^[A-Za-z0-9_-]{43}$/.test(token));
  const [error, setError] = useState("");
  const pending = useRef(false);

  return <div className="auth-layout">
    <div className="auth-intro"><div className="eyebrow accent">BACK ON TRACK</div><h1>Choose a new <br /><em>password.</em></h1></div>
    {done || location.state?.passwordResetComplete === true ? <section className="panel auth-form"><h2>Password reset</h2>
      <p role="status">Your password has been changed. Please log in with your new password.</p>
      <Link className="button primary" to="/login">Log in</Link></section>
      : invalid ? <section className="panel auth-form"><h2>Reset link unavailable</h2>
        <p role="alert">{invalidLink}</p><Link className="button primary" to="/forgot-password">Request a new reset link</Link></section>
      : <form className="panel auth-form" onSubmit={async event => {
        event.preventDefault();
        if (pending.current) return;
        const form = event.currentTarget;
        const data = new FormData(form);
        const password = String(data.get("password") ?? "");
        const confirmPassword = String(data.get("confirmPassword") ?? "");
        if (password !== confirmPassword) { setError("Passwords do not match"); return; }
        if (new TextEncoder().encode(password).length > 72) { setError("Password must be at most 72 UTF-8 bytes"); return; }
        pending.current = true; setBusy(true); setError("");
        try {
          await api("/auth/reset-password", { ...json("POST", { token, password, confirmPassword }), anonymous: true });
          form.reset(); setDone(true);
          navigate("/reset-password", { replace: true, state: { passwordResetComplete: true } });
          setToken(null);
          window.dispatchEvent(new Event("ringlab-session-expired"));
        } catch (failure) {
          if (failure instanceof ApiError && failure.status === 400 && failure.message === invalidLink) {
            setInvalid(true);
          } else setError((failure as Error).message);
        } finally { pending.current = false; setBusy(false); }
      }}>
        <h2>Reset password</h2><ErrorNotice message={error} />
        <label>New password<input type="password" name="password" required minLength={8} maxLength={72} autoComplete="new-password" /></label>
        <small>At least 8 characters, at most 72 UTF-8 bytes.</small>
        <label>Confirm password<input type="password" name="confirmPassword" required minLength={8} maxLength={72} autoComplete="new-password" /></label>
        <button className="primary" disabled={busy}>{busy ? "Please wait…" : "Reset password"}</button>
        <p><Link to="/login">Back to log in</Link></p>
      </form>}
  </div>;
}
