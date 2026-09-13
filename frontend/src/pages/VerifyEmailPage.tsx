import { Link, useSearchParams } from "react-router-dom";
import { useEffect, useRef, useState } from "react";
import { api, json } from "../api";

export function VerifyEmailPage() {
  const [params, setParams] = useSearchParams();
  const token = useRef(params.get("token")).current;
  const [state, setState] = useState<"verifying" | "success" | "error">("verifying");
  const [message, setMessage] = useState("");
  useEffect(() => {
    if (!token) { setState("error"); setMessage("This verification link is invalid or expired."); return; }
    api("/auth/verify-email", json("POST", { token }))
      .then(() => { setState("success"); setParams({}, { replace: true }); })
      .catch((error: Error) => { setState("error"); setMessage(error.message); });
  }, [token, setParams]);
  return <section className="panel auth-form verify-email">
    {state === "verifying" && <><h1>Verifying your email…</h1><p>Please wait a moment.</p></>}
    {state === "success" && <><h1>Email verified</h1><p>Your RingLab account is ready. Sign in to start sharing builds.</p><Link className="button primary" to="/login">Go to log in</Link></>}
    {state === "error" && <><h1>Verification unavailable</h1><p>{message}</p><Link className="button primary" to="/login">Go to log in</Link></>}
  </section>;
}
