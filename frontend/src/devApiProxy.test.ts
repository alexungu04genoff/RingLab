import { expect, it } from "vitest";
import { publicApiProxy } from "../devApiProxy";

it.each([
  "/api/dev-fixtures/demo-accounts",
  "/api/dev-fixtures/demo-accounts?ignored=true",
  "/api/%64ev-fixtures/demo-accounts",
  "/api/builds/../dev-fixtures/demo-accounts",
  "/api/builds/%2e%2e/dev-fixtures/demo-accounts",
  "/api/builds/..%252fdev-fixtures/demo-accounts",
  "/api/builds/..\\dev-fixtures/demo-accounts",
  "/api/unknown",
])("does not forward %s through the loopback proxy", (url) => {
  expect(publicApiProxy["/api"].bypass({ url })).toBe(false);
});

it.each([
  "/api/builds?search=%25%5F&excludeId=winner",
  "/api/auth/login", "/api/auth/google/link", "/api/racers",
  "/api/community/top-builds", "/api/stats/build?gameVersionId=patch",
])("preserves public API forwarding for %s", (url) => {
  expect(publicApiProxy["/api"].bypass({ url })).toBeUndefined();
});
