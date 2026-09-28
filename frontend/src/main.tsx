import { StrictMode } from "react";
import { CollectionProvider } from "./Collection";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Link, NavLink, Route, Routes } from "react-router-dom";
import { AuthProvider, RequireAuth, useAuth } from "./auth";
import { Explore } from "./pages/Explore";
import { BuildEditor } from "./pages/BuildEditor";
import { BuildDetails } from "./pages/BuildDetails";
import { AuthPage } from "./pages/AuthPage";
import { VerifyEmailPage } from "./pages/VerifyEmailPage";
import { ResendVerificationPage } from "./pages/ResendVerificationPage";
import { ForgotPasswordPage } from "./pages/ForgotPasswordPage";
import { ResetPasswordPage } from "./pages/ResetPasswordPage";
import { AccountPage } from "./pages/AccountPage";
import { GameData } from "./pages/GameData";
import { CompareBuilds } from "./pages/CompareBuilds";
import { BookmarkIcon, CompassIcon, HammerIcon, LibraryIcon } from "./icons";
import { SavedBuildsProvider } from "./SavedBuilds";
import { SavedBuildsPage } from "./pages/SavedBuildsPage";
import { SiteFooter } from "./components";
import { BuildComparisonProvider } from "./BuildComparison";
// Keep public dev browsers from pairing cached CSS with newer components.
import "./styles.css?v=20260927-auto-builder";
function App() {
  const { user, logout, loading } = useAuth();
  return (
    <>
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <header>
        <div className="navbar">
          <Link className="brand" to="/" aria-label="RingLab home">
            <span className="brand-mark" />
            RING<span>LAB</span>
          </Link>
          <nav aria-label="Main navigation">
            <NavLink to="/" end>
              <CompassIcon /> Explore
            </NavLink>
            <NavLink to="/game-data" aria-label="Game Collection"><LibraryIcon /> Game<span className="mobile-optional"> Collection</span></NavLink>
            <NavLink to="/my-builds"><HammerIcon /> My Builds</NavLink>
            <NavLink to="/saved-builds" aria-label="Saved Builds"><BookmarkIcon /> Saved<span className="mobile-optional"> Builds</span></NavLink>
          </nav>
          <div className="account">
            <Link className="button primary" to="/builds/new" aria-label="Create build">
              ＋ Create<span className="mobile-optional"> build</span>
            </Link>
            {loading ? (
              <span>Loading…</span>
            ) : user ? (
              <>
                <Link to="/account">@{user.username}</Link>
                <button onClick={() => logout().catch(() => {})}>
                  Log out
                </button>
              </>
            ) : (
              <>
                <Link className="account-login" to="/login">Log in</Link>
                <Link className="button primary" to="/register" aria-label="Join the grid">
                  Join<span className="mobile-optional"> the grid</span>
                </Link>
              </>
            )}
          </div>
        </div>
      </header>
      <main id="main">
        <Routes>
          <Route path="/saved-builds" element={<RequireAuth><SavedBuildsPage /></RequireAuth>} />
          <Route path="/" element={<Explore key="all" />} />
          <Route
            path="/my-builds"
            element={
              <RequireAuth>
                <Explore key="mine" mine />
              </RequireAuth>
            }
          />
          <Route
            path="/builds/new"
            element={
              <RequireAuth>
                <BuildEditor key="new" />
              </RequireAuth>
            }
          />
          <Route
            path="/builds/:id/edit"
            element={
              <RequireAuth>
                <BuildEditor key="edit" />
              </RequireAuth>
            }
          />
          <Route path="/builds/:id" element={<BuildDetails />} />
          <Route path="/compare" element={<CompareBuilds />} />
          <Route path="/login" element={<AuthPage key="login" />} />
          <Route
            path="/register"
            element={<AuthPage key="register" register />}
          />
          <Route path="/verify-email" element={<VerifyEmailPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />
          <Route path="/resend-verification" element={<ResendVerificationPage />} />
          <Route
            path="/account"
            element={<RequireAuth><AccountPage /></RequireAuth>}
          />
          <Route path="/game-data" element={<GameData />} />
          <Route
            path="*"
            element={
              <div className="empty">
                <h1>Off the track.</h1>
                <p>This page does not exist.</p>
                <Link to="/">Back to Explore</Link>
              </div>
            }
          />
        </Routes>
      </main>
      <SiteFooter />
    </>
  );
}
createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <CollectionProvider><SavedBuildsProvider><BuildComparisonProvider><App /></BuildComparisonProvider></SavedBuildsProvider></CollectionProvider>
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
);
