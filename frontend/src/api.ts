let bearer = sessionStorage.getItem("ringlab-token");
export function setToken(token: string | null) {
  bearer = token;
  if (token) sessionStorage.setItem("ringlab-token", token);
  else sessionStorage.removeItem("ringlab-token");
}
export function hasToken() {
  return !!bearer;
}
export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public retryAfterSeconds?: number,
    public field?: string,
  ) {
    super(status === 429 && retryAfterSeconds !== undefined
      ? `${message} Try again in ${retryAfterSeconds} seconds.` : message);
  }
}
export async function api<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.body) headers.set("Content-Type", "application/json");
  if (bearer) headers.set("Authorization", `Bearer ${bearer}`);
  const response = await fetch(`/api${path}`, { ...options, headers });
  if (!response.ok) {
    if (response.status === 401 && bearer) {
      setToken(null);
      window.dispatchEvent(new Event("ringlab-session-expired"));
    }
    const data = await response.json().catch(() => null);
    const violations = data?.violations
      ?.map((v: { message: string }) => v.message)
      .join(". ");
    const retryAfter = response.status === 429 ? response.headers.get("Retry-After") : null;
    const delay = retryAfter && /^\d+$/.test(retryAfter.trim())
      ? Number(retryAfter) : retryAfter ? Math.ceil((Date.parse(retryAfter) - Date.now()) / 1000) : NaN;
    throw new ApiError(
      response.status,
      data?.message ||
        violations ||
        ({
          401: "Please log in to continue.",
          403: "You do not have permission to do that.",
          404: "This page could not be found.",
        }[response.status] ??
          "Something went wrong. Please try again."),
      Number.isFinite(delay) && delay >= 0 ? delay : undefined,
      typeof data?.field === "string" ? data.field : undefined,
    );
  }
  if (response.status === 204 || response.headers.get("content-length") === "0")
    return undefined as T;
  const text = await response.text();
  return text ? (JSON.parse(text) as T) : (undefined as T);
}
export const json = (method: string, data?: unknown): RequestInit => ({
  method,
  ...(data === undefined ? {} : { body: JSON.stringify(data) }),
});
