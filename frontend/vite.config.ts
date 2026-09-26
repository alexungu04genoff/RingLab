import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import { loadEnv } from "vite";
import { publicApiProxy } from "./devApiProxy";
export default defineConfig(({ mode }) => {
  const target = loadEnv(mode, ".", "RINGLAB_").RINGLAB_API_TARGET;
  const proxy = { "/api": { ...publicApiProxy["/api"], ...(target ? { target } : {}) } };
  return {
  plugins: [react()],
  test: {
    environment: "jsdom",
    coverage: {
      provider: "v8",
      include: ["src/**/*.{ts,tsx}"],
      exclude: ["src/**/*.test.{ts,tsx}", "src/types.ts"],
      reporter: ["text", "html", "json-summary", "lcov"],
      thresholds: {
        statements: 80,
        branches: 80,
        functions: 65,
        lines: 80,
      },
    },
  },
  server: {
    host: "127.0.0.1",
    port: 5173,
    strictPort: true,
    allowedHosts: ["dev.ringlabgarage.com"],
    proxy,
  },
  preview: { proxy },
  };
});
