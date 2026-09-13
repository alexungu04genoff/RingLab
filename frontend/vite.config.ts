import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    coverage: {
      provider: "v8",
      include: ["src/**/*.{ts,tsx}"],
      exclude: ["src/**/*.test.{ts,tsx}", "src/types.ts"],
      reporter: ["text", "html", "json-summary", "lcov"],
    },
  },
  server: { host: "127.0.0.1", proxy: { "/api": "http://localhost:8080" } },
});
