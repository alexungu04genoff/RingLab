import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Link, NavLink, Route, Routes } from "react-router-dom";
import { AuthProvider, RequireAuth, useAuth } from "./auth";
import { Explore } from "./pages/Explore";
import { BuildEditor } from "./pages/BuildEditor";
import { BuildDetails } from "./pages/BuildDetails";
import { AuthPage } from "./pages/AuthPage";
import { GameData } from "./pages/GameData";
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
              Explore
            </NavLink>
            <NavLink to="/game-data">Game Collection</NavLink>
            <NavLink to="/builds/new">Create Build</NavLink>
            <NavLink to="/my-builds">My Builds</NavLink>
          </nav>
          <div className="account">
            {loading ? (
              <span>Loading…</span>
            ) : user ? (
              <>
                <span>@{user.username}</span>
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
          <Route path="/login" element={<AuthPage key="login" />} />
          <Route
            path="/register"
            element={<AuthPage key="register" register />}
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
      <footer>
        <span className="brand footer-brand">
          RING<span>LAB</span>
        </span>
        <span>A CrossWorlds community build lab · Educational project</span>
        <Link to="/game-data">Game collection ↗</Link>
      </footer>
    </>
  );
}
createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <App />
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
);
