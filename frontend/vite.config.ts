import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import { publicApiProxy } from "./devApiProxy";
export default defineConfig({
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
    proxy: publicApiProxy,
  },
  preview: { proxy: publicApiProxy },
});
