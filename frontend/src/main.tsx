import { StrictMode } from "react";
import { CollectionProvider } from "./features/collection/Collection";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { AuthProvider } from "./features/auth/auth";
import { SavedBuildsProvider } from "./features/saved-builds/SavedBuilds";
import { BuildComparisonProvider } from "./features/builds/BuildComparison";
import { App } from "./app/App";
import "./styles/index.css";
createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <CollectionProvider><SavedBuildsProvider><BuildComparisonProvider><App /></BuildComparisonProvider></SavedBuildsProvider></CollectionProvider>
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
);
