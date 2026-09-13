import { useEffect, useRef, useState } from "react";

interface GoogleIdentityServices {
  initialize(options: { client_id: string; callback: (response: { credential: string }) => void;
    auto_select: boolean }): void;
  renderButton(element: HTMLElement, options: { type: string; theme: string; size: string; text: string }): void;
}

declare global {
  interface Window { google?: { accounts: { id: GoogleIdentityServices } } }
}

let scriptLoading: Promise<void> | undefined;
function loadGoogle(): Promise<void> {
  if (window.google?.accounts.id) return Promise.resolve();
  if (!scriptLoading) {
    scriptLoading = new Promise<void>((resolve, reject) => {
      const script = document.createElement("script");
      script.src = "https://accounts.google.com/gsi/client";
      script.async = true;
      const timeout = window.setTimeout(() => fail(), 10000);
      function fail() {
        window.clearTimeout(timeout);
        script.remove();
        scriptLoading = undefined;
        reject(new Error("Google sign-in could not load. You can still use local sign-in."));
      }
      script.onload = () => { window.clearTimeout(timeout); resolve(); };
      script.onerror = fail;
      document.head.appendChild(script);
    });
  }
  return scriptLoading;
}

export function GoogleSignIn({ onCredential, disabled }: {
  onCredential: (credential: string) => void; disabled: boolean;
}) {
  const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID?.trim();
  const container = useRef<HTMLDivElement>(null);
  const callback = useRef(onCredential);
  const isDisabled = useRef(disabled);
  callback.current = onCredential;
  isDisabled.current = disabled;
  const [error, setError] = useState("");
  useEffect(() => {
    if (!clientId) return;
    let active = true;
    loadGoogle().then(() => {
      if (!active || !container.current) return;
      const gis = window.google?.accounts.id;
      if (!gis) throw new Error("Google sign-in is unavailable. Please use local sign-in.");
      gis.initialize({ client_id: clientId, auto_select: false, callback: ({ credential }) => {
        if (active && !isDisabled.current) callback.current(credential);
      } });
      gis.renderButton(container.current, { type: "standard", theme: "outline", size: "large", text: "continue_with" });
    }).catch((e: Error) => { if (active) setError(e.message); });
    return () => { active = false; };
  }, [clientId]);
  if (!clientId) return null;
  return <div className="google-sign-in">
    <div ref={container} aria-label="Sign in with Google" aria-disabled={disabled} />
    {error && <p role="status">{error}</p>}
    <p className="muted">──────── or ────────</p>
  </div>;
}
