import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Link, NavLink, Route, Routes } from "react-router-dom";
import { AuthProvider, RequireAuth, useAuth } from "./auth";
import { Explore } from "./pages/Explore";
import { BuildEditor } from "./pages/BuildEditor";
import { BuildDetails } from "./pages/BuildDetails";
import { AuthPage } from "./pages/AuthPage";
import { VerifyEmailPage } from "./pages/VerifyEmailPage";
import { ResendVerificationPage } from "./pages/ResendVerificationPage";
import { AccountPage } from "./pages/AccountPage";
import { GameData } from "./pages/GameData";
import { CompareBuilds } from "./pages/CompareBuilds";
import { CompassIcon, HammerIcon, LibraryIcon } from "./icons";
import { SiteFooter } from "./components";
import { BuildComparisonProvider } from "./BuildComparison";
import "./styles.css";
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
            <NavLink to="/game-data"><LibraryIcon /> Game Collection</NavLink>
            <NavLink to="/my-builds"><HammerIcon /> My Builds</NavLink>
          </nav>
          <div className="account">
            <Link className="button primary" to="/builds/new">
              ＋ Create build
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
                <Link to="/login">Log in</Link>
                <Link className="button primary" to="/register">
                  Join the grid
                </Link>
              </>
            )}
          </div>
        </div>
      </header>
      <main id="main">
        <Routes>
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
        <BuildComparisonProvider><App /></BuildComparisonProvider>
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
);
