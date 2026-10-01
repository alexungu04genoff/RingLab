export const browseOrigin = (pathname: string, search: string) => `${pathname}${search}`;
export function buildDetailsOrigin(from: unknown) {
  return typeof from === "string" &&
    (from === "/" || from.startsWith("/?") ||
      from === "/my-builds" || from.startsWith("/my-builds?") ||
      from === "/saved-builds" || from.startsWith("/saved-builds?"))
    ? from
    : "/";
}
