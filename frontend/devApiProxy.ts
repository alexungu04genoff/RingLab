// Only public application routes may pass through the development/preview server.
// Fixture tooling connects directly to Quarkus; a proxy would hide its real peer.
export function isPublicApiRequest(url: string): boolean {
  const path = url.split("?")[0];
  if (!path.startsWith("/api/") || /[%\\;]/.test(path)) return false;
  const normalized = new URL(path, "http://localhost").pathname;
  return /^\/api\/(auth|builds|comments|racers|machines|machine-parts|gadgets|game-versions|stats|news|community)(\/|$)/.test(normalized);
}

export const publicApiProxy = {
  "/api": {
    target: "http://127.0.0.1:8080",
    bypass: (request: { url?: string }) => isPublicApiRequest(request.url ?? "") ? undefined : false as const,
  },
};
