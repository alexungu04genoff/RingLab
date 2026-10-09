import { Link, NavLink, Route, Routes } from "react-router-dom";
import { Explore } from "../pages/Explore";
import { BuildEditor } from "../pages/BuildEditor";
import { BuildDetails } from "../pages/BuildDetails";
import { AuthPage } from "../pages/AuthPage";
import { VerifyEmailPage } from "../pages/VerifyEmailPage";
import { ResendVerificationPage } from "../pages/ResendVerificationPage";
import { ForgotPasswordPage } from "../pages/ForgotPasswordPage";
import { ResetPasswordPage } from "../pages/ResetPasswordPage";
import { AccountPage } from "../pages/AccountPage";
import { GameData } from "../pages/GameData";
import { CompareBuilds } from "../pages/CompareBuilds";
import { GuidePage } from "../pages/Guide";
import { BookmarkIcon, CompassIcon, GuideIcon, HammerIcon, LibraryIcon } from "../shared/ui/icons";
import { SavedBuildsPage } from "../pages/SavedBuildsPage";
import { SiteFooter } from "./SiteFooter";
import { RequireAuth, useAuth } from "../features/auth/auth";
export function App() {
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
            <NavLink to="/game-data" aria-label="Game Collection"><LibraryIcon /><span>Game<span className="mobile-optional"> Collection</span></span></NavLink>
            <NavLink className="nav-guide" to="/guide"><GuideIcon /> Guide</NavLink>
            <NavLink to="/my-builds"><HammerIcon /> My Builds</NavLink>
            <NavLink to="/saved-builds" aria-label="Saved Builds"><BookmarkIcon /><span>Saved<span className="mobile-optional"> Builds</span></span></NavLink>
          </nav>
          <div className="account">
            <Link className="button primary" to="/builds/new" aria-label="Create build">
              <span aria-hidden="true">＋</span>
              <span>Create<span className="mobile-optional"> build</span></span>
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
                  <span>Join<span className="mobile-optional"> the grid</span></span>
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
          <Route path="/guide" element={<GuidePage />} />
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
