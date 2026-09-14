import { useRef, useState } from "react";
import { api, json } from "../api";
import { useAuth } from "../auth";
import { ErrorNotice } from "../components";
import { GoogleSignIn } from "../GoogleSignIn";

interface MessageResponse {
  message: string;
}

export function AccountPage() {
  const { user } = useAuth();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const linking = useRef(false);

  async function linkGoogle(credential: string) {
    if (linking.current) return;
    linking.current = true;
    setBusy(true);
    setError("");
    setMessage("");
    try {
      const response = await api<MessageResponse>(
        "/auth/google/link",
        json("POST", { credential }),
      );
      setMessage(response.message);
    } catch (exception) {
      setError((exception as Error).message);
    } finally {
      linking.current = false;
      setBusy(false);
    }
  }

  return (
    <div className="auth-layout account-settings">
      <div className="auth-intro">
        <div className="eyebrow accent">YOUR GARAGE</div>
        <h1>Account<br /><em>settings.</em></h1>
        <p>Manage how you sign in to RingLab.</p>
      </div>
      <section className="panel auth-form">
        <h2>@{user?.username}</h2>
        <p>{user?.email}</p>
        <h3>Google sign-in</h3>
        <p>
          Link the Google account with the same email address. You can then use
          either sign-in method.
        </p>
        <ErrorNotice message={error} />
        {message && <p role="status">{message}</p>}
        <GoogleSignIn onCredential={linkGoogle} disabled={busy} showSeparator={false} />
      </section>
    </div>
  );
}
