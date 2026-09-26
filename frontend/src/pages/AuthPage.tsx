import { GoogleSignIn } from "../GoogleSignIn";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { ErrorNotice } from "../components";
import { useAuthForm } from "./useAuthForm";

export function AuthPage({ register = false }: { register?: boolean }) {
  const { error, registeredEmail, busy, submitForm, googleLogin } = useAuthForm(register);
  const navigate = useNavigate();
  const location = useLocation();
  if (registeredEmail) return (
    <div className="auth-layout"><div className="auth-intro"><div className="eyebrow accent">ONE MORE LAP</div><h1>Check your<br /><em>email.</em></h1></div>
      <section className="panel auth-form"><h2>Verify your account</h2><p>We sent a verification link to {registeredEmail}. Open it, then return here to log in.</p>
        <button className="primary" onClick={() => navigate("/login")}>Go to log in</button>
        <p><Link to="/resend-verification">Resend verification email</Link></p>
        <ErrorNotice message={error} />
      </section></div>
  );
  return (
    <div className="auth-layout">
      <div className="auth-intro">
        <div className="eyebrow accent">WELCOME TO RINGLAB</div>
        <h1>
          A place for
          <br />
          your next
          <br />
          <em>great setup.</em>
        </h1>
        <p>
          Share your CrossWorlds builds.
          <br />
          See what the community is driving.
        </p>
        <div className="decorative-rings" aria-hidden="true">
          ◎
        </div>
      </div>
      <form className="panel auth-form" onSubmit={submitForm}>
        <h2>{register ? "Join the grid" : "Welcome back"}</h2>
        <p>
          {register
            ? "Create your account to start sharing."
            : "Log in to your garage."}
        </p>
        <ErrorNotice message={error} />
        <GoogleSignIn onCredential={googleLogin} disabled={busy} />
        <p>Waiting for verification? <Link to="/resend-verification">Resend verification email</Link></p>
        <label>
          Username
          <input
            name="username"
            required
            minLength={register ? 3 : 1}
            maxLength={30}
            pattern={register ? "[A-Za-z0-9_]{3,30}" : undefined}
            autoComplete="username"
          />
          {register && (
            <small>
              3–30 letters, numbers or underscores. Case-insensitive.
            </small>
          )}
        </label>
        {register && (
          <label>
            Email
            <input
              name="email"
              type="email"
              required
              maxLength={254}
              autoComplete="email"
            />
          </label>
        )}
        <label>
          Password
          <input
            name="password"
            type="password"
            required
            minLength={register ? 8 : 1}
            maxLength={72}
            autoComplete={register ? "new-password" : "current-password"}
          />
          {register && (
            <small>At least 8 characters, at most 72 UTF-8 bytes.</small>
          )}
        </label>
        <button className="primary" disabled={busy}>
          {busy ? "Please wait…" : register ? "Create account" : "Log in"}
        </button>
        <p>
          {register ? "Already have an account?" : "New to RingLab?"}{" "}
          <Link
            to={register ? "/login" : "/register"}
            state={{ from: location.state?.from }}
          >
            {register ? "Log in" : "Register"}
          </Link>
        </p>
      </form>
    </div>
  );
}
